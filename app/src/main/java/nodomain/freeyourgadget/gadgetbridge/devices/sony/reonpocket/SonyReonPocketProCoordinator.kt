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
package nodomain.freeyourgadget.gadgetbridge.devices.sony.reonpocket

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.deviceCardAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.sony.reonpocket.SonyReonPocketProSupport
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import java.util.regex.Pattern

class SonyReonPocketProCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^RNP-P1$")

    override fun getManufacturer(): String = "Sony"

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return SonyReonPocketProSupport::class.java
    }

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        switchSetting(
            key = SonyReonPocketConstants.PREF_AUTO_START_STOP,
            title = R.string.sony_reon_pocket_auto_start_stop_title,
            summary = R.string.sony_reon_pocket_auto_start_stop_summary,
            icon = R.drawable.ic_auto_awesome,
        )

        seekbar(
            key = SonyReonPocketConstants.PREF_INTENSITY,
            title = R.string.sony_reon_pocket_intensity_title,
            summary = R.string.sony_reon_pocket_intensity_summary,
            max = SonyReonPocketConstants.INTENSITY_LEVELS - 1,
            defaultValue = (SonyReonPocketConstants.INTENSITY_LEVELS - 1) / 2,
        )

        seekbar(
            key = SonyReonPocketConstants.PREF_MANUAL_POWER,
            title = R.string.sony_reon_pocket_manual_power_title,
            summary = R.string.sony_reon_pocket_manual_power_summary,
            max = SonyReonPocketConstants.MANUAL_LEVELS,
            defaultValue = 0,
        )

        info(
            key = SonyReonPocketConstants.PREF_PANEL_TEMPERATURE,
            title = R.string.sony_reon_pocket_panel_temperature_title,
            icon = R.drawable.ic_thermometer,
        )

        info(
            key = SonyReonPocketConstants.PREF_HEAT_DISSIPATION,
            title = R.string.sony_reon_pocket_heat_dissipation_title,
            icon = R.drawable.ic_mode_heat,
        )

        screen(
            key = DeviceSpecificSettingsScreen.AUTHENTICATION.key,
            title = R.string.pref_header_authentication,
            icon = R.drawable.ic_vpn_key,
        ) {
            text(
                key = SonyReonPocketConstants.PREF_AUTH_KEY,
                title = R.string.pref_title_authkey,
                icon = R.drawable.ic_vpn_key,
                connectedOnly = false,
            )
        }

        screen(
            key = "sony_reon_pocket_tag",
            title = R.string.sony_reon_pocket_tag_title,
            icon = R.drawable.ic_bluetooth_searching,
        ) {
            info(
                key = SonyReonPocketConstants.PREF_TAG_ADDRESS,
                title = R.string.sony_reon_pocket_tag_address_title,
                icon = R.drawable.ic_bluetooth,
                connectedOnly = false,
                visibleWhen = { prefs ->
                    !prefs.getString(SonyReonPocketConstants.PREF_TAG_ADDRESS, "").isNullOrEmpty()
                },
            )
            info(
                key = SonyReonPocketConstants.PREF_TAG_SERIAL,
                title = R.string.sony_reon_pocket_tag_serial_title,
                icon = R.drawable.ic_label_24px,
                connectedOnly = false,
                visibleWhen = { prefs ->
                    !prefs.getString(SonyReonPocketConstants.PREF_TAG_ADDRESS, "").isNullOrEmpty()
                },
            )
            action(
                key = SonyReonPocketConstants.ACTION_REGISTER_TAG,
                title = R.string.sony_reon_pocket_tag_register_title,
                summary = R.string.sony_reon_pocket_tag_register_summary,
                icon = R.drawable.ic_bluetooth_connected,
                visibleWhen = { prefs ->
                    prefs.getString(SonyReonPocketConstants.PREF_TAG_ADDRESS, "").isNullOrEmpty()
                },
                onClick = { _, device ->
                    device?.let {
                        GBApplication.deviceService(it).onSendConfiguration(SonyReonPocketConstants.ACTION_REGISTER_TAG)
                        true
                    } ?: false
                },
            )
            action(
                key = SonyReonPocketConstants.ACTION_UNREGISTER_TAG,
                title = R.string.sony_reon_pocket_tag_unregister_title,
                summary = R.string.sony_reon_pocket_tag_unregister_summary,
                icon = R.drawable.ic_bluetooth_disabled,
                confirmationMessage = R.string.sony_reon_pocket_tag_unregister_confirmation,
                visibleWhen = { prefs ->
                    !prefs.getString(SonyReonPocketConstants.PREF_TAG_ADDRESS, "").isNullOrEmpty()
                },
                onClick = { _, device ->
                    device?.let {
                        GBApplication.deviceService(it).onSendConfiguration(SonyReonPocketConstants.ACTION_UNREGISTER_TAG)
                        true
                    } ?: false
                },
            )
            info(
                key = SonyReonPocketConstants.PREF_TAG_TEMPERATURE,
                title = R.string.sony_reon_pocket_tag_temperature_title,
                icon = R.drawable.ic_mode_heat,
                visibleWhen = { prefs -> hasTagReading(prefs, SonyReonPocketConstants.PREF_TAG_TEMPERATURE) },
            )
            info(
                key = SonyReonPocketConstants.PREF_TAG_HUMIDITY,
                title = R.string.sony_reon_pocket_tag_humidity_title,
                icon = R.drawable.ic_humidity_mid,
                visibleWhen = { prefs -> hasTagReading(prefs, SonyReonPocketConstants.PREF_TAG_HUMIDITY) },
            )
            info(
                key = SonyReonPocketConstants.PREF_TAG_BATTERY,
                title = R.string.sony_reon_pocket_tag_battery_title,
                icon = R.drawable.ic_battery_80,
                visibleWhen = { prefs -> hasTagReading(prefs, SonyReonPocketConstants.PREF_TAG_BATTERY) },
            )
        }
    }

    /**
     * Returns whether a stored tag reading ([key]) holds a real value, i.e. it is present and is
     * not the out-of-range sentinel the tag reports when no measurement is available. Temperature
     * and humidity saturate at 0x8000/100 (327.68), while battery saturates at 0xFF (255).
     */
    private fun hasTagReading(prefs: Prefs, key: String): Boolean {
        val value = prefs.getString(key, "")?.toDoubleOrNull() ?: return false
        val sentinel = if (key == SonyReonPocketConstants.PREF_TAG_BATTERY) {
            0xFF.toDouble()
        } else {
            0x8000 / 100.0
        }
        return value != sentinel
    }

    override fun getDeviceNameResource(): Int = R.string.devicetype_sony_reon_pocket_pro

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_air_conditioning

    override fun suggestUnbindBeforePair(): Boolean = false

    override fun getCustomActions(): List<DeviceCardAction> = listOf(
        deviceCardAction {
            icon = { device ->
                val smartMode = GBApplication
                    .getDeviceSpecificSharedPrefs(device.address)
                    .getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
                when {
                    !isPoweredOn(device) -> R.drawable.ic_power_settings_new
                    smartMode -> R.drawable.ic_auto_awesome
                    else -> R.drawable.ic_settings_remote
                }
            }
            description = { _, context -> context.getString(R.string.sony_reon_pocket_toggle_auto_cooling) }
            label = { device, context ->
                val smartMode = GBApplication
                    .getDeviceSpecificSharedPrefs(device.address)
                    .getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
                context.getString(
                    when {
                        !isPoweredOn(device) -> R.string.off
                        smartMode -> R.string.smart
                        else -> R.string.manual
                    }
                )
            }
            onClick = { device, _ ->
                GBApplication.deviceService(device)
                    .onSendConfiguration(SonyReonPocketConstants.ACTION_TOGGLE_AUTO_COOLING)
            }
        },
        deviceCardAction {
            icon = { device ->
                val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
                when {
                    isSmartAuto(prefs) -> R.drawable.ic_swap_vert
                    prefs.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false) -> R.drawable.ic_mode_heat
                    else -> R.drawable.ic_mode_cool
                }
            }
            description = { _, context -> context.getString(R.string.sony_reon_pocket_toggle_heat_cold) }
            label = { device, context ->
                val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
                context.getString(
                    when {
                        isSmartAuto(prefs) -> R.string.sony_reon_pocket_mode_auto
                        prefs.getBoolean(SonyReonPocketConstants.PREF_HEAT_MODE, false) -> R.string.sony_reon_pocket_mode_heat
                        else -> R.string.sony_reon_pocket_mode_cold
                    }
                )
            }
            onClick = { device, _ ->
                GBApplication.deviceService(device)
                    .onSendConfiguration(SonyReonPocketConstants.ACTION_TOGGLE_HEAT_COLD)
            }
        },
        deviceCardAction {
            icon = { device ->
                val value = currentPowerLevel(device)
                when (value) {
                    1 -> R.drawable.ic_cell_bar_1
                    2 -> R.drawable.ic_cell_bar_2
                    3 -> R.drawable.ic_cell_bar_3
                    4 -> R.drawable.ic_cell_bar_4
                    5 -> R.drawable.ic_cell_bar_5
                    else -> R.drawable.ic_power_settings_new
                }
            }
            isVisible = { device -> isPoweredOn(device) }
            description = { _, context -> context.getString(R.string.sony_reon_pocket_cycle_power) }
            label = { device, context ->
                val value = currentPowerLevel(device)
                if (value == 0) context.getString(R.string.off) else value.toString()
            }
            onClick = { device, _ ->
                GBApplication.deviceService(device)
                    .onSendConfiguration(SonyReonPocketConstants.ACTION_CYCLE_POWER)
            }
        },
    )

    /**
     * Returns the currently displayed power level: the smart intensity (1..5) when in smart mode,
     * or the manual power (0 = stop, 1..5) otherwise.
     */
    private fun currentPowerLevel(device: GBDevice): Int {
        val preferences = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val smartMode = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
        return if (smartMode) {
            preferences.getInt(SonyReonPocketConstants.PREF_INTENSITY, 0) + 1
        } else {
            preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0)
        }
    }

    /**
     * Returns whether the device is currently powered on: smart mode running, or manual power > 0.
     */
    private fun isPoweredOn(device: GBDevice): Boolean {
        val preferences = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val smartMode = preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false)
        return if (smartMode) {
            preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_ACTIVE, false)
        } else {
            preferences.getInt(SonyReonPocketConstants.PREF_MANUAL_POWER, 0) > 0
        }
    }

    /**
     * Whether the device is in smart auto (tag-driven) mode, used to render the heat/cold/auto
     * toggle in its third state.
     */
    private fun isSmartAuto(preferences: android.content.SharedPreferences): Boolean =
        preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_MODE_ENABLED, false) &&
            preferences.getBoolean(SonyReonPocketConstants.PREF_SMART_AUTO, false)

    override fun prepareDeviceForDeletion(gbDevice: GBDevice) {
        if (gbDevice.isConnected) {
            GBApplication.deviceService(gbDevice)
                .onSendConfiguration(SonyReonPocketConstants.ACTION_UNPAIR)
        }
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.UNKNOWN
    }
}
