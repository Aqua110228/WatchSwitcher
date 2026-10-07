package nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

enum class UgreenAncMode(
    val rawValue: Byte,
    override val label: Int,
    val shortLabel: Int = label,
) : LabeledEntry {
    OFF(0xD0.toByte(), R.string.off),
    LIGHT(0xC1.toByte(), R.string.redmi_buds_5_pro_anc_light),
    MEDIUM(0xB1.toByte(), R.string.ugreen_anc_mode_medium),
    DEEP(0xA1.toByte(), R.string.redmi_buds_5_pro_anc_deep),
    ADAPTIVE(0xD1.toByte(), R.string.prefs_active_noise_cancelling_adaptive),
    TRANSPARENCY(0xD2.toByte(), R.string.prefs_active_noise_cancelling_transparency),
    ;

    fun getRawValue(depth: UgreenAncMode? = null): Byte {
        val base = when (depth) {
            DEEP -> 0xA0
            MEDIUM -> 0xB0
            LIGHT -> 0xC0
            ADAPTIVE, null, OFF, TRANSPARENCY -> 0xD0
        }
        return when (this) {
            OFF -> (base or 0x00).toByte()
            DEEP -> 0xA1.toByte()
            MEDIUM -> 0xB1.toByte()
            LIGHT -> 0xC1.toByte()
            ADAPTIVE -> 0xD1.toByte()
            TRANSPARENCY -> (base or 0x02).toByte()
        }
    }

    companion object {
        fun fromPreference(value: String?): UgreenAncMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: ADAPTIVE

        fun fromRawValue(value: Int): UgreenAncMode {
            val uValue = value and 0xFF
            return when (uValue) {
                0, 160, 176, 192, 208 -> OFF
                2, 162, 178, 194, 210 -> TRANSPARENCY
                161 -> DEEP
                177 -> MEDIUM
                193 -> LIGHT
                1, 209 -> ADAPTIVE
                else -> ADAPTIVE
            }
        }

        fun depthFromRawValue(value: Int): UgreenAncMode? {
            val uValue = value and 0xFF
            return when (uValue and 0xF0) {
                0xA0 -> DEEP
                0xB0 -> MEDIUM
                0xC0 -> LIGHT
                0xD0 -> ADAPTIVE
                else -> null
            }
        }
    }
}

enum class UgreenEqualizer(val code: Byte, override val label: Int) : LabeledEntry {
    CLASSIC(0, R.string.haylou_s35_anc_eq_classic),
    POP(3, R.string.nothing_equalizer_pop),
    BASS(6, R.string.haylou_s35_anc_eq_bass),
    JAZZ(1, R.string.soundcore_equalizer_preset_jazz),
    ELECTRONIC(2, R.string.nothing_equalizer_electronic),
    ROCK(5, R.string.nothing_equalizer_rock),
    TREBLE(7, R.string.ugreen_equalizer_treble),
    CLASSICAL(4, R.string.nothing_equalizer_classical),
    ;

    companion object {
        fun fromPreference(value: String?): UgreenEqualizer =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: if ("popular".equals(value, ignoreCase = true)) POP else CLASSIC

        fun fromCode(code: Int): UgreenEqualizer =
            entries.find { (it.code.toInt() and 0xFF) == (code and 0xFF) } ?: CLASSIC
    }
}

enum class UgreenPromptLanguage(val code: Byte, override val label: Int) : LabeledEntry {
    ENGLISH(0, R.string.english),
    CHINESE(1, R.string.chinese),
    ;

    companion object {
        fun fromPreference(value: String?): UgreenPromptLanguage =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: ENGLISH

        fun fromCode(code: Int): UgreenPromptLanguage =
            if ((code and 0xFF) == 1) CHINESE else ENGLISH
    }
}
