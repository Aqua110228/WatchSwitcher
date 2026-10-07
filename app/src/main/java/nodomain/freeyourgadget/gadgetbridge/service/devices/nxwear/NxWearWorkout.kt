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
import java.util.Calendar
import java.util.TimeZone

/** Local identity for a workout explicitly started in Gadgetbridge, not inferred from watch data. */
internal data class NxWearWorkout(
    val sportType: Int,
    val startMillis: Long,
    val endMillis: Long? = null,
    val timezoneId: String = TimeZone.getDefault().id,
) {
    val activityKind: ActivityKind get() = NxWearSportSession.SPORTS.first { it.type == sportType }.activityKind
    val isRunning: Boolean get() = endMillis == null
    val durationSeconds: Long? get() = endMillis?.let { (it - startMillis) / 1000L }

    fun stop(nowMillis: Long): NxWearWorkout = if (isRunning) copy(endMillis = maxOf(startMillis, nowMillis)) else this

    fun encode(): ByteArray = "$PREFIX$sportType|$startMillis|${endMillis ?: ""}|$timezoneId".toByteArray(Charsets.UTF_8)

    data class Totals(val steps: Long, val distanceMeters: Long, val milliCalories: Long)

    /** Only measured intervals wholly inside this session count; never assign whole-day totals. */
    fun totals(readings: List<NxWearSportReading>, timezone: TimeZone = TimeZone.getTimeZone(timezoneId)): Totals? {
        val end = endMillis ?: return null
        val records = readings.filter { it.timestampMillis in startMillis..end }
            .distinctBy { it.timestampMillis }.sortedBy { it.timestampMillis }
        fun day(timestamp: Long): Pair<Int, Int> = Calendar.getInstance(timezone).run {
            timeInMillis = timestamp
            get(Calendar.YEAR) to get(Calendar.DAY_OF_YEAR)
        }
        var steps = 0L
        var distance = 0L
        var calories = 0L
        var intervals = 0
        for ((previous, current) in records.zipWithNext()) {
            if (day(previous.timestampMillis) != day(current.timestampMillis) ||
                current.steps < previous.steps || current.distance < previous.distance ||
                current.calories < previous.calories) continue
            steps += current.steps.toLong() - previous.steps
            distance += current.distance.toLong() - previous.distance
            calories += current.calories.toLong() - previous.calories
            intervals++
        }
        return if (intervals == 0) null else Totals(steps, distance, calories)
    }

    companion object {
        private const val PREFIX = "NX_WEAR_B8_WORKOUT_V1|"

        fun startIfIdle(previous: NxWearWorkout?, sportType: Int, nowMillis: Long): NxWearWorkout? {
            if (previous?.isRunning == true || previous?.endMillis?.let { nowMillis <= it } == true) return null
            return start(sportType, nowMillis)
        }

        fun start(sportType: Int, nowMillis: Long): NxWearWorkout {
            require(NxWearSportSession.SPORTS.any { it.type == sportType })
            require(nowMillis > 0)
            return NxWearWorkout(sportType, nowMillis)
        }

        fun decode(raw: ByteArray?): NxWearWorkout? {
            val value = raw?.toString(Charsets.UTF_8) ?: return null
            if (!value.startsWith(PREFIX)) return null
            val fields = value.removePrefix(PREFIX).split('|')
            if (fields.size != 4) return null
            val type = fields[0].toIntOrNull() ?: return null
            val start = fields[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val end = if (fields[2].isEmpty()) null else {
                fields[2].toLongOrNull()?.takeIf { it >= start } ?: return null
            }
            if (NxWearSportSession.SPORTS.none { it.type == type }) return null
            if (TimeZone.getTimeZone(fields[3]).id != fields[3]) return null
            return NxWearWorkout(type, start, end, fields[3])
        }
    }
}
