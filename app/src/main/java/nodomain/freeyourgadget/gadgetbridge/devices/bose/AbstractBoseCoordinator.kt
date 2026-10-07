/*  Copyright (C) 2021-2024 Damien Gaignon, Daniel Dakhno, José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.bose

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.bose.BoseProtocol
import nodomain.freeyourgadget.gadgetbridge.service.devices.bose.BoseSupport

abstract class AbstractBoseCoordinator : AbstractBLClassicDeviceCoordinator() {
    abstract val deviceConfig: BoseDeviceConfig

    // Filtered by the device's supported and unavailable masks
    private val shortcutActionLabels = listOf(
        BoseProtocol.BUTTON_MODE_BATTERY_LEVEL to R.string.battery_level,
        BoseProtocol.BUTTON_MODE_SELF_VOICE_OR_WIND to R.string.self_voice,
        BoseProtocol.BUTTON_MODE_SPOTIFY to R.string.pref_title_touch_spotify,
    )

    // Filtered by the device's supported-language mask
    private val voicePromptLanguages = listOf(
        R.string.english_gb to "0",
        R.string.english_us to "1",
        R.string.french to "2",
        R.string.italian to "3",
        R.string.german to "4",
        R.string.spanish_es to "5",
        R.string.spanish_mx to "6",
        R.string.portuguese to "7",
        R.string.mandarin to "8",
        R.string.korean to "9",
        R.string.russian to "10",
        R.string.polish to "11",
        R.string.hebrew to "12",
        R.string.turkish to "13",
        R.string.dutch to "14",
        R.string.japanese to "15",
        R.string.cantonese to "16",
        R.string.arabic to "17",
        R.string.swedish to "18",
        R.string.danish to "19",
        R.string.norwegian to "20",
        R.string.finnish to "21",
        R.string.hindi to "22",
    )

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return BoseSupport::class.java
    }

    override fun getManufacturer(): String {
        return "Bose"
    }

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> {
        return arrayOf(
            BatteryConfig(
                0,
                GBDevice.BATTERY_ICON_DEFAULT.toInt(),
                GBDevice.BATTERY_LABEL_DEFAULT.toInt(),
                25,
                100
            )
        )
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.HEADPHONES
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_headphones
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        deviceConfig.cnc?.let { cnc ->
            seekbar(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_CNC_LEVEL,
                title = R.string.prefs_active_noise_cancelling_level,
                icon = R.drawable.ic_noise_control_on,
                defaultValue = cnc.defaultValue,
                max = cnc.maximum,
                connectedOnly = true,
            )
        }
        deviceConfig.anr?.let { anr ->
            seekbar(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_ANR_LEVEL,
                title = R.string.prefs_active_noise_reduction_level,
                icon = R.drawable.ic_noise_control_on,
                defaultValue = anr.defaultValue,
                max = anr.maximum,
                connectedOnly = true,
            )
        }
        externalSettings(
            key = DeviceSettingsPreferenceConst.PREF_MULTIPOINT,
            title = R.string.bluetooth_multipoint_pairing,
            icon = R.drawable.ic_bluetooth_searching,
            connectedOnly = true,
            activityClass = nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity::class.java,
        )
        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
            connectedOnly = false,
        )
        screen(
            key = "pref_screen_bose_general",
            title = R.string.pref_header_general,
            icon = R.drawable.ic_settings,
        ) {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS,
                title = R.string.soundcore_voice_prompts,
                defaultValue = true,
                visibleWhen = { prefs ->
                    prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_TOGGLABLE, false)
                },
            )
            list(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_LANGUAGE,
                title = R.string.prefs_voice_prompts_language,
                entriesProvider = { prefs ->
                    val mask = prefs.getString(
                        DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_SUPPORTED,
                        null,
                    )?.toIntOrNull()
                    if (mask == null || mask == 0) {
                        emptyList()
                    } else {
                        val context = GBApplication.getContext()
                        voicePromptLanguages
                            .filter { (mask and (1 shl it.second.toInt())) != 0 }
                            .map { ListEntry.Text(it.second, context.getString(it.first)) }
                    }
                },
                defaultValue = "1",
                visibleWhen = { prefs ->
                    prefs.getString(DeviceSettingsPreferenceConst.PREF_BOSE_VOICE_PROMPTS_SUPPORTED, null)
                        ?.toIntOrNull()?.let { it != 0 } ?: false
                },
            )
            list(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_AUTO_OFF,
                title = R.string.prefs_wena3_auto_power_off_item,
                entriesProvider = { _ ->
                    val context = GBApplication.getContext()
                    deviceConfig.standbyTimerDurations.map { minutes ->
                        ListEntry.Text(minutes.toString(), when (minutes) {
                            0 -> context.getString(R.string.never)
                            5 -> context.getString(R.string.minutes_5)
                            10 -> context.getString(R.string.minutes_10)
                            20 -> context.getString(R.string.minutes_20)
                            40 -> context.getString(R.string.minutes_40)
                            60 -> context.getString(R.string.minutes_60)
                            180 -> context.getString(R.string.minutes_180)
                            else -> "$minutes minutes"
                        })
                    }
                },
                defaultValue = "60",
            )
            list(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT,
                title = R.string.prefs_shortcut_action,
                entriesProvider = { prefs ->
                    val supported = prefs.getString(
                        DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT_SUPPORTED,
                        null,
                    )?.toIntOrNull()
                    if (supported == null) {
                        emptyList()
                    } else {
                        val unavailable = prefs.getString(
                            DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT_UNAVAILABLE,
                            "0",
                        )?.toIntOrNull() ?: 0
                        val context = GBApplication.getContext()
                        shortcutActionLabels
                            .filter {
                                (supported and (1 shl it.first)) != 0 && (unavailable and (1 shl it.first)) == 0
                            }
                            .map { ListEntry.Text(it.first.toString(), context.getString(it.second)) }
                    }
                },
                defaultValue = "",
                visibleWhen = {
                    it.getString(DeviceSettingsPreferenceConst.PREF_BOSE_SHORTCUT_SUPPORTED, null) != null
                },
            )
        }
        screen(
            key = "pref_screen_bose_media",
            title = R.string.prefs_media_controls,
            summary = R.string.prefs_media_transport_controls_summary,
            icon = R.drawable.ic_play,
        ) {
            fun newHandler(key: String): (Context, GBDevice?) -> Boolean = { _, device ->
                GBApplication.deviceService(device).onSendConfiguration(key)
                true
            }

            action(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PLAY,
                title = R.string.pref_media_play,
                icon = R.drawable.ic_play,
                connectedOnly = true,
                onClick = newHandler(DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PLAY),
            )
            action(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PAUSE,
                title = R.string.pref_media_pause,
                icon = R.drawable.ic_pause,
                connectedOnly = true,
                onClick = newHandler(DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PAUSE),
            )
            action(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_NEXT,
                title = R.string.pref_media_next,
                icon = R.drawable.ic_skip_next,
                connectedOnly = true,
                onClick = newHandler(DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_NEXT),
            )
            action(
                key = DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PREVIOUS,
                title = R.string.pref_media_previous,
                icon = R.drawable.ic_skip_previous,
                connectedOnly = true,
                onClick = newHandler(DeviceSettingsPreferenceConst.PREF_BOSE_MEDIA_PREVIOUS),
            )
        }
    }
}
