/*  Copyright (C) 2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.nothing

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.prefs.NothingAudioMode
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.prefs.NothingEqualizer
import nodomain.freeyourgadget.gadgetbridge.devices.nothing.prefs.NothingTapAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.nothing.Ear1Support
import nodomain.freeyourgadget.gadgetbridge.service.devices.nothing.NothingBudsPreferences

abstract class AbstractEarCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getManufacturer(): String {
        return "Nothing"
    }

    override fun supportsFindDevice(device: GBDevice): Boolean {
        return true
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.EARBUDS
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 3
    }

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> {
        val battery1 = BatteryConfig(0, R.drawable.ic_tws_case, R.string.battery_case)
        val battery2 = BatteryConfig(1, R.drawable.ic_nothing_ear_l, R.string.left_earbud)
        val battery3 = BatteryConfig(2, R.drawable.ic_nothing_ear_r, R.string.right_earbud)
        return arrayOf<BatteryConfig>(battery1, battery2, battery3)
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return Ear1Support::class.java
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec {
        return deviceSettings {
            screen(
                key = "pref_screen_audio",
                title = R.string.pref_header_audio,
                icon = R.drawable.ic_music_note
            ) {
                if (supportsInEarDetection()) {
                    switchSetting(
                        key = "pref_nothing_inear_detection",
                        title = R.string.nothing_prefs_inear_title,
                        summary = R.string.nothing_prefs_inear_summary,
                        icon = R.drawable.ic_autoplay,
                        defaultValue = false,
                    )
                }
                if (audioModes.isNotEmpty()) {
                    enumList<NothingAudioMode>(
                        key = DeviceSettingsPreferenceConst.PREF_NOTHING_EAR1_AUDIOMODE,
                        title = R.string.nothing_prefs_audiomode_title,
                        icon = R.drawable.ic_auto_awesome,
                        defaultValue = NothingAudioMode.OFF,
                        filter = {
                            audioModes.contains(it)
                        }
                    )
                }
                if (equalizerPresets.isNotEmpty()) {
                    enumList<NothingEqualizer>(
                        key = DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER,
                        title = R.string.prefs_equalizer_preset,
                        icon = R.drawable.ic_equalizer,
                        defaultValue = NothingEqualizer.DIRAC,
                        filter = {
                            equalizerPresets.contains(it)
                        }
                    )
                }
                if (supportsUltraBass()) {
                    switchSetting(
                        key = "pref_nothing_ultra_bass_enabled",
                        title = R.string.nothing_prefs_ultra_bass_title,
                        summary = R.string.nothing_prefs_ultra_bass_summary,
                        icon = R.drawable.ic_speaker,
                        defaultValue = false,
                    )
                    seekbar(
                        key = "pref_nothing_ultra_bass_level",
                        title = R.string.nothing_prefs_ultra_bass_level_title,
                        icon = R.drawable.ic_speaker,
                        min = 1,
                        max = 5,
                        defaultValue = 2,
                        showValue = true,
                        dependency = "pref_nothing_ultra_bass_enabled"
                    )
                }
                if (supportsSpatialAudio()) {
                    switchSetting(
                        key = "pref_nothing_spatial_audio",
                        title = R.string.nothing_prefs_spatial_audio_title,
                        summary = R.string.nothing_prefs_spatial_audio_summary,
                        icon = R.drawable.ic_surround,
                        defaultValue = false,
                    )
                }
            }
            xmlScreen(
                DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
                R.xml.devicesettings_headphones,
                connectedOnly = false,
            )
            if (supportsLowLatency()) {
                xmlScreen(
                    DeviceSpecificSettingsScreen.CONNECTION,
                    R.xml.devicesettings_headphones_low_latency,
                    connectedOnly = false,
                )
            }
            if (touchGestures.isNotEmpty()) {
                screen(
                    key = "pref_screen_touch_options",
                    title = R.string.prefs_galaxy_touch_options,
                    icon = R.drawable.ic_touch
                ) {
                    Side.entries.forEach { side ->
                        category(
                            key = side.key,
                            title = side.title,
                        )
                        {
                            touchGestures.filter { it.side == side }
                                .forEach { gesture ->
                                    val meta = gesture.type.meta()
                                    enumList<NothingTapAction>(
                                        key = gesture.key,
                                        title = meta.title,
                                        icon = meta.icon,
                                        defaultValue = NothingTapAction.OFF,
                                        filter = {
                                            allowedActionsFor(gesture).isEmpty() || it in allowedActionsFor(
                                                gesture
                                            )
                                        },
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_nothingear
    }

    abstract fun incrementCounter(): Boolean

    open fun supportsInEarDetection(): Boolean {
        return true
    }

    open fun supportsLowLatency(): Boolean {
        return false
    }

    open val audioModes: List<NothingAudioMode>
        get() = listOf(
            NothingAudioMode.ANC,
            NothingAudioMode.OFF
        )

    open val equalizerPresets: List<NothingEqualizer>
        get() = emptyList()

    open fun supportsUltraBass(): Boolean {
        return false
    }

    /**
     * Returns the {@link TapGesture} values supported by this device.
     * <p>
     * Only gestures present in the returned list are exposed as configurable options for
     * this device; any {@link TapGesture} not included here is considered unsupported and
     * is filtered out before being presented to the user.
     *
     * @return the list of tap gestures supported by this device
     */
    open val touchGestures: List<TapGesture> get() = emptyList()

    /**
     * Returns the list of {@link NothingTapAction} values that are valid for the given
     * {@link TapGesture} on this device.
     * <p>
     * Not every device supports the full range of tap actions for every gesture: depending
     * on hardware capabilities or firmware limitations, some combinations of side and tap
     * type may only allow a restricted subset of actions. Subclasses should override this
     * method to declare those restrictions; the base implementation imposes no restriction
     * and returns every available action.
     *
     * @param gesture the gesture (side + tap type) for which allowed actions are requested
     * @return the list of actions allowed for {@code gesture} on this device; defaults to
     *         all {@link NothingTapAction} values when no restriction applies
     */
    open fun allowedActionsFor(gesture: TapGesture): List<NothingTapAction> =
        NothingTapAction.entries


    open fun supportsSpatialAudio(): Boolean {
        return false
    }

    //helpers for the DSL above

    enum class Side(val key: String, val title: Int) {
        LEFT("oppo_touch_header_left", R.string.left_earbud),
        RIGHT("oppo_touch_header_right", R.string.right_earbud),
    }

    data class NothingTapTypeMeta(val title: Int, val icon: Int)

    fun NothingTapAction.NothingTapType.meta(): NothingTapTypeMeta = when (this) {
        NothingTapAction.NothingTapType.TAP_2 -> NothingTapTypeMeta(
            R.string.double_tap,
            R.drawable.ic_filter_2
        )

        NothingTapAction.NothingTapType.TAP_3 -> NothingTapTypeMeta(
            R.string.triple_tap,
            R.drawable.ic_filter_3
        )

        NothingTapAction.NothingTapType.TAP_1_HOLD -> NothingTapTypeMeta(
            R.string.tap_and_hold,
            R.drawable.ic_horizontal_rule
        )

        NothingTapAction.NothingTapType.TAP_2_HOLD -> NothingTapTypeMeta(
            R.string.double_tap_and_hold,
            R.drawable.ic_double_tap_hold
        )
    }

    enum class TapGesture(
        val side: Side,
        val type: NothingTapAction.NothingTapType,
        val key: String
    ) {
        LEFT_TAP_2(
            Side.LEFT,
            NothingTapAction.NothingTapType.TAP_2,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__LEFT__TAP_2
        ),
        LEFT_TAP_3(
            Side.LEFT,
            NothingTapAction.NothingTapType.TAP_3,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__LEFT__TAP_3
        ),
        LEFT_TAP_1_HOLD(
            Side.LEFT,
            NothingTapAction.NothingTapType.TAP_1_HOLD,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__LEFT__TAP_1_HOLD
        ),
        LEFT_TAP_2_HOLD(
            Side.LEFT,
            NothingTapAction.NothingTapType.TAP_2_HOLD,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__LEFT__TAP_2_HOLD
        ),
        RIGHT_TAP_2(
            Side.RIGHT,
            NothingTapAction.NothingTapType.TAP_2,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__RIGHT__TAP_2
        ),
        RIGHT_TAP_3(
            Side.RIGHT,
            NothingTapAction.NothingTapType.TAP_3,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__RIGHT__TAP_3
        ),
        RIGHT_TAP_1_HOLD(
            Side.RIGHT,
            NothingTapAction.NothingTapType.TAP_1_HOLD,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__RIGHT__TAP_1_HOLD
        ),
        RIGHT_TAP_2_HOLD(
            Side.RIGHT,
            NothingTapAction.NothingTapType.TAP_2_HOLD,
            NothingBudsPreferences.PREF_CMF_BUDS_TOUCH__RIGHT__TAP_2_HOLD
        ),
    }
}
