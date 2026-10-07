package nodomain.freeyourgadget.gadgetbridge.devices.eightbitdo

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.screen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.eightbitdo.EightBitDoButton
import nodomain.freeyourgadget.gadgetbridge.service.devices.eightbitdo.EightBitDoMicroSupport
import nodomain.freeyourgadget.gadgetbridge.util.HidKey
import java.util.regex.Pattern

class EightBitDoMicroCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        // The "8BitDo Micro gamepad" is actual HID keyboard
        // 80EL is the BLE interface the app connects to
        // They can be connected to different hosts at the same time
        return Pattern.compile("^80EL$")
    }

    override fun getManufacturer(): String {
        return "8BitDo"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return EightBitDoMicroSupport::class.java
    }

    override fun createDevice(candidate: GBDeviceCandidate, deviceType: DeviceType): GBDevice? {
        val gbDevice = super.createDevice(candidate, deviceType)
        gbDevice?.alias = GBApplication.getContext().getString(deviceNameResource)
        return gbDevice
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_eightbitdo_micro
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_gamepad
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.GAMEPAD
    }

    override fun getBatteryCount(device: GBDevice): Int {
        // Does not seem to report battery
        return 0
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_DISABLE_SLEEP,
            title = R.string.eightbitdo_disable_sleep,
            icon = R.drawable.ic_activity_sleep,
            defaultValue = false,
        )
        screen(
            key = DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_SCREEN_KEYMAP,
            title = R.string.eightbitdo_keyboard_mapping,
            icon = R.drawable.ic_videogame,
        ) {
            action(
                key = DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_KEYMAP_RESET,
                title = R.string.eightbitdo_keymap_reset_title,
                icon = R.drawable.ic_history,
                confirmationMessage = R.string.eightbitdo_keymap_reset_confirmation,
                onClick = { _, device ->
                    GBApplication.deviceService(device).onSendConfiguration(
                        DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_KEYMAP_RESET
                    )
                    true
                },
            )
            category(key = "eightbitdo_buttons", title = R.string.eightbitdo_keyboard_mapping) {
                for (button in EightBitDoButton.entries) {
                    sortableList(
                        key = button.prefKey,
                        title = button.titleRes,
                        icon = button.iconRes,
                        entries = HID_KEY_ENTRIES,
                    )
                }
            }
        }
        screen(DeviceSpecificSettingsScreen.DEVELOPER, icon = R.drawable.ic_developer_mode) {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_EIGHTBITDO_REALTIME_BUTTONS,
                title = R.string.eightbitdo_realtime_buttons_title,
                summary = R.string.eightbitdo_realtime_buttons_summary,
                icon = R.drawable.ic_videogame,
                defaultValue = false,
            )
        }
    }

    companion object {
        private val HID_KEY_ENTRIES: List<ListEntry> = HidKey.entries.map { ListEntry.Text(it.prefValue, it.label) }
    }
}
