/*  Copyright (C) 2026 David Girón

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.roidmi

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.devices.BatteryCurrentSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.BatteryCurrentSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.devices.gatt_client.BleGattClientSupport
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.UUID
import java.util.Locale

/**
 * Device support for the Roidmi F8 Cordless Vacuum Cleaner (XCQ03RM / "ROIDMI Cleaner F1").
 *
 * ## Connection flow
 * Authenticate via FE95, then subscribe/read FFD0 telemetry and initialize the command channel.
 *
 * ## Telemetry
 * Nine-byte frames end with the sum of bytes[1..7] modulo 256 (excluding the opcode).
 * D1FF subtypes 0x11/0x17 carry standard/high-mode minute counters; see RoidmiF8Telemetry.md.
 * D2FF carries pack voltage; D3FF carries battery temperature (raw / 10 - 30 °C).
 * D4FF carries the active gear; its trailing field must not be reported as lifetime minutes.
 * D8FF carries run state and big-endian uint16 current at bytes[4..5] / 100 A.
 *
 * ## Configuration writes (ATT Write Command to D7FF / 0x003D)
 * The application value written is the ROIDMI payload only (no ATT header bytes):
 * - Standard gear 80/130/180 W: `71 0x 00 00` (x=0,1,2)
 * - Dust full reminder off/on:  `75 75 00 00` / `76 76 00 00`
 * - Reset filter timer:         `73 73`
 */
class RoidmiF8Support : BleGattClientSupport() {

    private enum class DeviceState(val code: Int) {
        RUNNING(0x00),
        IDLE(0x01),
        CHARGING(0x02);

        companion object {
            fun fromCode(code: Int): DeviceState? = entries.firstOrNull { it.code == code }
        }
    }

    // ── Resolved GATT characteristic instances ────────────────────────────────

    private var charD1FF: BluetoothGattCharacteristic? = null
    private var charBatteryVoltage: BluetoothGattCharacteristic? = null   // D2FF
    private var charD3FF: BluetoothGattCharacteristic? = null
    private var charD4FF: BluetoothGattCharacteristic? = null
    private var charCommand: BluetoothGattCharacteristic? = null       // D5FF
    private var charControl: BluetoothGattCharacteristic? = null       // D7FF
    private var charStatus: BluetoothGattCharacteristic? = null        // D8FF
    private var charVoltage: BluetoothGattCharacteristic? = null       // DBFF
    private var charAuth: BluetoothGattCharacteristic? = null          // FE95 0x0012 – RC4 challenge/response
    private var charAuthInit: BluetoothGattCharacteristic? = null      // FE95 0x001b – MI_KEY1 write
    private var charVersion: BluetoothGattCharacteristic? = null       // FE95 0x0017 – firmware token (read after auth)

    /** Last computed battery level (0–100); -1 = unknown. */
    private var lastBatteryLevel = -1

    private val cleaningSession = RoidmiF8Telemetry.CleaningSession()
    /** Whether the pack is currently charging (derived from the D8FF status flags). */
    private var charging = false
    /** Whether the motor is currently running (D8FF state 0x00); the pack voltage sags under load. */
    private var running = false

    /** Last measured pack voltage (V); <= 0 = unknown. */
    private var lastBatteryVoltage = 0f

    /** Last motor/charge current from D8FF (A); only used for full detection when charging. */
    private var lastCurrentAmps = 0f

    /** Firmware major from the D5FF 0x53 sensor response (e.g. "v0.5" → "5"); null = unknown. */
    private var firmwareMajor: String? = null

    /** Firmware build from the D5FF 0x51 response (e.g. "v_1.0.0.1" → "1001"); null = unknown. */
    private var firmwareBuild: String? = null

    /** Firmware revision from the D5FF 0x53 response: hex of the pre-dot ASCII byte ('0' → "30"). */
    private var firmwareRevision: String? = null

    /** Guards against acting on more than one AUTH notification per handshake. */
    @Volatile
    private var authDone = false

