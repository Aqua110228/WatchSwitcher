package nodomain.freeyourgadget.gadgetbridge.devices.shokz

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.Language
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.equalizerPreset
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.languages
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.multipointPairing
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzEqualizer
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzMediaSource
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzMp3PlaybackMode
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzSupport

abstract class ShokzCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getManufacturer(): String? {
        return "Shokz"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return ShokzSupport::class.java
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun addBatteryPollingSettings(): Boolean {
        // The device only reports battery level once, when we request it right after connecting -
        // there is no notification for battery changes, so we need to poll periodically.
        return true
    }

    override fun getDefaultIconResource(): Int {
        // TODO dedicated icon
        return R.drawable.ic_device_headphones
    }

    /**
     * Whether this device has an onboard MP3 player (e.g. the OpenSwim Pro), exposing a
     * separate media source, MP3-only equalizer preset and playback mode. Most Shokz
     * headphones (e.g. the OpenRun Pro) are Bluetooth-only and don't support this.
     */
    open fun supportsMp3(): Boolean = true

    /**
     * Whether this device supports customizing the long-press multi-function button and
     * simultaneous volume up/down controls (CONTROLS_GET/CONTROLS_SET). Confirmed present on
     * the OpenSwim Pro; the OpenRun Pro 2 does not respond to CONTROLS_GET at all.
     */
    open fun supportsControls(): Boolean = true

    /**
     * Whether this device supports the Bass Boost / Treble Boost equalizer presets, in addition
     * to Standard and Vocal. Confirmed present on the OpenRun Pro 2 (codes 0x03/0x04); unverified
     * on other Shokz devices, so disabled by default.
     */
    open fun supportsBassTreble(): Boolean = false

    /**
     * Whether this device supports the 5-band custom equalizer (code 0x05), in addition to the
     * fixed presets. Confirmed present on the OpenRun Pro 2; unverified on other Shokz devices,
     * so disabled by default.
     */
    open fun supportsCustomEqualizer(): Boolean = false

    /**
     * Whether this device supports the Classic (bone-conduction only, air speaker disabled) and
     * Volume Boost equalizer presets (codes 0x08/0x0a). The official app only shows these when
     * its account region is set to the US; Gadgetbridge has no such concept, so this is gated
     * purely on device support. Confirmed present on the OpenRun Pro 2; unverified on other
     * Shokz devices, so disabled by default.
     */
    open fun supportsClassicAndVolumeBoost(): Boolean = false

