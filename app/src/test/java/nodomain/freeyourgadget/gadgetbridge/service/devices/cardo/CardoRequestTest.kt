package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmRegion
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.ControlRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.ControlSubset
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.FactoryResetRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.FmTuneMode
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.GetRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.InitRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.SetRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.SubscribeRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.fmRadioToggle
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.fmRadioTune
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CardoRequestsTest: TestBase() {

    @Test
    fun `init request matches original two-byte payload`() {
        val request = InitRequest()
        assertArrayEquals(byteArrayOf(0x03, 0x00, 0x00), request.toByteArray())
    }

    @Test
    fun `factory reset matches original control payload`() {
        val request = FactoryResetRequest()
        assertArrayEquals(byteArrayOf(0x30, 0x05, 0x55), request.toByteArray())
    }

    @Test
    fun `get request encodes info type ids with length prefix`() {
        val request = GetRequest(infoTypeIds = listOf(0x03, 0x05, 0x02, 0x00))
        assertArrayEquals(
            byteArrayOf(0x00, 0x04, 0x03, 0x05, 0x02, 0x00),
            request.toByteArray()
        )
    }

    @Test
    fun `control request with FM subset matches real device capture`() {
        val request = ControlRequest(ControlSubset.FM, byteArrayOf(0x00, 0x00))
        assertArrayEquals(byteArrayOf(0x30, 0x20, 0x00, 0x00), request.toByteArray())
    }

    @Test
    fun `fm radio toggle off matches real device capture`() {
        val request = fmRadioToggle(on = false)
        assertArrayEquals(byteArrayOf(0x30, 0x20, 0x01, 0x00), request.toByteArray())
    }

    @Test
    fun `fm radio toggle on produces the opposite flag byte`() {
        val request = fmRadioToggle(on = true)
        assertArrayEquals(byteArrayOf(0x30, 0x20, 0x00, 0x00), request.toByteArray())
    }

    @Test
    fun `fm radio tune to frequency matches real device capture`() {
        val request = fmRadioTune(FmTuneMode.Frequency(9150, CardoFmRegion.WORLDWIDE))
        assertArrayEquals(
            byteArrayOf(0x30, 0x20, 0x08, 0x02, 0x23, 0xbe.toByte()),
            request.toByteArray()
        )
    }

    @Test
    fun `fm radio tune rejects frequency outside region range`() {
        assertThrows(IllegalArgumentException::class.java) {
            FmTuneMode.Frequency(11000, CardoFmRegion.WORLDWIDE)
        }
    }

    @Test
    fun `fm radio tune accepts frequency at exact region boundaries`() {
        val atMin = fmRadioTune(FmTuneMode.Frequency(CardoFmRegion.WORLDWIDE.minFreq, CardoFmRegion.WORLDWIDE))
        val atMax = fmRadioTune(FmTuneMode.Frequency(CardoFmRegion.WORLDWIDE.maxFreq, CardoFmRegion.WORLDWIDE))
        assert(atMin.toByteArray().isNotEmpty())
        assert(atMax.toByteArray().isNotEmpty())
    }

    @Test
    fun `fm radio tune preset rejects index above six`() {
        assertThrows(IllegalArgumentException::class.java) {
            FmTuneMode.Preset(7)
        }
    }

    @Test
    fun `fm radio tune preset rejects index zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            FmTuneMode.Preset(0)
        }
    }

    @Test
    fun `fm radio tune preset accepts index one to six`() {
        for (index in 1..6) {
            val request = fmRadioTune(FmTuneMode.Preset(index))
            assertArrayEquals(
                byteArrayOf(0x30, 0x20, 0x07, 0x01, index.toByte()),
                request.toByteArray()
            )
        }
    }

    @Test
    fun `fm radio tune simple modes encode mode code and zero byte`() {
        val expectedCodes = mapOf(
            FmTuneMode.Simple.SEEK_DOWN to 0x02,
            FmTuneMode.Simple.SEEK_UP to 0x03,
            FmTuneMode.Simple.SCAN_UP to 0x04,
            FmTuneMode.Simple.SCAN_DOWN to 0x05,
            FmTuneMode.Simple.AUTO_TUNE to 0x06,
            FmTuneMode.Simple.STOP_SCAN to 0x09
        )
        for ((mode, code) in expectedCodes) {
            val request = fmRadioTune(mode)
            assertArrayEquals(
                byteArrayOf(0x30, 0x20, code.toByte(), 0x00),
                request.toByteArray()
            )
        }
    }

    @Test
    fun `set request prefixes info type id before field payload`() {
        val request = SetRequest(infoTypeId = 0x02, fieldPayload = byteArrayOf(0x01, 0x02, 0x03))
        assertArrayEquals(
            byteArrayOf(0x10, 0x04, 0x02, 0x01, 0x02, 0x03),
            request.toByteArray()
        )
    }

    @Test
    fun `payload longer than 255 bytes is rejected instead of corrupting length byte`() {
        val oversizedPayload = ByteArray(256)
        assertThrows(IllegalArgumentException::class.java) {
            SetRequest(infoTypeId = 0x00, fieldPayload = oversizedPayload).toByteArray()
        }
    }

    @Test
    fun `subscribe request has correct message structure`() {
        val request = SubscribeRequest(servicesBitmask = 0xFF, specificServicesBitmask = 0xFFFF)
        val bytes = request.toByteArray()

        assert(bytes.size == 4) { "Expected: command(1) + services(1) + specificServices(2) = 4 byte" }
        assert(bytes[0] == 0x22.toByte()) { "Expected Command byte 0x22 (SUBSCRIBE)" }
        assert(bytes[1] == 0xFF.toByte()) { "Expected Services bitmask in second byte" }
    }
}
