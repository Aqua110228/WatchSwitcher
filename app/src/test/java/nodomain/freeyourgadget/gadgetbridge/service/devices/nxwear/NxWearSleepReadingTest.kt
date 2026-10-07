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

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import org.junit.Assert.*
import org.junit.Test

class NxWearSleepReadingTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val first = "0200010f014093c16a02909dc16a038ca1c16a0298a4c16a03f0a6c16a0228abc16a0340b1c16a0258b7c16a03ecb9c16a028cbfc16a0398c2c16a0284c7c16a03dcc9c16a0534ccc16a0400d3c16a"
    private val second = "010001090100d3c16a0300d3c16a0558d5c16a0328d8c16a0580dac16a03d8dcc16a0530dfc16a03e0e3c16a0448e5c16a000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun reconstructsCapturedSleepAcrossSegmentsAndIgnoresPadding() {
        val readings = listOf(first, second).flatMapIndexed { index, payload ->
            val raw = NxWearPacket.frame(index + 3, 0x31, NxWearDataType.SLEEP, bytes(payload))
            var packet = NxWearPacket.parse(raw.copyOfRange(0, 20))
            assertNull(NxWearSleepReading.parse(packet))
            for (offset in 20 until raw.size step 20) {
                packet = requireNotNull(packet.append(NxWearPacket.parse(raw.copyOfRange(offset, offset + 20))))
            }
            assertTrue(NxWearProtocol.isHistoryDataType(requireNotNull(packet.dataType)))
            requireNotNull(NxWearSleepReading.parse(packet))
        }
        assertEquals(0x6ac19340L * 1000L, readings.first().timestampMillis)
        assertEquals(0x6ac1e548L * 1000L, readings.last().let { it.timestampMillis + it.durationMinutes * 60_000L })
        assertEquals(350, readings.sumOf { it.durationMinutes })
        assertEquals(87, readings.filter { it.kind == ActivityKind.DEEP_SLEEP }.sumOf { it.durationMinutes })
        assertEquals(192, readings.filter { it.kind == ActivityKind.LIGHT_SLEEP }.sumOf { it.durationMinutes })
        assertEquals(71, readings.filter { it.kind == ActivityKind.REM_SLEEP }.sumOf { it.durationMinutes })
    }

    @Test
    fun reconstructsEverySleepBlockInOnePacket() {
        val payload = bytes(first).also { it[2] = 2 } + bytes(second).copyOfRange(3, 79)
        val packet = NxWearPacket.parse(NxWearPacket.frame(3, 0x31, NxWearDataType.SLEEP, payload))

        val readings = requireNotNull(NxWearSleepReading.parse(packet))

        assertEquals(350, readings.sumOf { it.durationMinutes })
        assertEquals(0x6ac1e548L * 1000L, readings.last().let { it.timestampMillis + it.durationMinutes * 60_000L })
    }

    @Test
    fun preservesWakeGapAndResumedSleepInLaterBlock() {
        fun block(vararg records: Pair<Int, Int>): ByteArray {
            val result = ByteArray(76)
            result[0] = records.size.toByte()
            records.forEachIndexed { index, (type, seconds) ->
                val offset = 1 + index * 5
                result[offset] = type.toByte()
                for (byteIndex in 0 until 4) result[offset + 1 + byteIndex] = (seconds shr (byteIndex * 8)).toByte()
            }
            return result
        }
        // Synthetic two-block layout: wake at 06:00, resume at 06:10, end at 08:00.
        val payload = byteArrayOf(1, 0, 2) +
            block(1 to 0, 2 to 60, 4 to 21_600) +
            block(1 to 22_200, 3 to 22_200, 4 to 28_800)
        val packet = NxWearPacket.parse(NxWearPacket.frame(3, 0x31, NxWearDataType.SLEEP, payload))

        val readings = requireNotNull(NxWearSleepReading.parse(packet))

        assertEquals(NxWearSleepReading(21_600_000L, 10, ActivityKind.AWAKE_SLEEP), readings[2])
        assertEquals(NxWearSleepReading(22_200_000L, 110, ActivityKind.LIGHT_SLEEP), readings.last())
    }

    @Test
    fun rejectsMissingSleepBlockAndInvalidLaterBlockCount() {
        val missingBlock = bytes(first).also { it[2] = 2 }
        val invalidCount = bytes(first).also { it[2] = 2 } + bytes(second).copyOfRange(3, 79).also { it[0] = 16 }
        for (payload in listOf(missingBlock, invalidCount, bytes(first).also { it[2] = 0 })) {
            val packet = NxWearPacket.parse(NxWearPacket.frame(3, 0x31, NxWearDataType.SLEEP, payload))
            assertNull(NxWearSleepReading.parse(packet))
        }
    }

    @Test
    fun rejectsTruncatedCountUnknownStateAndBackwardsTime() {
        for (payload in listOf(
            bytes(first).also { it[3] = 16 },
            bytes(first).also { it[9] = 6 },
            bytes(first).also { it[10] = 0; it[11] = 0; it[12] = 0; it[13] = 0 },
        )) {
            assertNull(NxWearSleepReading.parse(NxWearPacket.parse(NxWearPacket.frame(3, 0x31, NxWearDataType.SLEEP, payload))))
        }
    }

    @Test
    fun mapsInteriorWakeIntervalsButDoesNotEmitTerminalWakeMarker() {
        fun record(type: Int, timestampSeconds: Int) = byteArrayOf(
            type.toByte(),
            timestampSeconds.toByte(),
            (timestampSeconds shr 8).toByte(),
            (timestampSeconds shr 16).toByte(),
            (timestampSeconds shr 24).toByte(),
        )
        val payload = listOf(
            record(1, 0), record(2, 60), record(4, 120), record(3, 180), record(4, 240),
        ).fold(byteArrayOf(0, 0, 1, 5)) { result, item -> result + item }
        val packet = NxWearPacket.parse(NxWearPacket.frame(3, 0x31, NxWearDataType.SLEEP, payload))

        val readings = requireNotNull(NxWearSleepReading.parse(packet))

        assertEquals(1, readings.count { it.kind == ActivityKind.AWAKE_SLEEP })
        assertEquals(1, readings.count { it.kind == ActivityKind.DEEP_SLEEP })
        assertEquals(2, readings.count { it.kind == ActivityKind.LIGHT_SLEEP })
    }

    @Test
    fun readsCapturedAutomaticHeartRateAndLatestReading() {
        val payload = bytes("110011fb88c16a4f0390c16a5c0a97c16a47139ec16a471aa5c16a4d23acc16a452bb3c16a4133bac16a4e3bc1c16a5b43c8c16a3d4bcfc16a6153d6c16a395bddc16a4a63e4c16a4d6bebc16a4673f2c16a467bf9c16a49")
        val packet = NxWearPacket.parse(NxWearPacket.frame(5, 0x31, NxWearDataType.HISTORY_HEART_RATE, payload))
        val readings = requireNotNull(NxWearHeartRateReading.parse(packet))
        assertEquals(17, readings.size)
        assertEquals(57, readings.minOf { it.bpm })
        assertEquals(97, readings.maxOf { it.bpm })
        val latest = NxWearPacket.parse(NxWearPacket.frame(6, 0x31, NxWearDataType.HISTORY_HEART_RATE, bytes("010001a800c26a4c")))
        assertEquals(NxWearHeartRateReading(0x6ac200a8L * 1000L, 76), requireNotNull(NxWearHeartRateReading.parse(latest)).single())
    }
}
