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
package nodomain.freeyourgadget.gadgetbridge.service.devices.roidmi

import kotlin.math.roundToInt

/** Decoding supported by the F8 captures; see RoidmiF8Telemetry.md for remaining unknowns. */
internal object RoidmiF8Telemetry {
    enum class CleaningMode { STANDARD, HIGH }

    data class CleaningCounters(
        val mode: CleaningMode,
        val filterUsedMinutes: Long,
        val cleaningMinutes: Int,
    )

    /** Never combine a fresh mode reading with a cached reading from a previous connection. */
    class CleaningSession {
        var standard: CleaningCounters? = null
            private set
        var high: CleaningCounters? = null
            private set

        val cumulativeMinutes: Int?
            get() = standard?.let { normal -> high?.let { normal.cleaningMinutes + it.cleaningMinutes } }
        val filterUsedMinutes: Long?
            get() = standard?.let { normal -> high?.let { normal.filterUsedMinutes + it.filterUsedMinutes } }

        fun update(value: ByteArray): CleaningCounters? {
            val counters = RoidmiF8Telemetry.cleaningCounters(value) ?: return null
            when (counters.mode) {
                CleaningMode.STANDARD -> standard = counters
                CleaningMode.HIGH -> high = counters
            }
            return counters
        }

        fun reset() {
            standard = null
            high = null
        }
    }

    /** Nine-byte telemetry frames exclude the opcode from their additive checksum. */
    fun isValidFrame(value: ByteArray, opcode: Int): Boolean {
        if (value.size != 9 || (value[0].toInt() and 0xff) != opcode) return false
        var checksum = 0
        for (i in 1..7) checksum += value[i].toInt() and 0xff
        return (checksum and 0xff) == (value[8].toInt() and 0xff)
    }

    fun cleaningCounters(value: ByteArray): CleaningCounters? {
        val mode = when {
            isValidFrame(value, 0x11) -> CleaningMode.STANDARD
            isValidFrame(value, 0x17) -> CleaningMode.HIGH
            else -> return null
        }
        // Keep the full fields, not just the changing low bytes in the captures.
        val filterUsed = (1..4).fold(0L) { result, i ->
            (result shl 8) or (value[i].toLong() and 0xff)
        }
        val cleaning = (5..7).fold(0) { result, i ->
            (result shl 8) or (value[i].toInt() and 0xff)
        }
        return CleaningCounters(mode, filterUsed, cleaning)
    }

    fun gear(value: ByteArray): Int? {
        if (!isValidFrame(value, 0x41)) return null
        return (value[2].toInt() and 0xff).takeIf { it <= 2 }
    }

    /** FFD3 raw sensor value before applying the empirically calibrated Celsius conversion. */
    fun d3SensorValue(value: ByteArray): Int? {
        val offset = when {
            isValidFrame(value, 0x31) -> 4
            isValidFrame(value, 0x32) -> 6
            else -> return null
        }
        return ((value[offset].toInt() and 0xff) shl 8) or
            (value[offset + 1].toInt() and 0xff)
    }

    /** Calibrated against the supplied 27.4 °C and approximately 27.6–30 °C captures. */
    fun batteryTemperature(value: ByteArray): Float? {
        val raw = d3SensorValue(value) ?: return null
        // Do not turn empty/all-ones fields into a temperature reading.
        if (raw == 0 || raw == 0xffff) return null
        return (raw - 300) / 10f
    }

    fun currentAmps(value: ByteArray): Float? {
        if (!isValidFrame(value, 0x81) || (value[1].toInt() and 0xff) !in 0..2) return null
        val raw = ((value[4].toInt() and 0xff) shl 8) or (value[5].toInt() and 0xff)
        return raw / 100f
    }

    fun batteryLevel(voltage: Float, running: Boolean, previousLevel: Int, chargingFull: Boolean): Int = when {
        chargingFull -> 100
        // The observed empty endpoint must not be hidden by a cached resting percentage.
        voltage <= BATTERY_EMPTY_VOLTAGE -> 0
        running && previousLevel >= 0 -> previousLevel
        else -> estimateBatteryLevel(voltage)
    }

    fun estimateBatteryLevel(voltage: Float): Int {
        if (voltage <= BATTERY_SOC_CURVE.first().first) return 0
        if (voltage >= BATTERY_SOC_CURVE.last().first) return 100
        for (i in 1 until BATTERY_SOC_CURVE.size) {
            val (v1, p1) = BATTERY_SOC_CURVE[i]
            if (voltage < v1) {
                val (v0, p0) = BATTERY_SOC_CURVE[i - 1]
                return (p0 + (voltage - v0) / (v1 - v0) * (p1 - p0)).roundToInt()
            }
        }
        return 100
    }

    private const val BATTERY_EMPTY_VOLTAGE = 28.58f

    /** Empirical pack-voltage estimate, not a reported state-of-charge measurement. */
    private val BATTERY_SOC_CURVE = arrayOf(
        BATTERY_EMPTY_VOLTAGE to 0f,
        29.08f to 30f,
        29.60f to 40f,
        30.00f to 45f,
        30.40f to 48f,
        30.80f to 58f,
        31.20f to 62f,
        31.60f to 72f,
        32.00f to 82f,
        32.40f to 92f,
        32.80f to 100f,
    )

    fun packVoltage(value: ByteArray): Float? {
        val offset = when {
            isValidFrame(value, 0x21) -> 4
            isValidFrame(value, 0x22) -> 6
            else -> return null
        }
        val raw = ((value[offset].toInt() and 0xff) shl 8) or
            (value[offset + 1].toInt() and 0xff)
        return (raw / 100f).takeIf { raw != 0 }
    }
}
