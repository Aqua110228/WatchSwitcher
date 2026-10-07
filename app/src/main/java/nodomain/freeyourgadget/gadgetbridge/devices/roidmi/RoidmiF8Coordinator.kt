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
package nodomain.freeyourgadget.gadgetbridge.devices.roidmi

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.deviceCardAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.roidmi.RoidmiF8Support
import java.text.DecimalFormat
import java.util.Locale
import java.util.regex.Pattern
import lineageos.weather.util.TemperatureUtils

/**
 * Coordinator for the Roidmi F8 Cordless Vacuum Cleaner (model XCQ03RM).
 *
 * The device advertises via Xiaomi MiBeacon (service UUID 0xFE95, device ID 0x0248).
 * Battery percentage is estimated from FFD2 voltage and FFD8 run/charge state.
 */
class RoidmiF8Coordinator : AbstractBLEDeviceCoordinator() {

    override fun getManufacturer(): String = "Roidmi"

    // The F8 model advertises as ROIDMI Cleaner F1
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("ROIDMI Cleaner F1")

    override fun getBatteryCount(device: GBDevice): Int = 1

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    /**
     * The Roidmi F8 follows the Xiaomi / miio standard: it must be authenticated with the
     * device token before it accepts any command, otherwise it drops the connection.
     * The token is requested during pairing via `AuthKeyActivity` and stored under
     * [DeviceSettingsPreferenceConst.PREF_AUTH_KEY].
     */
    override fun requiresAuthKey(): Boolean = true

    /**
     * Accepts exactly 24 hexadecimal digits (12 bytes), optionally prefixed with `0x`.
     */
    override fun validateAuthKey(authKey: String): Boolean {
        var hex = authKey.trim()
        if (hex.startsWith("0x")) {
            hex = hex.substring(2)
        }
        return hex.length == 24 &&
            hex.matches(Regex("[0-9a-fA-F]+"))
    }

    override fun getSupportedDeviceSpecificAuthenticationSettings(): IntArray =
        intArrayOf(R.xml.devicesettings_roidmi_f8_pairingkey)

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        RoidmiF8Support::class.java

    override fun getDeviceNameResource(): Int = R.string.devicetype_roidmi_f8

    override fun getDefaultIconResource(): Int = R.drawable.ic_vacuum_hand

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        list(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_STANDARD_GEAR,
            title = R.string.pref_roidmi_f8_standard_gear_title,
            icon = R.drawable.ic_mode_fan,
            entries = listOf(
                ListEntry.Text("0", "80 W"),
                ListEntry.Text("1", "130 W"),
                ListEntry.Text("2", "180 W"),
            ),
            defaultValue = "0",
        )
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_DUST_REMINDER,
            title = R.string.pref_roidmi_f8_dust_reminder_title,
            summary = R.string.pref_roidmi_f8_dust_reminder_summary,
            icon = R.drawable.ic_notifications,
            defaultValue = true,
        )
        info(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_BATTERY_TEMPERATURE,
            title = R.string.pref_roidmi_f8_battery_temperature_title,
            icon = R.drawable.ic_temperature,
        )
        info(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_CLEANING_TIME,
            title = R.string.pref_roidmi_f8_cleaning_time_title,
            icon = R.drawable.ic_timer,
        )
        info(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_STANDARD_CLEANING_TIME,
            title = R.string.pref_roidmi_f8_standard_cleaning_time_title,
            icon = R.drawable.ic_timer,
        )
        info(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_HIGH_CLEANING_TIME,
            title = R.string.pref_roidmi_f8_high_cleaning_time_title,
            icon = R.drawable.ic_timer,
        )
        info(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_FILTER_USED_TIME,
            title = R.string.pref_roidmi_f8_filter_used_time_title,
            icon = R.drawable.ic_filter_alt,
        )
        action(
            key = DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_RESET_FILTER,
            title = R.string.pref_roidmi_f8_reset_filter_title,
            icon = R.drawable.ic_filter_alt,
            confirmationMessage = R.string.pref_roidmi_f8_reset_filter_confirm,
        ) { _, device ->
            GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_ROIDMI_F8_RESET_FILTER)
            true
        }
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.VACUUM

    /**
     * Extra live readings shown on the device card:
     * - motor / charge current (only while it is drawing more than [MIN_DISPLAY_CURRENT_AMPS])
     * - battery temperature
     */
    override fun getCustomActions(): List<DeviceCardAction> = listOf(
        deviceCardAction {
            icon = { R.drawable.ic_bolt }
            isVisible = { device ->
                device.isConnected &&
                    (device.getExtraInfo(RoidmiF8Support.EXTRA_CURRENT_AMPS) as? Float ?: 0f) > MIN_DISPLAY_CURRENT_AMPS
            }
            description = { _, context -> context.getString(R.string.electrical_current) }
            label = { device, _ ->
                String.format(Locale.getDefault(), "%.2f A", device.getExtraInfo(RoidmiF8Support.EXTRA_CURRENT_AMPS) as? Float ?: 0f)
            }
            onClick = { _, _ -> }
        },
        deviceCardAction {
            icon = { R.drawable.ic_temperature }
            isVisible = { device ->
                device.isConnected && device.getExtraInfo(RoidmiF8Support.EXTRA_TEMPERATURE_CELSIUS) is Float
            }
            description = { _, context -> context.getString(R.string.pref_roidmi_f8_battery_temperature_title) }
            label = { device, _ ->
                val celsius = device.getExtraInfo(RoidmiF8Support.EXTRA_TEMPERATURE_CELSIUS) as? Float ?: 0f
                TemperatureUtils.formatAndConvert(celsius.toDouble(), TemperatureUnit.CELSIUS, GBApplication.getPrefs().temperatureUnit, DecimalFormat("0.0"))
            }
            onClick = { _, _ -> }
        },
    )

    companion object {
        /** Hide the current reading when the vacuum is idle (draws essentially nothing). */
        private const val MIN_DISPLAY_CURRENT_AMPS = 0.05f
    }
}
