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

/** Phone-controlled sport sessions supported by the B8's app-sport command. */
internal object NxWearSportSession {
    data class Sport(val type: Int, val activityKind: ActivityKind)

    val SPORTS = listOf(
        Sport(5, ActivityKind.OUTDOOR_RUNNING), Sport(4, ActivityKind.INDOOR_RUNNING),
        Sport(2, ActivityKind.OUTDOOR_CYCLING), Sport(3, ActivityKind.INDOOR_CYCLING),
        Sport(7, ActivityKind.OUTDOOR_WALKING), Sport(11, ActivityKind.BASKETBALL),
        Sport(14, ActivityKind.SOCCER), Sport(10, ActivityKind.BADMINTON),
        Sport(6, ActivityKind.SWIMMING), Sport(12, ActivityKind.JUMP_ROPING),
        Sport(15, ActivityKind.MOUNTAINEERING), Sport(9, ActivityKind.YOGA),
    )

    const val OUTDOOR_RUNNING = 5
    const val START = 1
    const val STOP = 0

    fun encode(sportType: Int, status: Int, elapsedSeconds: Long = 0, distanceMeters: Long = 0): ByteArray {
        require(SPORTS.any { it.type == sportType }) { "Unsupported NX Wear sport type: $sportType" }
        require(status == START || status == STOP)
        require(elapsedSeconds in 0..0xffff_ffffL)
        require(distanceMeters in 0..0xffff_ffffL)
        val payload = ByteArray(10)
        payload[0] = sportType.toByte()
        payload[1] = status.toByte()
        for (i in 0..3) {
            payload[2 + i] = (elapsedSeconds ushr (i * 8)).toByte()
            payload[6 + i] = (distanceMeters ushr (i * 8)).toByte()
        }
        return NxWearPacket.frame(0, 0x01, 0x82, payload)
    }
}
