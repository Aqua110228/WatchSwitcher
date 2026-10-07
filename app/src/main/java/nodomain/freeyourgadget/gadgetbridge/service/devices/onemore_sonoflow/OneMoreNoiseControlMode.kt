package nodomain.freeyourgadget.gadgetbridge.service.devices.onemore_sonoflow

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

enum class OneMoreNoiseControlMode(val code: Byte, override val label: Int) : LabeledEntry {
    OFF(0x00, R.string.off),
    ANC(0x01, R.string.prefs_active_noise_cancelling),
    TRANSPARENCY(0x03, R.string.prefs_active_noise_cancelling_transparency),
    ;

    companion object {
        @JvmStatic
        fun fromPreference(value: String): OneMoreNoiseControlMode? = entries.find { it.name == value.uppercase() }

        @JvmStatic
        fun fromCode(code: Byte): OneMoreNoiseControlMode? = entries.find { it.code == code }
    }
}
