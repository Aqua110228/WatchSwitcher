package nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_ANC_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_DUAL_CONNECT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_EQ_PRESET
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_GAME_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_HIGH_QUALITY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_LAST_ACTIVE_ANC
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_LANG
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_VOLUME
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_SPATIAL_AUDIO
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.deviceCardAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen.UgreenHituneMax5cSupport
import java.util.regex.Pattern

open class UgreenHituneMax5cCoordinator : AbstractBLClassicDeviceCoordinator() {

    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("UGREEN HiTune Max\\s*5[cC]", Pattern.CASE_INSENSITIVE)
    }

    override fun getManufacturer(): String = "UGREEN"

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return UgreenHituneMax5cSupport::class.java
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_ugreen_hitune_max_5c
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_headphones
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.HEADPHONES
    }

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> {
        return arrayOf(
            BatteryConfig(
                0,
                GBDevice.BATTERY_ICON_DEFAULT.toInt(),
                GBDevice.BATTERY_LABEL_DEFAULT.toInt(),
                20,
                100
            )
        )
    }

    override fun supportsOSBatteryLevel(device: GBDevice): Boolean = true

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = DeviceSpecificSettingsScreen.SOUND.key,
            title = R.string.pref_header_sound,
            icon = R.drawable.ic_volume_up,
        ) {
            enumList<UgreenAncMode>(
                key = PREF_UGREEN_ANC_MODE,
                title = R.string.prefs_active_noise_cancelling,
                icon = R.drawable.ic_surround,
                defaultValue = UgreenAncMode.ADAPTIVE,
            )
            enumList<UgreenEqualizer>(
                key = PREF_UGREEN_EQ_PRESET,
                title = R.string.prefs_equalizer_preset,
                icon = R.drawable.ic_equalizer,
                defaultValue = UgreenEqualizer.CLASSIC,
            )
            switchSetting(
                key = PREF_UGREEN_SPATIAL_AUDIO,
                title = R.string.nothing_prefs_spatial_audio_title,
                summary = R.string.ugreen_spatial_audio_summary,
                icon = R.drawable.ic_surround,
                defaultValue = false,
            )
            switchSetting(
                key = PREF_UGREEN_HIGH_QUALITY,
                title = R.string.ugreen_high_quality,
                summary = R.string.ugreen_high_quality_summary,
                icon = R.drawable.ic_hearing,
                defaultValue = false,
                disableDependentsState = true,
            )
            switchSetting(
                key = PREF_UGREEN_GAME_MODE,
                title = R.string.prefs_game_mode,
                summary = R.string.ugreen_game_mode_summary,
                icon = R.drawable.ic_videogame,
                defaultValue = false,
                dependency = PREF_UGREEN_HIGH_QUALITY,
            )
            switchSetting(
                key = PREF_UGREEN_DUAL_CONNECT,
                title = R.string.dual_device_mode_title,
                summary = R.string.dual_device_mode_summary,
                icon = R.drawable.ic_link,
                defaultValue = false,
                dependency = PREF_UGREEN_HIGH_QUALITY,
            )
            enumList<UgreenPromptLanguage>(
                key = PREF_UGREEN_PROMPT_LANG,
                title = R.string.prefs_voice_prompts_language,
                icon = R.drawable.ic_voice,
                defaultValue = UgreenPromptLanguage.ENGLISH,
            )
            seekbar(
                key = PREF_UGREEN_PROMPT_VOLUME,
                title = R.string.earfun_voice_prompt_volume,
                icon = R.drawable.ic_volume_up,
                min = 1,
                max = 15,
                defaultValue = 8,
                showValue = true,
            )
        }

        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
            connectedOnly = false,
        )
    }

    override fun getCustomActions(): List<DeviceCardAction> {
        return listOf(DEVICE_CARD_ACTION_ANC, DEVICE_CARD_ACTION_EQ)
    }

    companion object {
        private val DEVICE_CARD_ACTION_ANC = deviceCardAction {
            icon = { device ->
                val prefs = GBApplication.getDevicePrefs(device)
                val mode = UgreenAncMode.fromPreference(prefs.getString(PREF_UGREEN_ANC_MODE, null))
                when (mode) {
                    UgreenAncMode.OFF -> R.drawable.ic_hearing_disabled
                    UgreenAncMode.TRANSPARENCY -> R.drawable.ic_hearing
                    else -> R.drawable.ic_noise_control_on
                }
            }
            description = { _, context -> context.getString(R.string.prefs_active_noise_cancelling) }
            label = { device, context ->
                val prefs = GBApplication.getDevicePrefs(device)
                val mode = UgreenAncMode.fromPreference(prefs.getString(PREF_UGREEN_ANC_MODE, null))
                context.getString(mode.shortLabel)
            }
            onClick = { device, context ->
                val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
                val currentMode = UgreenAncMode.fromPreference(prefs.getString(PREF_UGREEN_ANC_MODE, null))
                val nextMode = when (currentMode) {
                    UgreenAncMode.OFF -> {
                        val lastActiveStr = prefs.getString(PREF_UGREEN_LAST_ACTIVE_ANC, null)
                        val lastActive = UgreenAncMode.fromPreference(lastActiveStr)
                        if (lastActive != UgreenAncMode.OFF && lastActive != UgreenAncMode.TRANSPARENCY) {
                            lastActive
                        } else {
                            UgreenAncMode.ADAPTIVE
                        }
                    }
                    UgreenAncMode.TRANSPARENCY -> UgreenAncMode.OFF
                    else -> {
                        prefs.edit().putString(PREF_UGREEN_LAST_ACTIVE_ANC, currentMode.name.lowercase()).apply()
                        UgreenAncMode.TRANSPARENCY
                    }
                }
                prefs.edit().putString(PREF_UGREEN_ANC_MODE, nextMode.name.lowercase()).apply()
                device.sendDeviceUpdateIntent(context)
                GBApplication.deviceService(device).onSendConfiguration(PREF_UGREEN_ANC_MODE)
            }
        }

        private val DEVICE_CARD_ACTION_EQ = deviceCardAction {
            icon = { R.drawable.ic_equalizer }
            description = { _, context -> context.getString(R.string.prefs_equalizer_preset) }
            label = { device, context ->
                val prefs = GBApplication.getDevicePrefs(device)
                val eq = UgreenEqualizer.fromPreference(prefs.getString(PREF_UGREEN_EQ_PRESET, null))
                context.getString(eq.label)
            }
            onClick = { device, context ->
                val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
                val currentEq = UgreenEqualizer.fromPreference(prefs.getString(PREF_UGREEN_EQ_PRESET, null))
                val nextEq = UgreenEqualizer.entries[(currentEq.ordinal + 1) % UgreenEqualizer.entries.size]
                prefs.edit().putString(PREF_UGREEN_EQ_PRESET, nextEq.name.lowercase()).apply()
                device.sendDeviceUpdateIntent(context)
                GBApplication.deviceService(device).onSendConfiguration(PREF_UGREEN_EQ_PRESET)
            }
        }
    }
}
