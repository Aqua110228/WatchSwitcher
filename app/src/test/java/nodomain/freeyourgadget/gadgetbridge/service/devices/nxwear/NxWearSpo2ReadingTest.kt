/*  Copyright (C) 2026 David Girón

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.nxwear

import org.junit.Assert.*
import org.junit.Test

class NxWearSpo2ReadingTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val frames = listOf(
        "000b05063128000058001100111b89c16a620990",
        "01c16a632b97c16a62189ec16a603ba5c16a6028",
        "02acc16a634bb3c16a6338bac16a615bc1c16a63",
        "0348c8c16a626bcfc16a6358d6c16a607bddc16a",
        "046068e4c16a628bebc16a6078f2c16a6080f9c1",
        "056a62000000074c0000000001200413fb78c16a",
    )

    @Test
    fun reassemblesCapturedAutomaticSpo2AndPreservesHistoryAck() {
        var packet = NxWearPacket.parse(bytes(frames.first()))
        assertNull(NxWearSpo2Reading.parse(packet))
        for (frame in frames.drop(1)) packet = requireNotNull(packet.append(NxWearPacket.parse(bytes(frame))))
        val readings = requireNotNull(NxWearSpo2Reading.parse(packet))
        assertEquals(17, readings.size)
        assertEquals(96, readings.minOf { it.value })
        assertEquals(99, readings.maxOf { it.value })
        assertEquals(NxWearPacket.TimedReading(0x6ac1891bL * 1000L, 98), readings.first())
        assertEquals(NxWearPacket.TimedReading(0x6ac1f980L * 1000L, 98), readings.last())
        assertTrue(NxWearProtocol.isHistoryDataType(requireNotNull(packet.dataType)))
        assertArrayEquals(bytes("000b000604280000010001000000000000000000"),
            NxWearPacket.ack(requireNotNull(packet.sequence), requireNotNull(packet.dataType)))
    }

    @Test
    fun readsCapturedLatestSpo2AndKeepsManualSubtype() {
        val packet = NxWearPacket.parse(bytes("000b002d312800000800010001ad00c26a600001"))
        assertEquals(listOf(NxWearPacket.TimedReading(0x6ac200adL * 1000L, 96)), NxWearSpo2Reading.parse(packet))
        assertArrayEquals(bytes("000b002d04280000010001000000000000000000"),
            NxWearPacket.ack(requireNotNull(packet.sequence), requireNotNull(packet.dataType)))
        val manual = NxWearPacket.parse(NxWearPacket.frame(0, 0x31, NxWearDataType.SPO2, packet.payload))
        assertEquals(NxWearSpo2Reading.parse(packet), NxWearSpo2Reading.parse(manual))
        assertTrue(NxWearProtocol.isHistoryDataType(NxWearDataType.SPO2))
    }

    @Test
    fun rejectsMalformedHistoryAndUnrelatedTypes() {
        val payload = bytes("010001ad00c26a60")
        for (malformed in listOf(payload.copyOf(7), payload + byteArrayOf(0),
            payload.copyOf().also { it[0] = 2 }, payload.copyOf().also { it[2] = 2 })) {
            assertNull(NxWearSpo2Reading.parse(NxWearPacket.parse(
                NxWearPacket.frame(0, 0x31, NxWearDataType.HISTORY_SPO2, malformed))))
        }
        assertNull(NxWearSpo2Reading.parse(NxWearPacket.parse(
            NxWearPacket.frame(0, 0x31, NxWearDataType.HISTORY_HRV, payload))))
    }
}
