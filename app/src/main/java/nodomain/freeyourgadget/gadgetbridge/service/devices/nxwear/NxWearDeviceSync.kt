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

internal data class NxWearDeviceSync(
    val firmwareVersion: String?,
    val batteryLevel: Int?,
    val batteryStatus: Int?,
    val heartRateEnabled: Boolean?,
    val heartRateIntervalMinutes: Int?,
    val spo2Enabled: Boolean?,
    val wristWakeEnabled: Boolean?,
    val wristWakeStart: String?,
    val wristWakeEnd: String?,
    val wristWakeDurationSeconds: Int?,
) {
    companion object {
        fun parse(payload: ByteArray): NxWearDeviceSync? {
            if (payload.size < 3 || payload.le16(0) != payload.size - 2) return null
            var offset = 3
            var firmwareVersion: String? = null
            var batteryLevel: Int? = null
            var batteryStatus: Int? = null
            var heartRateEnabled: Boolean? = null
            var heartRateIntervalMinutes: Int? = null
            var spo2Enabled: Boolean? = null
            var wristWakeEnabled: Boolean? = null
            var wristWakeStart: String? = null
            var wristWakeEnd: String? = null
            var wristWakeDurationSeconds: Int? = null
            repeat(payload[2].toInt() and 0xff) {
                if (offset + 3 > payload.size) return null
                val itemLength = payload.le16(offset)
                if (itemLength < 3 || offset + itemLength > payload.size) return null
                val type = payload[offset + 2].toInt() and 0xff
                val data = payload.copyOfRange(offset + 3, offset + itemLength)
                when (type) {
                    0x02 -> if (data.size >= 6 && ((data[0].toInt() and 0xff) != 0x0c || data.size >= 7)) {
                        val customerHigh = if ((data[0].toInt() and 0xff) == 0x0c) {
                            (data[6].toInt() and 0xff) shl 8
                        } else 0
                        val customer = (data[1].toInt() and 0xff) + customerHigh
                        firmwareVersion = listOf(customer, data[2].toInt() and 0xff,
                            data[3].toInt() and 0xff, data[4].toInt() and 0xff,
                            data[5].toInt() and 0xff).joinToString(".")
                    }
                    0x03 -> if (data.size >= 2 && (data[0].toInt() and 0xff) <= 100) {
                        batteryLevel = data[0].toInt() and 0xff
                        batteryStatus = data[1].toInt() and 0xff
                    }
                    0x80 -> if (data.size >= 8) {
                        heartRateEnabled = when (data[0].toInt() and 0xff) { 0 -> false; 1 -> true; else -> null }
                        heartRateIntervalMinutes = (data[2].toInt() and 0xff).takeIf { it == 30 || it == 60 }
                        spo2Enabled = when (data[3].toInt() and 0xff) { 0 -> false; 1 -> true; else -> null }
                    }
                    0x7f -> if (data.size >= 6) {
                        wristWakeEnabled = when (data[0].toInt() and 0xff) { 0 -> false; 1 -> true; else -> null }
                        val startHour = data[1].toInt() and 0xff
                        val startMinute = data[2].toInt() and 0xff
                        val endHour = data[3].toInt() and 0xff
                        val endMinute = data[4].toInt() and 0xff
                        if (startHour < 24 && startMinute < 60 && endHour < 24 && endMinute < 60) {
                            wristWakeStart = "%02d:%02d".format(startHour, startMinute)
                            wristWakeEnd = "%02d:%02d".format(endHour, endMinute)
                        }
                        wristWakeDurationSeconds = (data[5].toInt() and 0xff).takeIf { it in 3..10 }
                    }
                }
                offset += itemLength
            }
            if (offset != payload.size) return null
            return NxWearDeviceSync(firmwareVersion, batteryLevel, batteryStatus, heartRateEnabled,
                heartRateIntervalMinutes, spo2Enabled, wristWakeEnabled, wristWakeStart,
                wristWakeEnd, wristWakeDurationSeconds)
        }
    }
}
