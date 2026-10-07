package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmRegion
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.ControlRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.ControlSubset


fun fmRadioToggle(on: Boolean): ControlRequest =
    ControlRequest(ControlSubset.FM, byteArrayOf(if (on) 0x00 else 0x01, 0x00))

sealed interface FmTuneMode {
    enum class Simple(val code: Int) : FmTuneMode {
        SEEK_DOWN(0x02), SEEK_UP(0x03), SCAN_UP(0x04), SCAN_DOWN(0x05),
        AUTO_TUNE(0x06), STOP_SCAN(0x09)
    }
    data class Preset(val index: Int) : FmTuneMode {
        //index 0 is present but empty / not used by the device
        init {
            require(index in 1..6) { "Invalid preset: $index" }
        }
    }
    data class Frequency(val khz100: Int, val region: CardoFmRegion) : FmTuneMode {
        init {
            require(khz100 in region.minFreq..region.maxFreq) {
                "Invalid frequency: $khz100 (region ${region.name}: ${region.minFreq}..${region.maxFreq})"
            }
        }
    }
}

fun fmRadioTune(mode: FmTuneMode): ControlRequest {
    val subPayload = when (mode) {
        is FmTuneMode.Simple -> byteArrayOf(mode.code.toByte(), 0x00)
        is FmTuneMode.Preset -> byteArrayOf(0x07, 0x01, mode.index.toByte())
        is FmTuneMode.Frequency -> byteArrayOf(
            0x08, 0x02,
            ((mode.khz100 shr 8) and 0xFF).toByte(),
            (mode.khz100 and 0xFF).toByte()
        )
    }
    return ControlRequest(ControlSubset.FM, subPayload)
}