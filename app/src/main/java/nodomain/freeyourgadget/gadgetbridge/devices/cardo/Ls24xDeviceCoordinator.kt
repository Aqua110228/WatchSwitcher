package nodomain.freeyourgadget.gadgetbridge.devices.cardo

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.CardoDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.state.CardoDeviceStatus
import java.util.regex.Pattern

class Ls24xDeviceCoordinator : AbstractBLEDeviceCoordinator() {

    override fun getManufacturer(): String {
        return "Cardo"
    }

    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("UCS LS2")
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return CardoDeviceSupport::class.java
    }


    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec {

        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val status = CardoDeviceStatus().apply { restoreFrom(prefs) }
        val fmRegion = status.config.fmRegion.value

        return deviceSettings {
            xmlScreen(
                DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
                R.xml.devicesettings_headphones,
                connectedOnly = false,
            )
            screen(
                key = "pref_cardo_screen_fm_radio",
                title = R.string.cardo_card_fm_radio,
                icon = R.drawable.ic_music_note
            ) {
                switchSetting(
                    key = "pref_cardo_toggle_fm",
                    title = R.string.cardo_control_enable_radio,
                    icon = R.drawable.ic_speaker,
                    connectedOnly = true,
                    defaultValue = false,
                )
                list(
                    key = "pref_cardo_fm_preset",
                    title = R.string.cardo_control_fm_preset_title,
                    icon = R.drawable.ic_music_note,
                    dependency = "pref_cardo_toggle_fm",
                    visibleWhen = { prefs -> (1..6).any { prefs.getInt("pref_cardo_fm_preset_$it", 0) > 0 } },
                    entriesProvider = { fmPresetEntries(device) }
                )
                seekbar(
                    key = "pref_cardo_fm_tuning",
                    title = R.string.cardo_control_fm_frequency_title,
                    icon = R.drawable.ic_speaker,
                    min = fmRegion.minFreq,
                    max = fmRegion.maxFreq,
                    defaultValue = fmRegion.minFreq,
                    step = 50,
                    scale = .01,
                    valueFormat = R.string.cardo_control_fm_frequency_subtitle,
                    showValue = false,
                    dependency = "pref_cardo_toggle_fm",
                    onSharedPreferenceChanged = { }
                )
                action (
                    key = "fake_seek_up",
                    title = R.string.cardo_control_fm_seek_up,
                    icon = R.drawable.ic_arrow_upward,
                    dependency = "pref_cardo_toggle_fm",
                    onClick = { _, device ->
                        GBApplication.deviceService(device).onSendConfiguration("fake_seek_up")
                        true
                    },
                )
                action (
                    key = "fake_scan_up",
                    title = R.string.cardo_control_fm_scan_up,
                    dependency = "pref_cardo_toggle_fm",
                    onClick = { _, device ->
                        GBApplication.deviceService(device).onSendConfiguration("fake_scan_up")
                        true
                    },
                )
                action (
                    key = "fake_seek_down",
                    title = R.string.cardo_control_fm_seek_down,
                    icon = R.drawable.ic_arrow_downward,
                    dependency = "pref_cardo_toggle_fm",
                    onClick = { _, device ->
                        GBApplication.deviceService(device).onSendConfiguration("fake_seek_down")
                        true
                    },
                )
                action (
                    key = "fake_scan_down",
                    title = R.string.cardo_control_fm_scan_down,
                    dependency = "pref_cardo_toggle_fm",
                    onClick = { _, device ->
                        GBApplication.deviceService(device).onSendConfiguration("fake_scan_down")
                        true
                    },
                )
                action (
                    key = "fake_scan_stop",
                    title = R.string.cardo_control_fm_scan_stop,
                    dependency = "pref_cardo_toggle_fm",
                    onClick = { _, device ->
                        GBApplication.deviceService(device).onSendConfiguration("fake_scan_stop")
                        true
                    },
                )
                action (
                    key = "fake_autotune",
                    title = R.string.cardo_control_fm_autotune,
                    confirmationMessage = R.string.cardo_control_fm_confirm_autotune,
                    dependency = "pref_cardo_toggle_fm",
                    onClick = { _, device ->
                        GBApplication.deviceService(device).onSendConfiguration("fake_autotune")
                        true
                    },
                )
            }
            screen(
                key = "pref_cardo_screen_general",
                title = R.string.cardo_card_voice_prompts,
                icon = R.drawable.ic_voice,
            ) {
                switchSetting(
                    key = "pref_cardo_toggle_voice_prompts",
                    title = R.string.cardo_control_enable_voice_prompts,
                    icon = R.drawable.ic_voice,
                    connectedOnly = true,
                    defaultValue = false,
                )
                seekbar(
                    key = "pref_cardo_standby_volume",
                    title = R.string.cardo_control_volume,
                    icon = R.drawable.ic_volume_up,
                    min = 0,
                    max = 15,
                    defaultValue = 3,
                    showValue = true,
                )
            }
        }
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_ls2_4x
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_supercars
    }

    override fun isExperimental(): Boolean {
        return true
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.HEAD_MOUNTED
    }

    companion object {
        private fun fmPresetEntries(device: GBDevice): List<ListEntry> {
            val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
            val context = GBApplication.getContext()
            return (1..6).mapNotNull { index ->
                val freqKhz100 = prefs.getInt("pref_cardo_fm_preset_$index", 0)
                if (freqKhz100 <= 0) return@mapNotNull null
                if (index == 4) return@mapNotNull null
                val label = context.getString(R.string.cardo_control_fm_frequency_subtitle, freqKhz100 * 0.01)
                ListEntry.Text(index.toString(), label)
            }
        }
    }
}