    init {
        addSupportedService(UUID_SERVICE_FFD0)
        addSupportedService(UUID_SERVICE_FE95)
        // The parent (BleGattClientSupport) adds the standard GATT battery and device-info
        // services by default. This device does not use them – remove them to avoid
        // unexpected characteristic reads or subscriptions on those services.
        supportedServices.remove(GattService.UUID_SERVICE_BATTERY_SERVICE)
        supportedServices.remove(GattService.UUID_SERVICE_DEVICE_INFORMATION)
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Initialisation: resolve all characteristic references and start the Xiaomi FE95
     * auth handshake. FFD0 subscriptions are enqueued after auth completes
     * (see [handleAuthNotification]). If the FE95 service is absent, FFD0 setup is
     * performed directly.
     */
    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        // Never combine newly received telemetry with state from a previous connection.
        cleaningSession.reset()
        lastBatteryLevel = -1
        lastBatteryVoltage = 0f
        lastCurrentAmps = 0f
        charging = false
        running = false
        firmwareMajor = null
        firmwareBuild = null
        firmwareRevision = null
        authDone = false
        device.setExtraInfo(EXTRA_CURRENT_AMPS, null)
        device.setExtraInfo(EXTRA_TEMPERATURE_CELSIUS, null)
        charD1FF = getCharacteristic(UUID_CHAR_D1FF)
        charBatteryVoltage = getCharacteristic(UUID_CHAR_D2FF)
        charD3FF = getCharacteristic(UUID_CHAR_D3FF)
        charD4FF = getCharacteristic(UUID_CHAR_D4FF)
        charCommand = getCharacteristic(UUID_CHAR_D5FF)
        charControl = getCharacteristic(UUID_CHAR_D7FF)
        charStatus = getCharacteristic(UUID_CHAR_D8FF)
        charVoltage = getCharacteristic(UUID_CHAR_DBFF)
        charAuth = getCharacteristic(UUID_CHAR_FE95_AUTH)
        charAuthInit = getCharacteristic(UUID_CHAR_FE95_INIT)
        charVersion = getCharacteristic(UUID_CHAR_FE95_VERSION)

        if (charBatteryVoltage == null || charStatus == null ||
            charVoltage == null || charControl == null || charCommand == null
        ) {
            LOG.warn("initializeDevice: required FFD0 chars not found – aborting")
            builder.setDeviceState(GBDevice.State.NOT_CONNECTED)
            return builder
        }

        builder.setDeviceState(GBDevice.State.AUTHENTICATING)

        val auth = charAuth
        val authInit = charAuthInit
        if (auth != null && authInit != null) {
            // Xiaomi MiBeacon (Mi Kettle-style) RC4 handshake. The device drops the link
            // unless it is authenticated with the user's miio token before any command.
            val token = getToken()
            if (token.size != 12) {
                LOG.warn("Xiaomi auth: no valid miio token configured – authentication required")
                builder.setDeviceState(GBDevice.State.AUTHENTICATION_REQUIRED)
                GB.toast(context, R.string.authentication_failed_check_key, Toast.LENGTH_LONG, GB.WARN)
                val device = getDevice()
                if (device != null) {
                    GBApplication.deviceService(device).disconnect()
                }
                return builder
            }
            val reversedMac = parseMacReversed(device.address)
            val frame = rc4(mixA(reversedMac, PRODUCT_ID), token)
            LOG.debug("Xiaomi auth: starting MiBeacon RC4 handshake")
            //  1. Subscribe to AUTH notifications (CCCD 0x0013).
            builder.notify(auth, true)
            //  2. Write MI_KEY1 to AUTH-INIT (0x001b) to open the handshake.
            builder.write(authInit, *MI_KEY1)
            //  3. Write RC4(mixA(reversedMac, productId), token) to AUTH (0x0012).
            //     The device replies with a notification handled in handleAuthNotification().
            builder.write(auth, *frame)
        } else {
            LOG.warn("initializeDevice: FE95 auth service not found – skipping auth")
            enqueueFFD0Setup(builder)
        }
        return builder
    }

