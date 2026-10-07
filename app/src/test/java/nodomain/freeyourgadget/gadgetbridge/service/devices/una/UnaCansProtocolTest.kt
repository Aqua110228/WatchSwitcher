/*  Copyright (C) 2026 Toby Murray

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.una

import nodomain.freeyourgadget.gadgetbridge.devices.una.UnaConstants
import nodomain.freeyourgadget.gadgetbridge.service.devices.una.UnaFtsProtocolTest.Companion.hexToByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnaCansProtocolTest {
    @Test
    fun buildEvent_matchesTheFrameTheWatchsOwnCompanionSends() {
        // Captured from the vendor app on the air: uid 0x2DD69B6A, Add, Message.
        val event = UnaCansProtocol.buildEvent(
            uid = 0x2DD69B6A,
            action = UnaConstants.ACTION_ADD,
            category = UnaConstants.CATEGORY_MESSAGE,
        )
        assertArrayEquals(hexToByteArray("01006a9bd62d01"), event)
    }

    @Test
    fun buildEvent_isAlwaysSevenBytes() {
        assertEquals(7, UnaCansProtocol.buildEvent(0, UnaConstants.ACTION_REMOVE, UnaConstants.CATEGORY_CALL).size)
    }

    @Test
    fun buildAttributeResponse_matchesTheCapturedResponseHeader() {
        // The vendor app's own responseData began 03 6a 9b d6 2d 05 15 00, i.e. AppIdentifier
        // with a 21-byte value.
        val response = UnaCansProtocol.buildAttributeResponse(
            uid = 0x2DD69B6A,
            attributes = listOf(UnaConstants.ATTRIBUTE_APP_IDENTIFIER to ByteArray(21)),
        )
        assertArrayEquals(hexToByteArray("036a9bd62d051500"), response.copyOfRange(0, 8))
        assertEquals(5 + 3 + 21, response.size)
    }

    @Test
    fun buildAttributeResponse_withNoAttributesIsHeaderOnly() {
        assertEquals(5, UnaCansProtocol.buildAttributeResponse(1, emptyList()).size)
    }

    @Test
    fun buildErrorResponse_isABareCode() {
        assertArrayEquals(
            byteArrayOf(UnaConstants.ERROR_NOTIFICATION_UID_NOT_FOUND.toByte()),
            UnaCansProtocol.buildErrorResponse(UnaConstants.ERROR_NOTIFICATION_UID_NOT_FOUND),
        )
    }

    @Test
    fun parseCommand_readsTheTwoPhaseFetchTheWatchActuallyPerforms() {
        // First round: metadata, with the per-attribute limits the watch asked for.
        val first = hexToByteArray("03" + "6a9bd62d" + "052000" + "01ff00" + "02ff00" + "071000" + "081000" + "091000")
        val metadata = UnaCansProtocol.parseCommand(first) as UnaCansCommand.RequestAttributes
        assertEquals(0x2DD69B6A, metadata.uid)
        assertEquals(
            listOf(5 to 32, 1 to 255, 2 to 255, 7 to 16, 8 to 16, 9 to 16),
            metadata.requested.map { it.attributeId to it.maxLength },
        )

        // Second round, about a quarter second later: the message body on its own.
        val second = hexToByteArray("03" + "6a9bd62d" + "04f807")
        val body = UnaCansProtocol.parseCommand(second) as UnaCansCommand.RequestAttributes
        assertEquals(listOf(UnaConstants.ATTRIBUTE_MESSAGE to 2040), body.requested.map { it.attributeId to it.maxLength })
    }

    @Test
    fun parseCommand_readsBothActions() {
        val positive = UnaCansProtocol.parseCommand(hexToByteArray("04" + "6a9bd62d")) as UnaCansCommand.ExecuteAction
        assertTrue(positive.positive)
        assertEquals(0x2DD69B6A, positive.uid)
        val negative = UnaCansProtocol.parseCommand(hexToByteArray("05" + "6a9bd62d")) as UnaCansCommand.ExecuteAction
        assertEquals(false, negative.positive)
    }

    @Test
    fun parseCommand_ignoresATrailingPartialEntry() {
        // A ragged tail must not invent an attribute out of whatever bytes are left.
        val ragged = hexToByteArray("03" + "6a9bd62d" + "052000" + "01")
        val parsed = UnaCansProtocol.parseCommand(ragged) as UnaCansCommand.RequestAttributes
        assertEquals(1, parsed.requested.size)
    }

    @Test
    fun parseCommand_rejectsShortAndUnknownFrames() {
        assertNull(UnaCansProtocol.parseCommand(hexToByteArray("0301")))
        assertNull(UnaCansProtocol.parseCommand(hexToByteArray("99" + "6a9bd62d")))
        assertNull(UnaCansProtocol.parseCommand(ByteArray(0)))
    }

    @Test
    fun fragment_splitsOnlyWhenItMustAndLosesNothing() {
        val payload = ByteArray(500) { (it % 251).toByte() }
        val fragments = UnaCansProtocol.fragment(payload, 217)
        assertEquals(listOf(217, 217, 66), fragments.map { it.size })
        assertArrayEquals(payload, fragments.reduce { a, b -> a + b })
        assertEquals(1, UnaCansProtocol.fragment(ByteArray(10), 217).size)
    }
}