    /**
     * Encodes the EQUALIZER_SET payload for a given preset. Beyond the preset code (first byte),
     * these carry extra device-specific tuning parameters that were reverse-engineered from the
     * respective official app/device traffic, and may not be identical across Shokz models.
     */
    open fun equalizerArgs(equalizer: ShokzEqualizer): ByteArray = when (equalizer) {
        ShokzEqualizer.STANDARD, ShokzEqualizer.SWIMMING, ShokzEqualizer.BASS, ShokzEqualizer.TREBLE,
        ShokzEqualizer.CLASSIC, ShokzEqualizer.VOLUME_BOOST ->
            byteArrayOf(equalizer.code.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        ShokzEqualizer.VOCAL ->
            byteArrayOf(equalizer.code.toByte(), 0xfc.toByte(), 0x00, 0x03, 0x02, 0x02, 0x00, 0x00)

        // CUSTOM carries 5 user-adjustable band gains, built separately from preferences by
        // the caller - this default is never actually sent.
        ShokzEqualizer.CUSTOM ->
            byteArrayOf(equalizer.code.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    }

    /**
     * Encodes the EQUALIZER_SET payload for the custom equalizer, given the 5 band gains
     * (each in the -5..5 range, as reverse-engineered from the official Shokz app).
     */
    open fun customEqualizerArgs(bands: IntArray): ByteArray = byteArrayOf(
        ShokzEqualizer.CUSTOM.code.toByte(),
        bands[0].toByte(), bands[1].toByte(), bands[2].toByte(), bands[3].toByte(), bands[4].toByte(),
        0x00, 0x00,
    )

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        multipointPairing()
        languages(Language.EN, Language.ZH, Language.JA, Language.KO)

        if (supportsMp3()) {
            enumList<ShokzMediaSource>(
                key = DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
                title = R.string.media_source,
                icon = R.drawable.ic_music_note,
                defaultValue = ShokzMediaSource.BLUETOOTH,
            )
        }

        // Equalizer presets - same enum, filtered to entries valid for each media source
        equalizerPreset<ShokzEqualizer>(
            key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_BLUETOOTH,
            title = R.string.sony_equalizer_bluetooth,
            defaultValue = ShokzEqualizer.STANDARD,
            filter = {
                ShokzMediaSource.BLUETOOTH in it.sources &&
                    (supportsBassTreble() || it != ShokzEqualizer.BASS && it != ShokzEqualizer.TREBLE) &&
                    (supportsCustomEqualizer() || it != ShokzEqualizer.CUSTOM) &&
                    (supportsClassicAndVolumeBoost() || it != ShokzEqualizer.CLASSIC && it != ShokzEqualizer.VOLUME_BOOST)
            },
            visibleWhen = { prefs ->
                !supportsMp3() || ShokzMediaSource.fromPreference(
                    prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                ) == ShokzMediaSource.BLUETOOTH
            },
        )

        if (supportsCustomEqualizer()) {
            screen(
                key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM,
                title = R.string.custom,
                icon = R.drawable.ic_graphic_eq,
                visibleWhen = { prefs ->
                    (!supportsMp3() || ShokzMediaSource.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                    ) == ShokzMediaSource.BLUETOOTH) &&
                        ShokzEqualizer.fromPreference(
                            prefs.getString(DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_BLUETOOTH, "")
                        ) == ShokzEqualizer.CUSTOM
                },
            ) {
                val bandKeys = listOf(
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_1,
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_2,
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_3,
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_4,
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_5,
                )
                val bandTitles = listOf(
                    R.string.shokz_equalizer_band_1,
                    R.string.shokz_equalizer_band_2,
                    R.string.shokz_equalizer_band_3,
                    R.string.shokz_equalizer_band_4,
                    R.string.shokz_equalizer_band_5,
                )
                bandKeys.forEachIndexed { i, key ->
                    seekbar(
                        key = key,
                        title = bandTitles[i],
                        icon = R.drawable.ic_graphic_eq,
                        min = -5,
                        max = 5,
                        defaultValue = 0,
                    )
                }
            }
        }

        if (supportsMp3()) {
            equalizerPreset<ShokzEqualizer>(
                key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_MP3,
                title = R.string.sony_equalizer_mp3,
                defaultValue = ShokzEqualizer.STANDARD,
                filter = { ShokzMediaSource.MP3 in it.sources },
                visibleWhen = { prefs ->
                    ShokzMediaSource.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                    ) == ShokzMediaSource.MP3
                },
            )

            enumList<ShokzMp3PlaybackMode>(
                key = DeviceSettingsPreferenceConst.PREF_MEDIA_PLAYBACK_MODE,
                title = R.string.media_playback_mode,
                icon = R.drawable.ic_play,
                defaultValue = ShokzMp3PlaybackMode.NORMAL,
                visibleWhen = { prefs ->
                    ShokzMediaSource.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                    ) == ShokzMediaSource.MP3
                },
            )
        }
        if (supportsControls()) {
            xmlScreen(
                DeviceSpecificSettingsScreen.TOUCH_OPTIONS,
                R.xml.devicesettings_shokz_controls,
                childConnectedKeys = listOf(
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_CONTROLS_LONG_PRESS_MULTI_FUNCTION,
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_CONTROLS_SIMULTANEOUS_VOLUME_UP_DOWN,
                ),
            )
        }
        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
            connectedOnly = false,
        )
    }
}
