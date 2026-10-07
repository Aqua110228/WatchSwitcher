package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_ADAPTIVE_NOISE_CANCELLING
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_ADAPTIVE_SOUND
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_AUTO_REPLY_PHONECALL
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_LEFT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_RIGHT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_DOUBLE_CONNECTION
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_PRESET
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_NOISE_CANCELLING_STRENGTH
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_TRANSPARENCY_STRENGTH
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_WEARING_DETECTION
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundCycle
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundMode
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerBand
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerBandLevel
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerPreset
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsGestureAction
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsLongGestureAction
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsNoiseCancellingStrength
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsPosition
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsTapType
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsTransparencyStrength
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.redmibuds.RedmiBudsDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import java.util.Locale

abstract class AbstractRedmiBudsCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getManufacturer(): String = "Xiaomi"

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        RedmiBudsDeviceSupport::class.java

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getBatteryCount(device: GBDevice): Int = 3

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> = arrayOf(
        BatteryConfig(0, R.drawable.ic_tws_case, R.string.battery_case),
        BatteryConfig(1, R.drawable.ic_nothing_ear_l, R.string.left_earbud),
        BatteryConfig(2, R.drawable.ic_nothing_ear_r, R.string.right_earbud),
    )

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_nothingear

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.EARBUDS

    open val ambientSoundModes: List<RedmiBudsAmbientSoundMode> get() = emptyList()

    open val noiseCancellingStrengths: List<RedmiBudsNoiseCancellingStrength> get() = emptyList()

    open val transparencyStrengths: List<RedmiBudsTransparencyStrength> get() = emptyList()

    open val supportsAdaptiveNoiseCancelling: Boolean get() = false

    open val equalizerPresets: List<RedmiBudsEqualizerPreset> get() = emptyList()

    open val supportsCustomEqualizer: Boolean get() = false

    open val supportsAdaptiveSound: Boolean get() = false

    open val supportsWearingDetection: Boolean get() = false

    open val supportsAutoAnswer: Boolean get() = false

    open val supportsDoubleConnection: Boolean get() = false

    open val singleTapActions: List<RedmiBudsGestureAction> get() = emptyList()

    open val tapActions: List<RedmiBudsGestureAction> get() = emptyList()

    open val longPressActions: List<RedmiBudsLongGestureAction> get() = emptyList()

    open val ambientSoundCycles: List<RedmiBudsAmbientSoundCycle> get() = emptyList()

    open val defaultDoubleTapAction: RedmiBudsGestureAction get() = RedmiBudsGestureAction.PLAY_PAUSE

    open val defaultTripleTapActionLeft: RedmiBudsGestureAction get() = RedmiBudsGestureAction.PREVIOUS_TRACK

    open val defaultTripleTapActionRight: RedmiBudsGestureAction get() = RedmiBudsGestureAction.NEXT_TRACK

    open val defaultLongPressAction: RedmiBudsLongGestureAction
        get() = if (RedmiBudsLongGestureAction.AMBIENT_SOUND_CONTROL in longPressActions) {
            RedmiBudsLongGestureAction.AMBIENT_SOUND_CONTROL
        } else {
            RedmiBudsLongGestureAction.VOICE_ASSISTANT
        }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        ambientSoundControl()
        sound()
        system()

        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
            connectedOnly = false,
        )
    }

    private fun DeviceSettingsScope.ambientSoundControl() {
        if (ambientSoundModes.isEmpty()) {
            return
        }

        category(
            key = "pref_key_header_redmibuds_ambient_sound_control",
            title = R.string.pref_header_sony_ambient_sound_control,
        ) {
            enumList<RedmiBudsAmbientSoundMode>(
                key = PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL,
                title = R.string.sony_ambient_sound,
                icon = R.drawable.ic_hearing,
                defaultValue = RedmiBudsAmbientSoundMode.OFF,
                filter = { it in ambientSoundModes },
            )

            if (supportsAdaptiveNoiseCancelling) {
                switchSetting(
                    key = PREF_REDMI_BUDS_ADAPTIVE_NOISE_CANCELLING,
                    title = R.string.pref_adaptive_noise_cancelling_title,
                    summary = R.string.pref_adaptive_noise_cancelling_summary,
                    defaultValue = true,
                    disableDependentsState = true,
                    visibleWhen = prefIs(
                        PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL,
                        RedmiBudsAmbientSoundMode.NOISE_CANCELLING,
                        RedmiBudsAmbientSoundMode.OFF,
                    ),
                )
            }

            if (noiseCancellingStrengths.isNotEmpty()) {
                enumList<RedmiBudsNoiseCancellingStrength>(
                    key = PREF_REDMI_BUDS_NOISE_CANCELLING_STRENGTH,
                    title = R.string.prefs_active_noise_cancelling_level,
                    defaultValue = RedmiBudsNoiseCancellingStrength.BALANCED,
                    dependency = PREF_REDMI_BUDS_ADAPTIVE_NOISE_CANCELLING.takeIf { supportsAdaptiveNoiseCancelling },
                    filter = { it in noiseCancellingStrengths },
                    visibleWhen = prefIs(
                        PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL,
                        RedmiBudsAmbientSoundMode.NOISE_CANCELLING,
                        RedmiBudsAmbientSoundMode.OFF,
                    ),
                )
            }

            if (transparencyStrengths.isNotEmpty()) {
                enumList<RedmiBudsTransparencyStrength>(
                    key = PREF_REDMI_BUDS_TRANSPARENCY_STRENGTH,
                    title = R.string.redmi_buds_5_pro_transparency_strength,
                    defaultValue = RedmiBudsTransparencyStrength.REGULAR,
                    filter = { it in transparencyStrengths },
                    visibleWhen = prefIs(
                        PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL,
                        RedmiBudsAmbientSoundMode.TRANSPARENCY,
                        RedmiBudsAmbientSoundMode.OFF,
                    ),
                )
            }
        }
    }

    private fun DeviceSettingsScope.sound() {
        if (equalizerPresets.isEmpty() && !supportsCustomEqualizer && !supportsAdaptiveSound) {
            return
        }

        category(
            key = "pref_key_header_redmibuds_sound",
            title = R.string.pref_header_sound,
        ) {
            if (supportsCustomEqualizer) {
                // The ten bands of the user curve need a screen of their own, or they crowd
                // out everything else in this category.
                screen(
                    key = "pref_screen_redmibuds_equalizer",
                    title = R.string.pref_header_equalizer,
                    icon = R.drawable.ic_graphic_eq,
                ) {
                    equalizerPreset()
                    customEqualizer()
                }
            } else {
                equalizerPreset()
            }

            if (supportsAdaptiveSound) {
                switchSetting(
                    key = PREF_REDMI_BUDS_ADAPTIVE_SOUND,
                    title = R.string.redmi_buds_5_pro_adaptive_sound,
                    summary = R.string.redmi_buds_5_pro_adaptive_sound_description,
                    defaultValue = false,
                )
            }
        }
    }

    private fun DeviceSettingsScope.equalizerPreset() {
        if (equalizerPresets.isEmpty()) {
            return
        }

        enumList<RedmiBudsEqualizerPreset>(
            key = PREF_REDMI_BUDS_EQUALIZER_PRESET,
            title = R.string.prefs_equalizer_preset,
            icon = R.drawable.ic_graphic_eq,
            defaultValue = equalizerPresets.first(),
            filter = { it in equalizerPresets },
        )
    }

    private fun DeviceSettingsScope.customEqualizer() {
        val visibleWhen: ((Prefs) -> Boolean)? = if (equalizerPresets.isEmpty()) {
            null
        } else {
            prefIs(
                PREF_REDMI_BUDS_EQUALIZER_PRESET,
                RedmiBudsEqualizerPreset.CUSTOM,
                equalizerPresets.first(),
            )
        }

        category(
            key = "pref_key_header_redmibuds_custom_equalizer",
            title = R.string.soundcore_equalizer_custom_title,
            visibleWhen = visibleWhen,
        ) {
            RedmiBudsEqualizerBand.entries.forEach { band ->
                enumList<RedmiBudsEqualizerBandLevel>(
                    key = band.preferenceKey,
                    title = band.label,
                    icon = R.drawable.ic_graphic_eq,
                    defaultValue = RedmiBudsEqualizerBandLevel.FLAT,
                    visibleWhen = visibleWhen,
                )
            }
        }
    }

    private fun DeviceSettingsScope.system() {
        if (!supportsWearingDetection && !supportsAutoAnswer && !supportsDoubleConnection &&
            singleTapActions.isEmpty() && tapActions.isEmpty() && longPressActions.isEmpty()
        ) {
            return
        }

        category(
            key = "pref_key_header_redmibuds_system",
            title = R.string.pref_header_system,
        ) {
            touchOptions()

            if (supportsWearingDetection) {
                switchSetting(
                    key = PREF_REDMI_BUDS_WEARING_DETECTION,
                    title = R.string.nothing_prefs_inear_title,
                    summary = R.string.nothing_prefs_inear_summary,
                    icon = R.drawable.ic_pause,
                    defaultValue = false,
                )
            }

            if (supportsAutoAnswer) {
                switchSetting(
                    key = PREF_REDMI_BUDS_AUTO_REPLY_PHONECALL,
                    title = R.string.pref_auto_reply_calls_title,
                    summary = R.string.pref_auto_reply_calls_summary,
                    icon = R.drawable.ic_phone,
                    defaultValue = false,
                )
            }

            if (supportsDoubleConnection) {
                switchSetting(
                    key = PREF_REDMI_BUDS_DOUBLE_CONNECTION,
                    title = R.string.redmi_buds_5_pro_double_connection,
                    summary = R.string.redmi_buds_5_pro_double_connection_description,
                    defaultValue = false,
                )
            }
        }
    }

    private fun DeviceSettingsScope.touchOptions() {
        if (singleTapActions.isEmpty() && tapActions.isEmpty() && longPressActions.isEmpty()) {
            return
        }

        screen(
            key = "pref_screen_redmibuds_touch_options",
            title = R.string.prefs_galaxy_touch_options,
            icon = R.drawable.ic_touch,
        ) {
            tapCategory(
                RedmiBudsTapType.SINGLE,
                singleTapActions,
                RedmiBudsGestureAction.NONE,
                RedmiBudsGestureAction.NONE
            )
            tapCategory(RedmiBudsTapType.DOUBLE, tapActions, defaultDoubleTapAction, defaultDoubleTapAction)
            tapCategory(RedmiBudsTapType.TRIPLE, tapActions, defaultTripleTapActionLeft, defaultTripleTapActionRight)
            longPressCategory()
        }
    }

    private fun DeviceSettingsScope.tapCategory(
        tapType: RedmiBudsTapType,
        actions: List<RedmiBudsGestureAction>,
        defaultLeft: RedmiBudsGestureAction,
        defaultRight: RedmiBudsGestureAction,
    ) {
        if (actions.isEmpty()) {
            return
        }

        category(
            key = "pref_key_header_redmibuds_${tapType.name.lowercase(Locale.ROOT)}_tap",
            title = tapType.title,
        ) {
            enumList<RedmiBudsGestureAction>(
                key = tapType.getPreferenceKey(RedmiBudsPosition.LEFT),
                title = R.string.prefs_left,
                icon = tapType.icon,
                defaultValue = defaultLeft,
                filter = { it in actions },
            )
            enumList<RedmiBudsGestureAction>(
                key = tapType.getPreferenceKey(RedmiBudsPosition.RIGHT),
                title = R.string.prefs_right,
                defaultValue = defaultRight,
                filter = { it in actions },
            )
        }
    }

    private fun DeviceSettingsScope.longPressCategory() {
        if (longPressActions.isEmpty()) {
            return
        }

        category(
            key = "pref_key_header_redmibuds_long_press",
            title = RedmiBudsTapType.LONG.title,
        ) {
            longPress(
                RedmiBudsPosition.LEFT,
                R.string.sony_button_mode_left,
                PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_LEFT
            )
            longPress(
                RedmiBudsPosition.RIGHT,
                R.string.sony_button_mode_right,
                PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_RIGHT
            )
        }
    }

    private fun DeviceSettingsScope.longPress(
        position: RedmiBudsPosition,
        title: Int,
        cycleKey: String,
    ) {
        val modeKey = RedmiBudsTapType.LONG.getPreferenceKey(position)

        enumList<RedmiBudsLongGestureAction>(
            key = modeKey,
            title = title,
            icon = RedmiBudsTapType.LONG.icon,
            defaultValue = defaultLongPressAction,
            filter = { it in longPressActions },
        )

        if (RedmiBudsLongGestureAction.AMBIENT_SOUND_CONTROL in longPressActions && ambientSoundCycles.isNotEmpty()) {
            enumList<RedmiBudsAmbientSoundCycle>(
                key = cycleKey,
                title = R.string.sony_ambient_sound_control_button_modes,
                defaultValue = RedmiBudsAmbientSoundCycle.ALL,
                filter = { it in ambientSoundCycles },
                visibleWhen = prefIs(
                    modeKey,
                    RedmiBudsLongGestureAction.AMBIENT_SOUND_CONTROL,
                    defaultLongPressAction,
                ),
            )
        }
    }

    /**
     * Builds a predicate that is true while [key] holds [expected].
     *
     * @param key      the preference key.
     * @param expected the value that makes the predicate true.
     * @param fallback the value to assume while the preference is unset.
     * @return the predicate.
     */
    private fun <T : Enum<T>> prefIs(key: String, expected: T, fallback: T): (Prefs) -> Boolean {
        val expectedValue = expected.name.lowercase(Locale.ROOT)
        val fallbackValue = fallback.name.lowercase(Locale.ROOT)
        return { prefs -> prefs.getString(key, fallbackValue) == expectedValue }
    }
}
