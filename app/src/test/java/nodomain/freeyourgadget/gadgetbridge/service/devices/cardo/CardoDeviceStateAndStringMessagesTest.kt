package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallDirection
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoDmcGroupState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.CardoResponse
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.decodeDeviceState
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardoDeviceStateAndStringMessagesTest: TestBase() {

    @Test
    fun `device state decodes all seven fields from real device capture`() {
        val payload = byteArrayOf(0x1, 0x0, 0x6, 0x23, 0xbe.toByte(), 0x0)

        val state = decodeDeviceState(payload)

        assertEquals(CardoState.STAND_BY, state.state)
        assertEquals(CardoCallState.IDLE, state.callState)
        assertEquals(CardoCallDirection.UNKNOWN, state.callDirection)
        assertEquals(CardoFmState.IDLE, state.fmState)
        assertEquals(6, state.currentSelectedIndex)
        assertEquals(9150, state.currentStation)
        assertEquals(CardoDmcGroupState.READY, state.dmcGroupState)
    }

    @Test
    fun `device state field bit sizes sum to less than full payload - documented gap`() {
        val knownBits = 8 + 2 + 6 + 4 + 4 + 16 + 2
        val realPayloadBits = 6 * 8
        assertEquals(42, knownBits)
        assertEquals(48, realPayloadBits)
        assert(knownBits < realPayloadBits) {
            "Expected a gap between known bytes and complete payload (6 bit left, not yet known)"
        }
    }

    @Test
    fun `payload length byte above 127 is treated as unsigned, not as negative`() {
        val payloadLength = 200
        val payloadBytes = ByteArray(payloadLength) { i -> (0x41 + (i % 26)).toByte() }
        val fullMessage = byteArrayOf(0x42, payloadLength.toByte()) + payloadBytes

        val response = CardoResponse(fullMessage)

        assertNotNull(
            "friendlyName should not be null: parsing a 200-byte payload must " +
                    "succeed even with length byte > 127",
            response.friendlyName
        )
        assertEquals(payloadLength, response.friendlyName?.length)
        assertEquals("ABCDEFGHIJ", response.friendlyName?.take(10))
    }

    @Test
    fun `payload length byte at exactly 128 - the first value that would be negative as signed byte`() {
        val payloadLength = 128
        val payloadBytes = ByteArray(payloadLength) { 0x58.toByte() } // repeated 'X'
        val fullMessage = byteArrayOf(0x42, payloadLength.toByte()) + payloadBytes

        val response = CardoResponse(fullMessage)

        assertNotNull(response.friendlyName)
        assertEquals(payloadLength, response.friendlyName?.length)
    }

    @Test
    fun `payload length byte at 127 - the last value that fits in a signed byte - still works`() {
        val payloadLength = 127
        val payloadBytes = ByteArray(payloadLength) { 0x59.toByte() } // repeated 'Y'
        val fullMessage = byteArrayOf(0x42, payloadLength.toByte()) + payloadBytes

        val response = CardoResponse(fullMessage)

        assertNotNull(response.friendlyName)
        assertEquals(payloadLength, response.friendlyName?.length)
    }
}
