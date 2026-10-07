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

/** Sleep transition records from the B8's paged 0x06 history stream. */
internal data class NxWearSleepReading(val timestampMillis: Long, val durationMinutes: Int, val kind: ActivityKind) {
    companion object {
        fun parse(packet: NxWearPacket): List<NxWearSleepReading>? {
            if (!packet.isComplete || packet.command != 0x31 || packet.dataType != NxWearDataType.SLEEP) return null
            val payload = packet.payload
            if (payload.size < 4) return null
            val blockCount = payload[2].toInt() and 0xff
            if (blockCount == 0) return null
            val records = mutableListOf<Pair<Int, Long>>()
            for (block in 0 until blockCount) {
                val countOffset = 3 + block * 76
                if (countOffset >= payload.size) return null
                val count = payload[countOffset].toInt() and 0xff
                if (count > 15 || count > (payload.size - countOffset - 1) / 5) return null
                for (index in 0 until count) {
                    val offset = countOffset + 1 + index * 5
                    records.add((payload[offset].toInt() and 0xff) to payload.le32(offset + 1))
                }
            }
            if (records.size < 2 || records.first().first != 1 || records.last().first != 4 ||
                records.any { it.first !in 1..5 } ||
                records.zipWithNext().any { (start, end) -> end.second < start.second }) return null
            return records.zipWithNext().mapNotNull { (start, end) ->
                val duration = end.second - start.second
                if (duration == 0L) return@mapNotNull null
                if (duration % 60L != 0L || duration / 60L > Int.MAX_VALUE) return null
                val kind = when (start.first) {
                    1, 3 -> ActivityKind.LIGHT_SLEEP
                    2 -> ActivityKind.DEEP_SLEEP
                    4 -> ActivityKind.AWAKE_SLEEP
                    5 -> ActivityKind.REM_SLEEP
                    else -> return null
                }
                NxWearSleepReading(start.second * 1000L, (duration / 60L).toInt(), kind)
            }
        }
    }
}
