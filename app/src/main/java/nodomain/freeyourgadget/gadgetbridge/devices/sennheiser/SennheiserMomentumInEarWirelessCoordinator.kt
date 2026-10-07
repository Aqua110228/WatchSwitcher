/*  Copyright (C) 2026 oddballza

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
package nodomain.freeyourgadget.gadgetbridge.devices.sennheiser

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.SennheiserMomentumSupport
import java.util.regex.Pattern

/**
 * Sennheiser MOMENTUM In-Ear Wireless (M2 IEBT). The battery level comes from Android, which the
 * headset reports it to over the hands-free profile.
 */
class SennheiserMomentumInEarWirelessCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getDeviceNameResource(): Int = R.string.devicetype_sennheiser_momentum_in_ear_wireless

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_headphones

    override fun getManufacturer(): String = "Sennheiser"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("MOMENTUM M2 IEBT")

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.HEADPHONES

    override fun supportsOSBatteryLevel(device: GBDevice): Boolean = true

    override fun supportsFindDevice(device: GBDevice): Boolean = true

    override fun supportsPowerOff(device: GBDevice): Boolean = true

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        SennheiserMomentumSupport::class.java

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = DeviceSpecificSettingsScreen.SOUND.key,
            title = R.string.pref_header_sound,
            icon = R.drawable.ic_volume_up,
        ) {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_PROMPTS,
                title = R.string.sennheiser_momentum_voice_prompts_title,
                summary = R.string.sennheiser_momentum_voice_prompts_summary,
                icon = R.drawable.ic_voice,
                defaultValue = true,
            )
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_SWAP_VOLUME_BUTTONS,
                title = R.string.sennheiser_momentum_swap_volume_buttons_title,
                summary = R.string.sennheiser_momentum_swap_volume_buttons_summary,
                icon = R.drawable.ic_swap_vert,
                defaultValue = false,
            )
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED,
                title = R.string.sennheiser_momentum_led_title,
                summary = R.string.sennheiser_momentum_led_summary,
                icon = R.drawable.ic_brightness_medium,
                defaultValue = true,
            )
        }
        screen(
            key = DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS.key,
            title = R.string.pref_header_calls_and_notifications,
            icon = R.drawable.ic_phone,
        ) {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_ANSWER,
                title = R.string.sennheiser_momentum_voice_answer_title,
                summary = R.string.sennheiser_momentum_voice_answer_summary,
                icon = R.drawable.ic_microphone,
                defaultValue = false,
            )
            xmlScreen(
                DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
                R.xml.devicesettings_headphones,
            )
        }
    }
}
