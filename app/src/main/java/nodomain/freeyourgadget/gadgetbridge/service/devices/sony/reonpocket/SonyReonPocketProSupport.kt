/*  Copyright (C) 2026 David Giron

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.sony.reonpocket

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.Toast
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.sony.reonpocket.SonyReonPocketConstants
import nodomain.freeyourgadget.gadgetbridge.devices.sony.reonpocket.SonyReonTagConstants
import nodomain.freeyourgadget.gadgetbridge.devices.sony.reonpocket.SonyReonPocketTelemetry
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions
import nodomain.freeyourgadget.gadgetbridge.service.btle.BluetoothCompanyIdentifiers
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class SonyReonPocketProSupport : AbstractBTLESingleDeviceSupport(LOG) {
    // During a SMART -> MANUAL transition we deliberately stop smart first; the device echoes an
    // intermediate smart-stop frame that would flash the UI as "off". While this flag is set, such
    // smart frames are ignored until the expected manual frame arrives.
    private var expectingManualFrame = false

    // Reon Tag discovery: a temporary BLE scan started when the user taps "Register tag".
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tagScanCallback: ScanCallback? = null
    private var tagScanTimeout: Runnable? = null

    init {
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS)
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE)
        addSupportedService(GattService.UUID_SERVICE_DEVICE_INFORMATION)
        addSupportedService(SonyReonPocketConstants.SERVICE_UUID)
    }

    override fun useAutoConnect(): Boolean = false

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)

        device.firmwareVersion = "N/A"

        // The authentication token and feature write must precede any CCCD write,
        // otherwise the descriptor writes are never acknowledged and initialization stalls.
        builder.setDeviceState(GBDevice.State.AUTHENTICATING)
        builder.write(SonyReonPocketConstants.UUID_CHARACTERISTIC_OWNER_AUTHENTICATION, *SonyReonPocketConstants.commandAuth(ownerId))
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.write(SonyReonPocketConstants.UUID_CHARACTERISTIC_TAG_AUTHENTICATION, *SonyReonPocketConstants.commandFeature())
        builder.write(SonyReonPocketConstants.UUID_CHARACTERISTIC_CURRENT_TIME, *buildTimeSyncCommand())

        builder.read(GattCharacteristic.UUID_CHARACTERISTIC_MODEL_NUMBER_STRING)
        builder.read(GattCharacteristic.UUID_CHARACTERISTIC_SERIAL_NUMBER_STRING)
        builder.read(GattCharacteristic.UUID_CHARACTERISTIC_FIRMWARE_REVISION_STRING)

        // The Reon Pocket Pro does not expose the standard Battery Service. Battery (as a percentage)
        // and the temperature telemetry are custom characteristics that notify after the handshake.
        builder.notify(SonyReonPocketConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL, true)
        builder.notify(SonyReonPocketConstants.UUID_CHARACTERISTIC_TEMPERATURE_HUMIDITY, true)

        // The cooling/heating mode characteristic reports the current smart direction (heat vs cool),
        // used to keep the quick action icon in sync with the device.
        builder.notify(SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, true)

        // The Auto Start/Stop control characteristic notifies its state in response to a query.
        builder.notify(SonyReonPocketConstants.UUID_CHARACTERISTIC_AUTO_START_STOP, true)

        builder.read(SonyReonPocketConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL)
        builder.read(SonyReonPocketConstants.UUID_CHARACTERISTIC_TEMPERATURE_HUMIDITY)
        builder.read(SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE)

        // Read the current Auto Start/Stop state so the switch reflects the device on connect.
        builder.read(SonyReonPocketConstants.UUID_CHARACTERISTIC_AUTO_START_STOP)

        // The Pocket forgets any registered tag on disconnect, so re-register the stored tag address
        // here to keep its telemetry flowing across reconnects.
        val tagAddress = devicePrefs.preferences.getString(SonyReonPocketConstants.PREF_TAG_ADDRESS, "").orEmpty().trim()
        if (tagAddress.isNotEmpty()) {
            try {
                builder.write(SonyReonPocketConstants.UUID_CHARACTERISTIC_TAG_AUTHENTICATION, *SonyReonTagConstants.commandRegisterTag(tagAddress))
            } catch (e: IllegalArgumentException) {
                LOG.warn("Sony Reon Pocket Pro re-register tag: invalid stored address '{}'", tagAddress, e)
            }
        }

        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }


    private fun buildTimeSyncCommand(): ByteArray {
        val epochSeconds = System.currentTimeMillis() / 1000L
        val timezoneOffsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        return SonyReonPocketConstants.commandTimeSync(epochSeconds, timezoneOffsetMinutes)
    }

    /**
     * Returns the 16-byte Owner ID used to claim the device. On the first connection it is derived
     * deterministically from the device MAC address combined with a local, per-installation salt,
     * and persisted as a device-specific preference (visible/editable by the user). Mixing in the
     * local salt means a different Gadgetbridge installation cannot derive the same Owner ID from
     * the same device, while remaining stable across delete/re-pair on this installation. To share a
     * device, the Owner ID can be copied from settings to the other installation. Falls back to a
     * freshly derived key if the stored value is empty or invalid.
     */
    private val ownerId: ByteArray
        get() {
            val preferences = devicePrefs.preferences
            var hexKey = preferences.getString(SonyReonPocketConstants.PREF_AUTH_KEY, "")
            if (hexKey.isNullOrBlank()) {
                hexKey = GB.hexdump(SonyReonPocketConstants.generateOwnerId(device.address, localSalt))
                preferences.edit().putString(SonyReonPocketConstants.PREF_AUTH_KEY, hexKey).apply()
                LOG.info("Sony Reon Pocket Pro generated Owner ID for {}", device.address)
            }

            return try {
                GB.hexStringToByteArray(hexKey.trim())
            } catch (e: Exception) {
                LOG.warn("Sony Reon Pocket Pro invalid Owner ID '{}', regenerating from MAC", hexKey, e)
                SonyReonPocketConstants.generateOwnerId(device.address, localSalt)
            }
        }

    /**
     * Returns a stable, random per-installation salt used when deriving the Owner ID. It is
     * generated once and stored in the global Gadgetbridge preferences so it survives per-device
     * delete/re-pair but differs between installations.
     */
    private val localSalt: String
        get() {
            val globalPrefs = GBApplication.getPrefs()
            var salt = globalPrefs.getString(SonyReonPocketConstants.PREF_OWNER_ID_SALT, "")
            if (salt.isNullOrBlank()) {
                salt = UUID.randomUUID().toString()
                globalPrefs.preferences.edit()
                    .putString(SonyReonPocketConstants.PREF_OWNER_ID_SALT, salt)
                    .apply()
            }
            return salt
        }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ): Boolean {
        if (super.onCharacteristicWrite(gatt, characteristic, status)) {
            return true
        }

        // The auth write fails with an ATT error (0x81) when the
        // device is not in pairing mode. Show a message so the user puts the Reon Pocket
        // into pairing mode (hold SMART ~15s until it blinks fast) and reconnect.
        if (characteristic.uuid == SonyReonPocketConstants.UUID_CHARACTERISTIC_OWNER_AUTHENTICATION &&
            status != BluetoothGatt.GATT_SUCCESS
        ) {
            LOG.warn("Sony Reon Pocket Pro authentication failed (status={}), pairing mode required", status)
            GB.toast(context, R.string.sony_reon_pocket_pairing_required, Toast.LENGTH_LONG, GB.WARN)
            device.state = GBDevice.State.AUTHENTICATION_REQUIRED
            device.sendDeviceUpdateIntent(context)
            return true
        }

        return false
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ): Boolean {
        if (super.onCharacteristicRead(gatt, characteristic, value, status)) {
            return true
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            LOG.warn("Error reading {}: status={}", GattCharacteristic.toString(characteristic), status)
            return isDeviceInfoCharacteristic(characteristic.uuid)
        }

        return when (characteristic.uuid) {
            SonyReonPocketConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL -> { handleBatteryLevel(value); true }
            SonyReonPocketConstants.UUID_CHARACTERISTIC_TEMPERATURE_HUMIDITY -> { handleTelemetry(value); true }
            SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE -> { handleCoolingMode(value); true }
            SonyReonPocketConstants.UUID_CHARACTERISTIC_AUTO_START_STOP -> { handleAutoStartStopStatus(value); true }
            GattCharacteristic.UUID_CHARACTERISTIC_MODEL_NUMBER_STRING -> {
                val model = stringValue(value)
                LOG.debug("Sony Reon Pocket Pro model: {}", model)
                updateVersionInfo(null, model)
                true
            }
            GattCharacteristic.UUID_CHARACTERISTIC_SERIAL_NUMBER_STRING -> {
                val serialNumber = stringValue(value)
                LOG.debug("Sony Reon Pocket Pro serial number: {}", serialNumber)
                handleGBDeviceEvent(GBDeviceEventUpdateDeviceInfo("SERIAL: ", serialNumber))
                true
            }
            GattCharacteristic.UUID_CHARACTERISTIC_FIRMWARE_REVISION_STRING -> {
                val firmwareVersion = stringValue(value)
                LOG.debug("Sony Reon Pocket Pro firmware: {}", firmwareVersion)
                updateVersionInfo(firmwareVersion, null)
                true
            }
            else -> {
                LOG.warn("Unhandled characteristic read {}: {}", characteristic.uuid, GB.hexdump(value))
                false
            }
        }
    }



    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true
        }

        return when (characteristic.uuid) {
            SonyReonPocketConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL -> { handleBatteryLevel(value); true }
            SonyReonPocketConstants.UUID_CHARACTERISTIC_TEMPERATURE_HUMIDITY -> { handleTelemetry(value); true }
            SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE -> { handleCoolingMode(value); true }
            SonyReonPocketConstants.UUID_CHARACTERISTIC_AUTO_START_STOP -> { handleAutoStartStopStatus(value); true }
            else -> false
        }
    }

    private fun handleBatteryLevel(value: ByteArray?) {
        if (value == null || value.isEmpty()) {
            LOG.warn("Sony Reon Pocket Pro battery notification empty")
            return
        }

        val level = value[0].toInt() and 0xff
        // byte[2] indicates the charging state: 0x00 = discharging, 0x01 = charging.
        val charging = value.size > 2 && (value[2].toInt() and 0xff) != 0
        LOG.debug("Sony Reon Pocket Pro battery level: {}%, charging: {}", level, charging)

        val batteryEvent = GBDeviceEventBatteryInfo()
        batteryEvent.state = if (charging) BatteryState.BATTERY_CHARGING else BatteryState.BATTERY_NORMAL
        batteryEvent.level = level
        handleGBDeviceEvent(batteryEvent)
    }

    /** Updates the read-only panel temperature and fin-temperature-derived dissipation indicator. */
    private fun handleTelemetry(value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        if ((value[0].toInt() and 0xff) == SonyReonTagConstants.TELEMETRY_SUBFRAME_TAG) {
            handleTagTelemetry(value)
            return
        }
        val reading = SonyReonPocketTelemetry.parse(value) ?: return
        LOG.debug("Sony Reon Pocket Pro panel: {} °C, heat dissipation: {}/5",
            reading.panelTemperatureCelsius, reading.heatDissipationLevel)
        devicePrefs.preferences.edit()
            .putString(
                SonyReonPocketConstants.PREF_PANEL_TEMPERATURE,
                reading.panelTemperatureCelsius?.let { String.format(Locale.ROOT, "%.2f °C", it) }.orEmpty()
            )
            .putString(
                SonyReonPocketConstants.PREF_HEAT_DISSIPATION,
                reading.heatDissipationLevel?.let { "$it / 5" }.orEmpty()
            )
            .apply()
        device.sendDeviceUpdateIntent(context)
    }

    /**
     * Parses a Reon Tag (RNPT-1) telemetry subframe relayed through the Pocket.
     */
    private fun handleTagTelemetry(value: ByteArray) {
        val reading = SonyReonTagConstants.parseTagTelemetry(value)
        if (reading == null) {
            LOG.debug("Sony Reon Pocket Pro unparsable tag telemetry: {}", GB.hexdump(value))
            return
        }

        LOG.debug(
            "Sony Reon Pocket Pro tag[{}]: {}\u00b0C {}% battery={}% aux={}",
            reading.slot,
            reading.temperatureCelsius,
            reading.humidityPercent,
            reading.batteryPercent,
            reading.auxRaw
        )

        val preferences = devicePrefs.preferences
        val editor = preferences.edit()
        reading.temperatureCelsius?.let {
            editor.putString(SonyReonPocketConstants.PREF_TAG_TEMPERATURE, String.format(Locale.ROOT, "%.2f", it))
        }
        reading.humidityPercent?.let {
            editor.putString(SonyReonPocketConstants.PREF_TAG_HUMIDITY, String.format(Locale.ROOT, "%.2f", it))
        }
        reading.batteryPercent?.let {
            editor.putString(SonyReonPocketConstants.PREF_TAG_BATTERY, it.toString())
        }
        editor.apply()
        device.sendDeviceUpdateIntent(context)
    }

    /**
     * Parses the cooling/heating mode characteristic (mode characteristic). byte[3] encodes the smart vs
     * manual state and the heat vs cold direction (0x01 = manual cold active, 0x02 = manual heat
     * active, 0x03 = manual cold stop, 0x04 = manual heat stop, 0x10 = smart cold, 0x20 = smart
     * heat). byte[4] carries the manual power level (level - 1) when active. The result is persisted
     * so the quick actions and settings reflect the actual device state.
     */
    private fun handleCoolingMode(value: ByteArray?) {
        if (value == null || value.size <= 3) {
            return
        }

        val modeByte = value[3].toInt() and 0xff
        val smartMode: Boolean
        val heatMode: Boolean
        // Whether smart mode is currently running (only meaningful when smartMode is true).
        var smartActive = false
        // Smart auto (tag-driven auto-switching between cool and warm).
        var smartAuto = false
        // -1 means "not a manual frame" (leave the stored manual power untouched).
        var manualPower = -1
        when (modeByte) {
            SonyReonPocketConstants.MODE_BYTE_MANUAL_COOL_ACTIVE -> {
                smartMode = false; heatMode = false
                manualPower = if (value.size > 4) (value[4].toInt() and 0xff) + 1 else -1
            }
            SonyReonPocketConstants.MODE_BYTE_MANUAL_HEAT_ACTIVE -> {
                smartMode = false; heatMode = true
                manualPower = if (value.size > 4) (value[4].toInt() and 0xff) + 1 else -1
            }
            SonyReonPocketConstants.MODE_BYTE_MANUAL_COLD -> { smartMode = false; heatMode = false; manualPower = 0 }
            SonyReonPocketConstants.MODE_BYTE_MANUAL_HEAT -> { smartMode = false; heatMode = true; manualPower = 0 }
            SonyReonPocketConstants.MODE_BYTE_SMART_COLD -> { smartMode = true; heatMode = false; smartActive = false }
            SonyReonPocketConstants.MODE_BYTE_SMART_COOL_ACTIVE -> { smartMode = true; heatMode = false; smartActive = true }
            SonyReonPocketConstants.MODE_BYTE_SMART_HEAT -> { smartMode = true; heatMode = true; smartActive = false }
            SonyReonPocketConstants.MODE_BYTE_SMART_HEAT_ACTIVE -> { smartMode = true; heatMode = true; smartActive = true }
            SonyReonPocketConstants.MODE_BYTE_SMART_AUTO -> {
                // Auto-switching keeps its current direction; leave heatMode as stored.
                smartMode = true; smartAuto = true; smartActive = false
                heatMode = devicePrefs.preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
            }
            SonyReonPocketConstants.MODE_BYTE_SMART_AUTO_ACTIVE -> {
                smartMode = true; smartAuto = true; smartActive = true
                heatMode = devicePrefs.preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
            }
            else -> {
                LOG.warn("Sony Reon Pocket Pro unknown cooling mode: {}", GB.hexdump(value))
                return
            }
        }
        LOG.debug(
            "Sony Reon Pocket Pro mode: {} {} auto={} active={} power={}",
            if (smartMode) "smart" else "manual",
            if (heatMode) "heat" else "cold",
            smartAuto,
            smartActive,
            manualPower
        )

        // While transitioning SMART -> MANUAL, ignore the intermediate smart-stop frame so the UI
        // does not briefly flash "off"; clear the flag once the manual frame arrives.
        if (expectingManualFrame) {
            if (smartMode) {
                LOG.debug("Sony Reon Pocket Pro ignoring intermediate smart frame during SMART -> MANUAL transition")
                return
            }
            expectingManualFrame = false
        }

        val preferences = devicePrefs.preferences
        val changed = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false) != smartMode ||
                preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false) != heatMode
        if (changed) {
            preferences.edit()
                .putBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, smartMode)
                .putBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, heatMode)
                .apply()
            device.sendDeviceUpdateIntent(context)
        }

        // Keep the smart on/off flag in sync with what the device reports.
        if (smartMode && preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, false) != smartActive) {
            preferences.edit()
                .putBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, smartActive)
                .apply()
            device.sendDeviceUpdateIntent(context)
        }

        // Keep the smart auto (tag-driven auto-switching) flag in sync.
        if (preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false) != smartAuto) {
            preferences.edit()
                .putBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, smartAuto)
                .apply()
            device.sendDeviceUpdateIntent(context)
        }

        // In smart mode, byte[6] carries the intensity for both directions: the low nibble holds
        // the cold level (0x1..0x5) and the high nibble the heat level (0x1..0x5). Store both
        // per-direction values and mirror the active one into PREF_INTENSITY (0-based, 0..4).
        if (smartMode && value.size > 6) {
            val raw = value[6].toInt() and 0xff
            val coolLevel = (raw and 0x0f) - 1
            val heatLevel = ((raw shr 4) and 0x0f) - 1
            val editor = preferences.edit()
            var updated = false
            if (coolLevel in 0 until SonyReonPocketConstants.INTENSITY_LEVELS) {
                editor.putInt(SonyReonPocketConstants.PREF_INTENSITY_COOL, coolLevel)
                updated = true
            }
            if (heatLevel in 0 until SonyReonPocketConstants.INTENSITY_LEVELS) {
                editor.putInt(SonyReonPocketConstants.PREF_INTENSITY_HEAT, heatLevel)
                updated = true
            }
            val level = if (heatMode) heatLevel else coolLevel
            if (level in 0 until SonyReonPocketConstants.INTENSITY_LEVELS &&
                preferences.getInt(SonyReonPocketConstants.PREF_INTENSITY, -1) != level
            ) {
                editor.putInt(SonyReonPocketConstants.PREF_INTENSITY, level)
                updated = true
            }
            if (updated) {
                editor.apply()
                device.sendDeviceUpdateIntent(context)
            }
        }

        // In manual mode, keep the manual power setting in sync (0 = stop, 1..5 = power).
        if (manualPower in 0..SonyReonPocketConstants.MANUAL_LEVELS &&
            preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, -1) != manualPower
        ) {
            preferences.edit()
                .putInt(SonyReonPocketConstants.PREF_MANUAL_POWER, manualPower)
                .apply()
            device.sendDeviceUpdateIntent(context)
        }
    }

    override fun onSendConfiguration(config: String) {
        when (config) {
            SonyReonPocketConstants.ACTION_TOGGLE_AUTO_COOLING -> toggleAutoCoolingMode()
            SonyReonPocketConstants.ACTION_UNPAIR -> sendUnpairCommand()
            SonyReonPocketConstants.ACTION_TOGGLE_HEAT_COLD -> toggleHeatColdMode()
            SonyReonPocketConstants.PREF_AUTO_START_STOP -> setAutoStartStop()
            SonyReonPocketConstants.PREF_INTENSITY -> setIntensity()
            SonyReonPocketConstants.PREF_MANUAL_POWER -> setManualPower()
            SonyReonPocketConstants.ACTION_CYCLE_POWER -> cyclePower()
            SonyReonPocketConstants.ACTION_REGISTER_TAG -> registerTag()
            SonyReonPocketConstants.ACTION_UNREGISTER_TAG -> unregisterTag()
            SonyReonPocketConstants.PREF_SMART_AUTO -> setSmartAuto()
            SonyReonPocketConstants.PREF_SMART_AUTO_THRESHOLD_COOL,
            SonyReonPocketConstants.PREF_SMART_AUTO_THRESHOLD_WARM -> {
                // Re-apply only while smart auto is the active mode so a threshold edit takes effect.
                if (devicePrefs.preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false)) {
                    setSmartAuto()
                }
            }
            else -> super.onSendConfiguration(config)
        }
    }

    private fun updateVersionInfo(firmwareVersion: String?, model: String?) {
        val versionInfo = GBDeviceEventVersionInfo()
        versionInfo.fwVersion = firmwareVersion
        versionInfo.hwVersion = model
        handleGBDeviceEvent(versionInfo)
    }

    private fun isDeviceInfoCharacteristic(characteristicUuid: UUID): Boolean {
        return GattCharacteristic.UUID_CHARACTERISTIC_MODEL_NUMBER_STRING == characteristicUuid ||
                GattCharacteristic.UUID_CHARACTERISTIC_SERIAL_NUMBER_STRING == characteristicUuid ||
                GattCharacteristic.UUID_CHARACTERISTIC_FIRMWARE_REVISION_STRING == characteristicUuid
    }

    private fun stringValue(value: ByteArray): String {
        return BLETypeConversions.getStringValue(value, 0).orEmpty().trim()
    }

    /**
     * Cycles the device through three states on each tap: OFF -> SMART -> MANUAL -> OFF.
     * OFF stops the device, SMART starts smart cooling/heating at the stored intensity, and MANUAL
     * runs at the last manual power level.
     */
    private fun toggleAutoCoolingMode() {
        val preferences = devicePrefs.preferences
        val smartMode = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
        val heatMode = preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
        val poweredOn = if (smartMode) {
            preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, false)
        } else {
            preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0) > 0
        }

        val editor = preferences.edit()
        val command: ByteArray
        when {
            !poweredOn -> {
                // OFF -> SMART: enable and start smart mode. When AUTO was the selected direction and
                // a tag is usable, resume smart auto; otherwise start smart cool/heat at the stored
                // intensity (dropping a stale auto flag if the tag is no longer available).
                if (preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false) && isTagUsable(preferences)) {
                    applySmartAutoThresholds()
                    command = SonyReonPocketConstants.commandSmartAutoStart()
                    editor.putBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, true)
                    editor.putBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, true)
                } else {
                    val level = preferences.getInt(SonyReonPocketConstants.PREF_INTENSITY, (SonyReonPocketConstants.INTENSITY_LEVELS - 1) / 2) + 1
                    command = SonyReonPocketConstants.commandSmartStart(heatMode, level)
                    editor.putBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, true)
                    editor.putBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, true)
                    editor.putBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false)
                }
            }
            smartMode -> {
                // SMART -> MANUAL: the device won't switch straight from smart-active to
                // manual-active, so stop smart first, then start manual at the last power level.
                // Manual has no auto mode, so the direction resets to cold.
                val level = preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER_LAST, 1)
                    .coerceIn(1, SonyReonPocketConstants.MANUAL_LEVELS)
                val smartAuto = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false)
                // Ignore the smart-stop frame the device echoes before it reaches the manual state.
                expectingManualFrame = true
                val stopCommand = if (smartAuto) {
                    SonyReonPocketConstants.commandSmartAutoStop()
                } else {
                    SonyReonPocketConstants.commandMode(true, heatMode)
                }
                if (!writeCharacteristic("stop smart", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, stopCommand, false)) {
                    expectingManualFrame = false
                    return
                }
                command = SonyReonPocketConstants.commandManual(false, level)
                editor.putBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
                editor.putBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, false)
                editor.putBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false)
                editor.putBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
                editor.putInt(SonyReonPocketConstants.PREF_MANUAL_POWER, level)
                editor.putInt(SonyReonPocketConstants.PREF_MANUAL_POWER_LAST, level)
            }
            else -> {
                // MANUAL -> OFF: stop the device, remembering the current manual power.
                val current = preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0)
                if (current > 0) {
                    editor.putInt(SonyReonPocketConstants.PREF_MANUAL_POWER_LAST, current)
                }
                command = SonyReonPocketConstants.commandManual(heatMode, 0)
                editor.putInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0)
            }
        }

        if (!writeCharacteristic("cycle mode", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)) {
            return
        }

        editor.apply()
        device.sendDeviceUpdateIntent(context)
    }

    private fun toggleHeatColdMode() {
        val preferences = devicePrefs.preferences
        val smartModeEnabled = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
        val manualRunning = !smartModeEnabled &&
                preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0) > 0

        if (manualRunning) {
            // Manual mode is running: simple cold <-> heat toggle (auto is a smart-only mode).
            val newHeatMode = !preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
            val level = preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0)
            if (!writeCharacteristic("toggle heat/cold", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, SonyReonPocketConstants.commandManual(newHeatMode, level), false)) {
                return
            }
            preferences.edit().putBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, newHeatMode).apply()
            device.sendDeviceUpdateIntent(context)
            return
        }

        // Smart mode, or the device is off (off always powers on into smart): cycle
        // COLD -> HEAT -> AUTO -> COLD. AUTO is only offered when a Reon Tag is registered and
        // reporting readings, otherwise HEAT wraps straight back to COLD.
        val smartActive = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, false)
        val smartAuto = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false)
        val heatMode = preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
        val tagUsable = isTagUsable(preferences)

        // AUTO -> COLD, HEAT -> AUTO (or COLD if no tag), COLD -> HEAT.
        if (smartAuto) {
            // Leaving AUTO for COLD: keep smart running (or stopped) in the cold direction.
            switchSmartDirection(preferences, smartActive, heatMode = false)
            return
        }
        if (heatMode && tagUsable) {
            // HEAT -> AUTO: configure thresholds and either start auto or leave it stopped.
            applySmartAutoThresholds()
            val command = if (smartActive) {
                SonyReonPocketConstants.commandSmartAutoStart()
            } else {
                val coolThreshold = readThreshold(
                    SonyReonPocketConstants.PREF_SMART_AUTO_THRESHOLD_COOL,
                    SonyReonPocketConstants.SMART_AUTO_THRESHOLD_COOL_DEFAULT
                )
                val warmThreshold = readThreshold(
                    SonyReonPocketConstants.PREF_SMART_AUTO_THRESHOLD_WARM,
                    SonyReonPocketConstants.SMART_AUTO_THRESHOLD_WARM_DEFAULT
                )
                SonyReonPocketConstants.commandSmartAuto(coolThreshold, warmThreshold)
            }
            if (!writeCharacteristic("toggle heat/cold", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)) {
                return
            }
            preferences.edit().putBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, true).apply()
            device.sendDeviceUpdateIntent(context)
            return
        }
        // COLD -> HEAT, or HEAT -> COLD when no tag is available for AUTO.
        switchSmartDirection(preferences, smartActive, heatMode = !heatMode)
    }

    /**
     * Switches smart mode to the given cold/heat [heatMode] direction, clearing smart auto. Keeps
     * the device running when [smartActive], otherwise only changes the (stopped) direction.
     */
    private fun switchSmartDirection(preferences: android.content.SharedPreferences, smartActive: Boolean, heatMode: Boolean) {
        val fallback = preferences.getInt(SonyReonPocketConstants.PREF_INTENSITY, (SonyReonPocketConstants.INTENSITY_LEVELS - 1) / 2)
        val levelKey = if (heatMode) SonyReonPocketConstants.PREF_INTENSITY_HEAT else SonyReonPocketConstants.PREF_INTENSITY_COOL
        val level = preferences.getInt(levelKey, fallback)
        val command = if (smartActive) {
            SonyReonPocketConstants.commandSmartStart(heatMode, level + 1)
        } else {
            SonyReonPocketConstants.commandMode(true, heatMode)
        }
        if (!writeCharacteristic("toggle heat/cold", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)) {
            return
        }
        preferences.edit()
            .putBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false)
            .putBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, heatMode)
            .putInt(SonyReonPocketConstants.PREF_INTENSITY, level)
            .apply()
        device.sendDeviceUpdateIntent(context)
    }

    /**
     * Whether a Reon Tag is registered and currently reporting a valid reading, i.e. smart auto can
     * rely on it. Requires a stored tag address and a temperature that is not the out-of-range
     * sentinel (0x8000/100) the tag reports when it has no measurement.
     */
    private fun isTagUsable(preferences: android.content.SharedPreferences): Boolean {
        if (preferences.getString(SonyReonPocketConstants.PREF_TAG_ADDRESS, "").isNullOrEmpty()) {
            return false
        }
        val temperature = preferences.getString(SonyReonPocketConstants.PREF_TAG_TEMPERATURE, null)?.toDoubleOrNull()
            ?: return false
        return temperature != 0x8000 / 100.0
    }

    private fun setAutoStartStop() {
        val enabled = devicePrefs.preferences.getBoolean(SonyReonPocketConstants.PREF_AUTO_START_STOP, false)
        writeCharacteristic(
            "auto start/stop",
            SonyReonPocketConstants.UUID_CHARACTERISTIC_AUTO_START_STOP,
            SonyReonPocketConstants.commandAutoStartStop(enabled),
            false
        )
    }

    private fun setIntensity() {
        val preferences = devicePrefs.preferences
        val level = preferences.getInt(SonyReonPocketConstants.PREF_INTENSITY, (SonyReonPocketConstants.INTENSITY_LEVELS - 1) / 2)
        val heatMode = preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
        val command = SonyReonPocketConstants.commandIntensity(heatMode, level + 1)

        if (!writeCharacteristic("intensity", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)) {
            return
        }

        // Intensity commands put the device into smart mode; keep the smart flag in sync.
        if (!preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)) {
            preferences.edit()
                .putBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, true)
                .apply()
            device.sendDeviceUpdateIntent(context)
        }
    }

    private fun setManualPower() {
        val preferences = devicePrefs.preferences
        val level = preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0)
        val heatMode = preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)
        val command = SonyReonPocketConstants.commandManual(heatMode, level)

        if (!writeCharacteristic("manual power", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)) {
            return
        }

        // Manual power commands take the device out of smart mode; keep the smart flag in sync.
        if (preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)) {
            preferences.edit()
                .putBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
                .apply()
            device.sendDeviceUpdateIntent(context)
        }
    }

    /**
     * Cycles the active power level on each tap. In smart mode the value cycles through the
     * intensity levels 1..5 and wraps back to 1. In manual mode it cycles 1, 2, 3, 4, 5, 1, ...
     * Powering the device on/off is handled by the dedicated power action, so this never stops it.
     */
    private fun cyclePower() {
        val preferences = devicePrefs.preferences
        val smartMode = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
        val heatMode = preferences.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false)

        if (smartMode) {
            val current = preferences.getInt(SonyReonPocketConstants.PREF_INTENSITY, 0) + 1
            val next = if (current >= SonyReonPocketConstants.INTENSITY_LEVELS) 1 else current + 1
            val command = SonyReonPocketConstants.commandSmartStart(heatMode, next)
            if (!writeCharacteristic("cycle intensity", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)) {
                return
            }
            preferences.edit()
                .putInt(SonyReonPocketConstants.PREF_INTENSITY, next - 1)
                .apply()
        } else {
            val current = preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0)
            val next = if (current >= SonyReonPocketConstants.MANUAL_LEVELS) 1 else current + 1
            val command = SonyReonPocketConstants.commandManual(heatMode, next)
            if (!writeCharacteristic("cycle manual power", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)) {
                return
            }
            preferences.edit()
                .putInt(SonyReonPocketConstants.PREF_MANUAL_POWER, next)
                .putInt(SonyReonPocketConstants.PREF_MANUAL_POWER_LAST, next)
                .apply()
        }
        device.sendDeviceUpdateIntent(context)
    }

    /**
     * Parses the Auto Start/Stop characteristic value : 0180 = enabled, 00 =
     * disabled. The result is persisted so the settings switch reflects the actual device state.
     */
    private fun handleAutoStartStopStatus(value: ByteArray) {
        if (value.isEmpty()) {
            return
        }

        val enabled = (value[0].toInt() and 0xff) == 0x01
        LOG.debug("Sony Reon Pocket Pro auto start/stop: {}", enabled)

        val preferences = devicePrefs.preferences
        if (preferences.getBoolean(SonyReonPocketConstants.PREF_AUTO_START_STOP, false) != enabled) {
            preferences.edit()
                .putBoolean(SonyReonPocketConstants.PREF_AUTO_START_STOP, enabled)
                .apply()
            device.sendDeviceUpdateIntent(context)
        }
    }

    private fun sendUnpairCommand() {
        if (writeCharacteristic("unpair", SonyReonPocketConstants.UUID_CHARACTERISTIC_UNPAIR, SonyReonPocketConstants.commandFactoryReset(), true)) {
            waitForUnpairCommand()
        }
    }

    /**
     * Registers a Reon Tag (RNPT-1) with the Pocket by writing its Bluetooth address to the feature
     * characteristic (tag authentication characteristic). The tag address is read from the PREF_TAG_ADDRESS preference
     * (display form, e.g. `EA:65:60:8A:EF:2D`). The Pocket then relays the tag's readings through the
     * telemetry characteristic (subframe 0x02).
     */
    /**
     * Toggles smart auto mode (requires a paired Reon Tag). When starting, the configured thresholds
     * are written first (byte[3]=0x30) and the mode is then started (byte[3]=0x31); when already
     * running, it is stopped. Keeps the smart flags in sync.
     */
    private fun setSmartAuto() {
        val preferences = devicePrefs.preferences
        val running = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false) &&
                preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, false)

        if (running) {
            if (!writeCharacteristic("smart auto stop", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, SonyReonPocketConstants.commandSmartAutoStop(), false)) {
                return
            }
            preferences.edit()
                .putBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, false)
                .apply()
            device.sendDeviceUpdateIntent(context)
            return
        }

        // Configure the thresholds first, then start smart auto.
        applySmartAutoThresholds()
        if (!writeCharacteristic("smart auto start", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, SonyReonPocketConstants.commandSmartAutoStart(), false)) {
            return
        }
        preferences.edit()
            .putBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, true)
            .putBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, true)
            .putBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, true)
            .apply()
        device.sendDeviceUpdateIntent(context)
    }

    /** Writes the configured cool/warm switching thresholds to the device (byte[3]=0x30 frame). */
    private fun applySmartAutoThresholds() {
        val coolThreshold = readThreshold(
            SonyReonPocketConstants.PREF_SMART_AUTO_THRESHOLD_COOL,
            SonyReonPocketConstants.SMART_AUTO_THRESHOLD_COOL_DEFAULT
        )
        val warmThreshold = readThreshold(
            SonyReonPocketConstants.PREF_SMART_AUTO_THRESHOLD_WARM,
            SonyReonPocketConstants.SMART_AUTO_THRESHOLD_WARM_DEFAULT
        )
        val command = SonyReonPocketConstants.commandSmartAuto(coolThreshold, warmThreshold)
        writeCharacteristic("smart auto thresholds", SonyReonPocketConstants.UUID_CHARACTERISTIC_DEVICE_MODE, command, false)
    }

    /** Reads a threshold preference stored as text (Celsius), falling back to [default]. */
    private fun readThreshold(key: String, default: Double): Double {
        val raw = devicePrefs.preferences.getString(key, null)
        return raw?.toDoubleOrNull() ?: default
    }

    private fun registerTag() {
        scanAndRegisterTag()
    }

    /**
     * Scans for a nearby Reon Tag (RNPT-1) advertising [SonyReonTagConstants.SERVICE_UUID] (or the
     * matching local name) and, on the first match, stops scanning and registers it with the Pocket.
     * The user does not need to know the tag address: it is discovered here and persisted in
     * PREF_TAG_ADDRESS so the settings screen can display it.
     */
    @Suppress("MissingPermission")
    private fun scanAndRegisterTag() {
        if (tagScanCallback != null) {
            LOG.debug("Sony Reon Pocket Pro register tag: scan already in progress")
            return
        }

        val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            LOG.warn("Sony Reon Pocket Pro register tag: no BLE scanner available")
            return
        }

        GB.toast(context, context.getString(R.string.sony_reon_pocket_tag_scanning), Toast.LENGTH_SHORT, GB.INFO)

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SonyReonTagConstants.SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val scanRecord = result.scanRecord
                val name = scanRecord?.deviceName ?: device.name
                val serviceUuids = scanRecord?.serviceUuids
                val matchesService = serviceUuids?.any { it.uuid == SonyReonTagConstants.SERVICE_UUID } == true
                val matchesName = name == SonyReonTagConstants.ADVERTISED_NAME
                if (!matchesService && !matchesName) {
                    return
                }
                // The advertised (scan) address may be an ephemeral random address. Decrypt the
                // obfuscated advertising payload to recover the tag's real BD address and serial and
                // trust those; only fall back to the scan address if decryption is unavailable.
                val identity = SonyReonTagConstants.parseAdvertisement(extractTagPayload(scanRecord))
                val tagAddress = identity?.bdAddress ?: device.address
                LOG.info(
                    "Sony Reon Pocket Pro found Reon Tag scan={} decrypted={} serial={} ({})",
                    device.address, identity?.bdAddress, identity?.serial, name
                )
                stopTagScan()
                onTagFound(tagAddress, identity?.serial)
            }

            override fun onScanFailed(errorCode: Int) {
                LOG.warn("Sony Reon Pocket Pro register tag: scan failed with code {}", errorCode)
                stopTagScan()
                GB.toast(context, context.getString(R.string.sony_reon_pocket_tag_scan_failed), Toast.LENGTH_SHORT, GB.WARN)
            }
        }

        tagScanCallback = callback
        tagScanTimeout = Runnable {
            LOG.warn("Sony Reon Pocket Pro register tag: no tag found within timeout")
            stopTagScan()
            GB.toast(context, context.getString(R.string.sony_reon_pocket_tag_not_found), Toast.LENGTH_LONG, GB.WARN)
        }
        mainHandler.postDelayed(tagScanTimeout!!, TAG_SCAN_TIMEOUT_MS)

        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: Exception) {
            LOG.warn("Sony Reon Pocket Pro register tag: failed to start scan", e)
            stopTagScan()
        }
    }

    @Suppress("MissingPermission")
    private fun stopTagScan() {
        tagScanTimeout?.let { mainHandler.removeCallbacks(it) }
        tagScanTimeout = null
        val callback = tagScanCallback ?: return
        tagScanCallback = null
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (e: Exception) {
            LOG.debug("Sony Reon Pocket Pro register tag: failed to stop scan", e)
        }
    }

    /**
     * Extracts the tag's obfuscated advertising payload from a scan record. The payload is carried
     * as Manufacturer Specific Data under Sony's company id (0x012D); getManufacturerSpecificData
     * already strips the 2-byte company id, so the returned buffer starts at the obfuscated payload.
     */
    private fun extractTagPayload(scanRecord: android.bluetooth.le.ScanRecord?): ByteArray? {
        if (scanRecord == null) {
            return null
        }
        scanRecord.getManufacturerSpecificData(BluetoothCompanyIdentifiers.SONY_CORPORATION)?.let {
            if (it.isNotEmpty()) return it
        }
        return null
    }

    /** Registers the discovered tag address with the Pocket and persists it for display. */
    private fun onTagFound(tagAddress: String, serial: String?) {
        val command = try {
            SonyReonTagConstants.commandRegisterTag(tagAddress)
        } catch (e: IllegalArgumentException) {
            LOG.warn("Sony Reon Pocket Pro register tag: invalid address '{}'", tagAddress, e)
            return
        }

        writeCharacteristic("register tag", SonyReonPocketConstants.UUID_CHARACTERISTIC_TAG_AUTHENTICATION, command, false)
        val editor = devicePrefs.preferences.edit()
            .putString(SonyReonPocketConstants.PREF_TAG_ADDRESS, tagAddress)
        if (!serial.isNullOrEmpty()) {
            editor.putString(SonyReonPocketConstants.PREF_TAG_SERIAL, serial)
        } else {
            editor.remove(SonyReonPocketConstants.PREF_TAG_SERIAL)
        }
        editor.apply()
        device.sendDeviceUpdateIntent(context)
        GB.toast(context, context.getString(R.string.sony_reon_pocket_tag_registered, tagAddress), Toast.LENGTH_SHORT, GB.INFO)
    }

    /**
     * Unregisters the configured Reon Tag from the Pocket (tag authentication characteristic, opcode 0x0202).
     */
    private fun unregisterTag() {
        val tagAddress = devicePrefs.preferences.getString(SonyReonPocketConstants.PREF_TAG_ADDRESS, "").orEmpty().trim()
        if (tagAddress.isEmpty()) {
            LOG.warn("Sony Reon Pocket Pro unregister tag: no tag address configured")
            return
        }

        val command = try {
            SonyReonTagConstants.commandUnregisterTag(tagAddress)
        } catch (e: IllegalArgumentException) {
            LOG.warn("Sony Reon Pocket Pro unregister tag: invalid address '{}'", tagAddress, e)
            return
        }

        writeCharacteristic("unregister tag", SonyReonPocketConstants.UUID_CHARACTERISTIC_TAG_AUTHENTICATION, command, false)
        devicePrefs.preferences.edit()
            .remove(SonyReonPocketConstants.PREF_TAG_ADDRESS)
            .remove(SonyReonPocketConstants.PREF_TAG_SERIAL)
            .remove(SonyReonPocketConstants.PREF_TAG_TEMPERATURE)
            .remove(SonyReonPocketConstants.PREF_TAG_HUMIDITY)
            .remove(SonyReonPocketConstants.PREF_TAG_BATTERY)
            .apply()
        device.sendDeviceUpdateIntent(context)
    }

    override fun onFactoryReset() {
        writeCharacteristic(
            "factory reset",
            SonyReonPocketConstants.UUID_CHARACTERISTIC_UNPAIR,
            SonyReonPocketConstants.commandFactoryReset(),
            true
        )
    }

    private fun writeCharacteristic(taskName: String, characteristicUuid: UUID, command: ByteArray, immediate: Boolean): Boolean {

        return try {
            val builder = if (immediate) createTransactionBuilder(taskName) else performInitialized(taskName)
            builder.write(characteristicUuid, *command)
            if (immediate) {
                builder.queueImmediately()
            } else {
                builder.queue()
            }
            true
        } catch (e: IOException) {
            LOG.warn("Sony Reon Pocket Pro {} failed", taskName, e)
            false
        }
    }

    private fun waitForUnpairCommand() {
        try {
            Thread.sleep(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SonyReonPocketProSupport::class.java)
        private const val TAG_SCAN_TIMEOUT_MS = 15_000L
    }
}
