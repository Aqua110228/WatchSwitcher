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

internal data class NxWearSportReading(
    val timestampMillis: Long,
    val steps: Int,
    val distance: Int, // meters
    val calories: Int, // milli-kcal
    val duration: Int, // seconds
) {
    companion object {
        fun parse(packet: NxWearPacket): List<NxWearSportReading>? {
            if (!packet.isComplete || packet.command != 0x31 || packet.dataType !in setOf(NxWearDataType.REALTIME_SPORT, NxWearDataType.HISTORY_SPORT)) return null
            val payload = packet.payload
            if (payload.size < 3 || payload.le16(0) < (payload[2].toInt() and 0xff) || payload.size != 3 + (payload[2].toInt() and 0xff) * 20) return null
            return (3 until payload.size step 20).mapNotNull { offset ->
                val timestamp = payload.le32(offset)
                val steps = payload.le32(offset + 4)
                val distance = payload.le32(offset + 8)
                val calories = payload.le32(offset + 12)
                val duration = payload.le32(offset + 16)
                if (timestamp == 0L || steps > Int.MAX_VALUE || distance > Int.MAX_VALUE ||
                    calories > Int.MAX_VALUE || duration > Int.MAX_VALUE) null
                else NxWearSportReading(timestamp * 1000L, steps.toInt(), distance.toInt(), calories.toInt(), duration.toInt())
            }
        }
    }
}