    /**
     * Queues all FFD0 CCCD subscriptions, initial reads, the D5FF init sequence and
     * sets the device state to INITIALIZED. Called either directly (no auth service)
     * or from [handleAuthNotification] after the handshake completes.
     */
    private fun enqueueFFD0Setup(builder: TransactionBuilder) {
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        // Subscribe (CCCD) + initial read for all notify-capable FFD0 characteristics.
        // This matches the sequence used by the original ROIDMI application.
        subscribeAndRead(builder, charD1FF)
        subscribeAndRead(builder, charBatteryVoltage)   // D2FF – pack voltage
        subscribeAndRead(builder, charD3FF)
        subscribeAndRead(builder, charD4FF)
        // D5FF: command channel – subscribe only, no read.
        builder.notify(charCommand, true)
        // D6FF: read-only MAC address – no CCCD.
        getCharacteristic(UUID_CHAR_D6FF)?.let { builder.read(it) }
        subscribeAndRead(builder, charControl)       // D7FF – control flags (NOTIFY+WRITE+READ)
        subscribeAndRead(builder, charStatus)        // D8FF – run state + current
        subscribeAndRead(builder, getCharacteristic(UUID_CHAR_D9FF))
        subscribeAndRead(builder, getCharacteristic(UUID_CHAR_DAFF))
        subscribeAndRead(builder, charVoltage)       // DBFF – uncalibrated secondary sensor
        subscribeAndRead(builder, getCharacteristic(UUID_CHAR_DCFF))

        // D5FF init sequence: activate command channel and request device info.
        // The device only answers one query at a time and drops queries sent too soon
        // after the "55 55" init, so space the writes out like the official app does.
        builder.write(charCommand, *CMD_INIT)
        builder.sleep(D5FF_QUERY_DELAY_MS)
        builder.write(charCommand, *CMD_VERSION_INFO)
        builder.sleep(D5FF_QUERY_DELAY_MS)
        builder.write(charCommand, *CMD_QUERY_52)
        builder.sleep(D5FF_QUERY_DELAY_MS)
        builder.write(charCommand, *CMD_SENSOR_INFO)

        builder.setDeviceState(GBDevice.State.INITIALIZED)
    }

    /**
     * Handles the single AUTH notification of the MiBeacon RC4 handshake.
     *
     * By the time this fires, the client has already written [MI_KEY1] to AUTH-INIT and
     * `RC4(mixA(reversedMac, productId), token)` to AUTH. The device replies with its own
     * 12-byte confirmation frame (not validated here). The client completes the handshake
     * by writing `RC4(token, MI_KEY2)` to AUTH and reading the VERSION characteristic,
     * then proceeds to FFD0 setup.
     */
    private fun handleAuthNotification(value: ByteArray) {
        if (authDone) {
            LOG.debug("Xiaomi auth: extra AUTH notification ignored: {}", GB.hexdump(value))
            return
        }
        authDone = true
        LOG.debug("Xiaomi auth: device confirm={}", GB.hexdump(value))
        try {
            val b = createTransactionBuilder("auth_finish")
            // Final client frame: RC4(token, MI_KEY2).
            b.write(charAuth, *rc4(getToken(), MI_KEY2))
            // Read VERSION to complete authentication (device firmware token).
            charVersion?.let { b.read(it) }
            // Authenticated: enqueue the FFD0 telemetry setup and go INITIALIZED.
            enqueueFFD0Setup(b)
            b.queue()
        } catch (e: Exception) {
            LOG.error("handleAuthNotification: failed to finish handshake", e)
        }
    }

    /**
     * Returns the Xiaomi/miio device token from device preferences.
     *
     * The token is entered by the user during pairing (via `AuthKeyActivity`) and stored
     * under the standard [DeviceSettingsPreferenceConst.PREF_AUTH_KEY] preference.
     *
     * The value must be a hex string; an optional `"0x"` prefix is accepted. Returns an
     * empty array if not configured or invalid (the MiBeacon token is 12 bytes / 24 hex
     * chars).
     */
    private fun getToken(): ByteArray {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        var hex = prefs.getString(DeviceSettingsPreferenceConst.PREF_AUTH_KEY, "").orEmpty().trim()
        // Allow "0x" prefix to avoid user mistakes
        if (hex.length > 2 && hex.startsWith("0x")) {
            hex = hex.substring(2)
        }
        if (hex.isEmpty()) {
            return ByteArray(0)
        }
        if (hex.length != 24) {
            LOG.warn("getToken: token length {} is invalid (expected 24 hex chars)", hex.length)
            return ByteArray(0)
        }
        if (!hex.matches(Regex("[0-9a-fA-F]+"))) {
            LOG.warn("getToken: invalid hexadecimal token")
            return ByteArray(0)
        }
        return StringUtils.hexToBytes(hex)
    }

