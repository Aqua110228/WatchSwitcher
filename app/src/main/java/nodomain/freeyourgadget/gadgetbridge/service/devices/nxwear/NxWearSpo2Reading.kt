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

internal object NxWearSpo2Reading {
    fun parse(packet: NxWearPacket): List<NxWearPacket.TimedReading>? {
        if (packet.dataType !in setOf(NxWearDataType.SPO2, NxWearDataType.HISTORY_SPO2)) return null
        val readings = packet.timedReadings(requireNotNull(packet.dataType)) ?: return null
        if (packet.dataType == NxWearDataType.HISTORY_SPO2 &&
            (packet.payload.le16(0) != readings.size || (packet.payload[2].toInt() and 0xff) != readings.size)) return null
        return readings
    }
}
