package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallDirection
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoCallState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.decodeDeviceState
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class CardoCallSequenceTest: TestBase()
{

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `outgoing call establishing - confirmed by official app UI`() {
        val payload = bytes(0x01, 0x41, 0x00, 0x23, 0xaa, 0x00)
        val state = decodeDeviceState(payload)

        assertEquals(CardoState.STAND_BY, state.state)
        assertEquals(CardoCallState.ESTABLISHING, state.callState)
        assertEquals(CardoCallDirection.OUTGOING, state.callDirection)
    }

    @Test
    fun `outgoing call establishing while device state is MOBILE_CALL - real capture`() {
        val payload = bytes(0x03, 0x41, 0x00, 0x23, 0xaa, 0x00)
        val state = decodeDeviceState(payload)

        assertEquals(CardoState.MOBILE_CALL, state.state)
        assertEquals(CardoCallState.ESTABLISHING, state.callState)
        assertEquals(CardoCallDirection.OUTGOING, state.callDirection)
    }

    @Test
    fun `outgoing call progresses to active SCO - real capture`() {
        val payload = bytes(0x03, 0x42, 0x00, 0x23, 0xaa, 0x00)
        val state = decodeDeviceState(payload)

        assertEquals(CardoState.MOBILE_CALL, state.state)
        assertEquals(CardoCallState.ACTIVE_SCO, state.callState)
        assertEquals(CardoCallDirection.OUTGOING, state.callDirection)
    }

    @Test
    fun `outgoing call progresses to active no SCO - the sample that broke every wrong bit layout`() {
        val payload = bytes(0x03, 0x46, 0x00, 0x23, 0xaa, 0x00)
        val state = decodeDeviceState(payload)

        assertEquals(CardoState.MOBILE_CALL, state.state)
        assertEquals(CardoCallState.ACTIVE_NO_SCO, state.callState)
        assertEquals(CardoCallDirection.OUTGOING, state.callDirection)
    }

    @Test
    fun `call ends and state returns to idle unknown direction - real capture`() {
        val payload = bytes(0x01, 0x00, 0x00, 0x23, 0xaa, 0x00)
        val state = decodeDeviceState(payload)

        assertEquals(CardoState.STAND_BY, state.state)
        assertEquals(CardoCallState.IDLE, state.callState)
        assertEquals(CardoCallDirection.UNKNOWN, state.callDirection)
    }
}