    /** Subscribes to notifications and queues an initial read for [characteristic]. */
    private fun subscribeAndRead(
        builder: TransactionBuilder,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        if (characteristic == null) return
        builder.notify(characteristic, true)
        builder.read(characteristic)
    }

    // ── Misc overrides ────────────────────────────────────────────────────────

    override fun useAutoConnect(): Boolean = false

    // ── GATT callbacks ────────────────────────────────────────────────────────

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ): Boolean {
        // Explicitly skip the parent's standard GATT battery level handler – this device
        // estimates battery level from pack voltage on FFD2.
        if (characteristic.uuid == GattCharacteristic.UUID_CHARACTERISTIC_BATTERY_LEVEL) {
            return true
        }
        if (super.onCharacteristicRead(gatt, characteristic, value, status)) {
            return true
        }
        return when (characteristic) {
            charBatteryVoltage -> { handleBatteryVoltage(value, status); true }
            charVoltage -> { handleVoltage(value, status); true }
            charStatus -> { handleStatus(value, status); true }
            charD1FF -> { if (status == BluetoothGatt.GATT_SUCCESS) handleD1FFNotification(value); true }
            charD3FF -> { if (status == BluetoothGatt.GATT_SUCCESS) handleD3Sensor(value); true }
            charD4FF -> { if (status == BluetoothGatt.GATT_SUCCESS) handleGear(value); true }
            charVersion -> { LOG.debug("Xiaomi auth: VERSION={}", GB.hexdump(value)); true }
            else -> false
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true
        }

        return when (characteristic) {
            // ── FE95 auth ───────────────────────────────────────────────────
            charAuth -> { handleAuthNotification(value); true }
            // ── FFD0 telemetry ──────────────────────────────────────────────
            charBatteryVoltage -> { handleBatteryVoltage(value, BluetoothGatt.GATT_SUCCESS); true }
            charVoltage -> { handleVoltage(value, BluetoothGatt.GATT_SUCCESS); true }
            charStatus -> { handleStatus(value, BluetoothGatt.GATT_SUCCESS); true }
            charD1FF -> { handleD1FFNotification(value); true }
            charD3FF -> { handleD3Sensor(value); true }
            charD4FF -> { handleGear(value); true }
            // Control channel (D7FF) – log raw values for future parsing.
            charControl -> {
                LOG.debug("DxFF notification {}: {}", characteristic.uuid, GB.hexdump(value)); true
            }
            charCommand -> { handleCommandResponse(value); true }
            else -> {
                LOG.warn("Unhandled characteristic notification: {}", characteristic.uuid); false
            }
        }
    }

    // ── Telemetry parsers ─────────────────────────────────────────────────────

    /** D1FF 0x11/0x17: standard/high cleaning minutes and estimated per-mode filter usage. */
    private fun handleD1FFNotification(value: ByteArray) {
        val counters = cleaningSession.update(value)
        if (counters == null) {
            LOG.debug("D1FF unparsed status: {}", GB.hexdump(value))
            return
        }
        val key = when (counters.mode) {
            RoidmiF8Telemetry.CleaningMode.STANDARD -> DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_STANDARD_CLEANING_TIME
            RoidmiF8Telemetry.CleaningMode.HIGH -> DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_HIGH_CLEANING_TIME
        }
        LOG.info("Roidmi F8 {} cleaning time: {} min", counters.mode, counters.cleaningMinutes)
        val editor = GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
            .putString(key, "${counters.cleaningMinutes} min")
        // Either mode can arrive first. Only publish totals after receiving both this session.
        cleaningSession.cumulativeMinutes?.let {
            editor.putString(DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_CLEANING_TIME, "$it min")
        }
        cleaningSession.filterUsedMinutes?.let {
            editor.putString(DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_FILTER_USED_TIME, "$it min")
        }
        editor.apply()
    }

    /** FFD3: opcode 0x31 selects bytes[4..5], 0x32 bytes[6..7]; raw / 10 - 30 °C. */
    private fun handleD3Sensor(value: ByteArray) {
        val temperature = RoidmiF8Telemetry.batteryTemperature(value) ?: return
        LOG.trace("Roidmi F8 battery temperature: {} °C", temperature)
        device.setExtraInfo(EXTRA_TEMPERATURE_CELSIUS, temperature)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
            .putString(DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_BATTERY_TEMPERATURE,
                String.format(Locale.getDefault(), "%.1f °C", temperature))
            .apply()
        device.sendDeviceUpdateIntent(context)
    }

    /** D4FF: active gear. The trailing field's units have not been established. */
    private fun handleGear(value: ByteArray) {
        val gear = RoidmiF8Telemetry.gear(value) ?: return
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
            .putString(DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_STANDARD_GEAR, gear.toString())
            .apply()
    }

    /** D2FF: voltage at bytes[4..5] for 0x21, bytes[6..7] for 0x22 (big-endian / 100). */
    private fun handleBatteryVoltage(value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return
        val voltage = RoidmiF8Telemetry.packVoltage(value) ?: return
        LOG.info("Roidmi F8 pack voltage: {} V", voltage)
        lastBatteryVoltage = voltage
        reportBattery()
    }

    /**
     * DBFF – secondary voltage rail (ATT handle 0x004D), big-endian uint16 at bytes[6..7] / 100 → V.
     * This is NOT the pack voltage shown by the app (that comes from 0x0029); it reads a lower
     * value (~19 V) and stays clamped near 19.84 V while charging, so it is only logged.
     * Example: `B1 00 00 00 00 00 07 16 1D` → 0x0716 = 1814 → 18.14 V.
     */
    private fun handleVoltage(value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            LOG.warn("handleVoltage: GATT error {}", status)
            return
        }
        // Sensor characteristics need at least 8 bytes (indices 0–7).
        if (value.size < 8) {
            LOG.warn("handleVoltage: payload too short ({})", value.size)
            return
        }
        val raw = ((value[6].toInt() and 0xFF) shl 8) or (value[7].toInt() and 0xFF)
        LOG.debug("Roidmi F8 secondary rail voltage: {} V", raw / 100.0f)
    }

    /**
     * D8FF – run / charge state + instantaneous current (ATT handle 0x0041).
     * - value[1] → device state:
     *   `0x00` motor running (cleaning), `0x01` idle / standby, `0x02` charging.
     * - value[4..5], unsigned big-endian / 100 → amps (motor draw or charge current).
     * - value[6..7] → 16-bit running counter (not a charge flag).
     *
     * Examples: `81 01 00 00 00 00 …` → idle; `81 02 00 00 00 6A …` → charging (1.06 A).
     */
    private fun handleStatus(value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            LOG.warn("handleStatus: GATT error {}", status)
            return
        }
        val currentAmps = RoidmiF8Telemetry.currentAmps(value) ?: return
        val state = value[1].toInt() and 0xFF
        val deviceState = DeviceState.fromCode(state)
        running = deviceState == DeviceState.RUNNING
        charging = deviceState == DeviceState.CHARGING
        lastCurrentAmps = currentAmps
        LOG.info(
            "Roidmi F8 status: state={} (0x{}), current={} A",
            deviceState ?: "UNKNOWN", String.format("%02X", state), currentAmps
        )
        device.setExtraInfo(EXTRA_CURRENT_AMPS, currentAmps)

        // Store the unsigned motor/charge current magnitude for battery history.
        try {
            GBApplication.acquireDB().use { db ->
                val sample = BatteryCurrentSample()
                sample.timestamp = System.currentTimeMillis()
                sample.batteryIndex = 0
                sample.current = currentAmps
                BatteryCurrentSampleProvider(device, db.daoSession).persistSamples(sample, context)
            }
        } catch (e: Exception) {
            LOG.error("handleStatus: failed to persist current sample", e)
        }

        reportBattery()
    }

    /**
     * Hold the last percentage under motor load unless the empty-voltage floor is reached.
     * Charging with tapered current is treated as full. Wait for voltage before publishing.
     */
    private fun reportBattery() {
        if (lastBatteryVoltage <= 0) return
        val chargingFull = charging && lastCurrentAmps <= FULL_CHARGE_CURRENT_AMPS
        lastBatteryLevel = RoidmiF8Telemetry.batteryLevel(
            lastBatteryVoltage, running, lastBatteryLevel, chargingFull)
        val batteryInfo = GBDeviceEventBatteryInfo()
        batteryInfo.batteryIndex = 0
        batteryInfo.level = lastBatteryLevel
        batteryInfo.state = when {
            chargingFull -> BatteryState.BATTERY_CHARGING_FULL
            charging -> BatteryState.BATTERY_CHARGING
            else -> BatteryState.BATTERY_NORMAL
        }
        batteryInfo.voltage = lastBatteryVoltage
        handleGBDeviceEvent(batteryInfo)
    }

    // ── D5FF command response parser ──────────────────────────────────────────

    /**
     * Parses a notification from D5FF (command channel).
     * - 0x51 – firmware build: dotted ASCII with digits joined (e.g. "v_1.0.0.1!" → "1001").
     * - 0x52 – unknown one-byte parameter followed by an additive checksum.
     * - 0x53 – firmware major + revision (e.g. "v0.5" → major "5", revision hex "30").
     *
     * The full firmware version shown by the official app combines both: `v_<major>.<build>.<rev>`
     * (e.g. "v0.5" + build "1001" → "v_5.1001.30"), where the revision is the ASCII byte of the
     * digit before the dot ('0' = 0x30) rendered as hex.
     */
    private fun handleCommandResponse(value: ByteArray) {
        if (value.isEmpty()) return
        when (value[0].toInt() and 0xFF) {
            0x51 -> {
                // The firmware build is reported as a dotted ASCII string (e.g. "v_1.0.0.1!"),
                // terminated by '!'. The official app joins the digits into a single build
                // number (e.g. "1.0.0.1" → "1001"), so drop every non-digit character.
                firmwareBuild = String(value, 1, value.size - 1, Charsets.UTF_8)
                    .filter { it.isDigit() }
                    .ifEmpty { null }
                LOG.info("Roidmi F8 firmware build: {}", firmwareBuild)
                updateFirmwareVersion()
            }
            0x52 -> {
                // Captured 52 1E 70: 70 is the checksum of 52 + 1E, not counter data.
                LOG.debug("Roidmi F8 unparsed 0x52 response: {}", GB.hexdump(value))
            }
            0x53 -> {
                // The sensor response (e.g. "v0.5") carries the firmware major and revision:
                // the digit after the dot is the major ("5"); the digit before the dot is the
                // revision, shown as the hex of its ASCII byte ('0' = 0x30 → "30").
                val sensorVer = String(value, 1, value.size - 1, Charsets.UTF_8).trim()
                LOG.info("Roidmi F8 sensor version: {}", sensorVer)
                val digits = sensorVer.removePrefix("v").split(".")
                if (digits.size >= 2) {
                    val revChar = digits[0].firstOrNull()
                    firmwareMajor = digits[1].filter { it.isDigit() }.ifEmpty { null }
                    firmwareRevision = revChar?.let { String.format("%02x", it.code) }
                }
                updateFirmwareVersion()
            }
            else -> LOG.debug(
                "D5FF response 0x{}: {}",
                String.format("%02X", value[0].toInt() and 0xFF), GB.hexdump(value)
            )
        }
    }

    /**
     * Composes and publishes the firmware version once its parts are known, in the same
     * `v_<major>.<build>.<revision>` format used by the official app (e.g. "v_5.1001.30").
     * Missing parts are simply omitted so a partial version is still shown.
     */
    private fun updateFirmwareVersion() {
        val major = firmwareMajor
        val build = firmwareBuild
        val revision = firmwareRevision
        if (major == null && build == null) {
            return
        }
        val version = buildString {
            append("v_")
            append(major ?: "")
            if (build != null) append(".").append(build)
            if (revision != null) append(".").append(revision)
        }
        LOG.info("Roidmi F8 firmware version: {}", version)
        device.setFirmwareVersion(version)
        device.sendDeviceUpdateIntent(context)
    }

    // ── Configuration writes ──────────────────────────────────────────────────

    override fun onSendConfiguration(config: String) {
        val control = charControl
        if (control == null) {
            LOG.warn("onSendConfiguration: control characteristic not available")
            return
        }

        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)

        val cmd: ByteArray = when (config) {
            DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_STANDARD_GEAR -> {
                val gear = prefs.getString(
                    DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_STANDARD_GEAR, "0"
                )?.toIntOrNull()?.coerceIn(0, 2) ?: 0
                gearCommand(gear)
            }
            DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_DUST_REMINDER -> {
                val on = prefs.getBoolean(
                    DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_DUST_REMINDER, true
                )
                if (on) CMD_DUST_REMINDER_ON else CMD_DUST_REMINDER_OFF
            }
            DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_RESET_FILTER -> CMD_RESET_FILTER
            else -> return
        }

        try {
            val builder = performInitialized("sendConfig:$config")
            builder.write(control, *cmd)
            builder.queue()
        } catch (e: IOException) {
            LOG.error("onSendConfiguration: failed to send command", e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RoidmiF8Support::class.java)

        /**
         * First fixed key written to the AUTH-INIT characteristic to open the handshake.
         */
        private val MI_KEY1 = byteArrayOf(0x90.toByte(), 0xCA.toByte(), 0x85.toByte(), 0xDE.toByte())

        /**
         * Second fixed key; the final client frame is `RC4(token, MI_KEY2)`.
         */
        private val MI_KEY2 = byteArrayOf(0x92.toByte(), 0xAB.toByte(), 0x54.toByte(), 0xFA.toByte())

        /** MiBeacon product id for roidmi.vacuum.v1 (advertised device id 0x0248). */
        private const val PRODUCT_ID = 0x0248

        // ── Service UUIDs ─────────────────────────────────────────────────────

        /** Vendor FFD0 service – hosts all DxFF telemetry + command characteristics. */
        private val UUID_SERVICE_FFD0: UUID = UUID.fromString("0000ffd0-0000-1000-8000-00805f9b34fb")

        /** Xiaomi MiBeacon service – provides the BLE auth handshake. */
        private val UUID_SERVICE_FE95: UUID = UUID.fromString("0000fe95-0000-1000-8000-00805f9b34fb")

        /** FE95 AUTH characteristic (UUID 0x0001, ATT handle 0x0012, read/write/notify). */
        private val UUID_CHAR_FE95_AUTH: UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")

        /** FE95 AUTH-INIT characteristic (UUID 0x0010, ATT handle 0x001b, write). */
        private val UUID_CHAR_FE95_INIT: UUID = UUID.fromString("00000010-0000-1000-8000-00805f9b34fb")

        /** FE95 VERSION characteristic (UUID 0x0004, ATT handle 0x0017, read/notify). */
        private val UUID_CHAR_FE95_VERSION: UUID = UUID.fromString("00000004-0000-1000-8000-00805f9b34fb")

        // ── FFD0 characteristic UUIDs (DxFF telemetry + command chars) ────────

        private val UUID_CHAR_D1FF: UUID = UUID.fromString("0000ffd1-0000-1000-8000-00805f9b34fb")
        /** D2FF – pack voltage. */
        private val UUID_CHAR_D2FF: UUID = UUID.fromString("0000ffd2-0000-1000-8000-00805f9b34fb")
        private val UUID_CHAR_D3FF: UUID = UUID.fromString("0000ffd3-0000-1000-8000-00805f9b34fb")
        /** D4FF – active suction gear: byte[2] = gear index (0=80 W, 1=130 W, 2=180 W). */
        private val UUID_CHAR_D4FF: UUID = UUID.fromString("0000ffd4-0000-1000-8000-00805f9b34fb")
        /** D5FF – command channel (write + notify). Init `55 55`, queries `51/52/53`. */
        private val UUID_CHAR_D5FF: UUID = UUID.fromString("0000ffd5-0000-1000-8000-00805f9b34fb")
        /** D6FF – device MAC address (read-only). */
        private val UUID_CHAR_D6FF: UUID = UUID.fromString("0000ffd6-0000-1000-8000-00805f9b34fb")
        /** D7FF – control / settings write channel (no-response writes). */
        private val UUID_CHAR_D7FF: UUID = UUID.fromString("0000ffd7-0000-1000-8000-00805f9b34fb")
        /** D8FF – run state + current: byte[1]=0x00 running; bytes[4..5] big-endian /100 → A */
        private val UUID_CHAR_D8FF: UUID = UUID.fromString("0000ffd8-0000-1000-8000-00805f9b34fb")
        private val UUID_CHAR_D9FF: UUID = UUID.fromString("0000ffd9-0000-1000-8000-00805f9b34fb")
        private val UUID_CHAR_DAFF: UUID = UUID.fromString("0000ffda-0000-1000-8000-00805f9b34fb")
        /** DBFF – secondary sensor, logged only; FFD2 supplies battery pack voltage. */
        private val UUID_CHAR_DBFF: UUID = UUID.fromString("0000ffdb-0000-1000-8000-00805f9b34fb")
        private val UUID_CHAR_DCFF: UUID = UUID.fromString("0000ffdc-0000-1000-8000-00805f9b34fb")

        // ── D5FF init + query commands ────────────────────────────────────────

        /** Sent to D5FF immediately after setup to wake up the command channel. */
        private val CMD_INIT = byteArrayOf(0x55, 0x55)
        /** Version query written to D5FF after init. */
        private val CMD_VERSION_INFO = byteArrayOf(0x51)

        // ── D7FF configuration command payloads (ATT value only – no header bytes) ─

        /** Builds the standard-gear command `71 <index> 00 00` (index 0=80 W, 1=130 W, 2=180 W). */
        private fun gearCommand(gear: Int): ByteArray = byteArrayOf(0x71, gear.toByte(), 0x00, 0x00)

        /** Dust-full reminder: off / on */
        private val CMD_DUST_REMINDER_OFF = byteArrayOf(0x75, 0x75, 0x00, 0x00)
        private val CMD_DUST_REMINDER_ON = byteArrayOf(0x76, 0x76, 0x00, 0x00)

        /** Reset filter used-time counter */
        private val CMD_RESET_FILTER = byteArrayOf(0x73, 0x73)
        /** Query 0x52: captured response contains one unknown byte and a checksum. */
        private val CMD_QUERY_52 = byteArrayOf(0x52)
        /** Sensor / brush version query. Response: opcode 0x53 + ASCII version string. */
        private val CMD_SENSOR_INFO = byteArrayOf(0x53)

        // ── Extra-info keys ───────────────────────────────────────────────────

        /** Extra-info key for the last motor / charge current (Float, amps). */
        const val EXTRA_CURRENT_AMPS = "current_amps"
        const val EXTRA_TEMPERATURE_CELSIUS = "temperature_celsius"
        /**
         * Charge current (A) below which a charging pack is considered full. During the CV phase
         * the charge current tapers towards 0 A as the pack tops off (observed dropping from
         * ~1.9 A down to 0 A on the official app's 100 % point).
         */
        private const val FULL_CHARGE_CURRENT_AMPS = 0.05f
        /**
         * Delay between consecutive D5FF query writes. The device answers one query at a
         * time and drops queries issued too soon after "55 55".
         */
        private const val D5FF_QUERY_DELAY_MS = 800

        // ── Xiaomi MiBeacon (Mi Kettle-style) RC4 auth ────────────────────────

        private fun parseMacReversed(address: String): ByteArray =
            StringUtils.hexToBytes(address.replace(":", "")).reversedArray()

        /**
         * MiBeacon key-mixing function A over the reversed MAC and product id.
         * Layout: `[mac0, mac2, mac5, pid&0xff, pid&0xff, mac4, mac5, mac1]`.
         */
        private fun mixA(mac: ByteArray, productId: Int): ByteArray = byteArrayOf(
            mac[0], mac[2], mac[5],
            (productId and 0xff).toByte(), (productId and 0xff).toByte(),
            mac[4], mac[5], mac[1],
        )

        /**
         * Standard RC4 stream cipher (no keystream drop), used for every MiBeacon frame.
         * Encryption and decryption are identical.
         */
        private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0 until 256) {
                j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
                val t = s[i]; s[i] = s[j]; s[j] = t
            }
            val out = ByteArray(data.size)
            var a = 0
            var b = 0
            for (k in data.indices) {
                a = (a + 1) and 0xff
                b = (b + s[a]) and 0xff
                val t = s[a]; s[a] = s[b]; s[b] = t
                out[k] = ((data[k].toInt() and 0xff) xor s[(s[a] + s[b]) and 0xff]).toByte()
            }
            return out
        }
    }
}
