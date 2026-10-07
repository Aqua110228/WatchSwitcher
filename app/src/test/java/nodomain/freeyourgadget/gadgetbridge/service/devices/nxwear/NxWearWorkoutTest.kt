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
import java.util.Calendar
import java.util.TimeZone

class NxWearWorkoutTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun selectedWalkingRunningAndBasketballKindsSurviveRecovery() {
        for ((type, kind) in listOf(7 to ActivityKind.OUTDOOR_WALKING,
                5 to ActivityKind.OUTDOOR_RUNNING, 11 to ActivityKind.BASKETBALL)) {
            val workout = NxWearWorkout.start(type, 1_000_123L)
            val recovered = requireNotNull(NxWearWorkout.decode(workout.encode()))
            assertEquals(workout, recovered)
            assertEquals(kind, recovered.activityKind)
            assertTrue(recovered.isRunning)
            assertNull(recovered.durationSeconds)
            val stopped = recovered.stop(1_030_456L)
            assertEquals(type, stopped.sportType)
            assertEquals(1_000_123L, stopped.startMillis)
            assertEquals(30L, stopped.durationSeconds)
            assertEquals(stopped, NxWearWorkout.decode(stopped.encode()))
        }
    }

    @Test
    fun repeatedStopDoesNotExtendClosedWorkoutAndNextWorkoutIsIndependent() {
        val first = NxWearWorkout.start(7, 1000).stop(2000)
        assertEquals(first, first.stop(5000))
        val second = NxWearWorkout.start(11, 2001).stop(2500)
        assertEquals(ActivityKind.OUTDOOR_WALKING, first.activityKind)
        assertEquals(ActivityKind.BASKETBALL, second.activityKind)
        assertEquals(2000L, first.endMillis)
        assertEquals(2500L, second.endMillis)
    }

    @Test
    fun repeatedStartOrBackwardsClockCannotReplaceOrOverlapThePreviousSession() {
        val active = NxWearWorkout.start(7, 1000)
        val recovered = requireNotNull(NxWearWorkout.decode(active.encode()))
        assertNull(NxWearWorkout.startIfIdle(recovered, 11, 2000))
        val closed = recovered.stop(3000)
        assertNull(NxWearWorkout.startIfIdle(closed, 11, 3000))
        assertNull(NxWearWorkout.startIfIdle(closed, 11, 2000))
        assertEquals(ActivityKind.BASKETBALL,
            requireNotNull(NxWearWorkout.startIfIdle(closed, 11, 3001)).activityKind)
    }

    @Test
    fun clockMovingBackwardsCannotCreateNegativeDuration() {
        val stopped = NxWearWorkout.start(5, 3000).stop(2000)
        assertEquals(3000L, stopped.endMillis)
        assertEquals(0L, stopped.durationSeconds)
    }

    @Test
    fun malformedOrForeignJournalIsNotAdoptedAsALocalWorkout() {
        assertNull(NxWearWorkout.decode(null))
        assertNull(NxWearWorkout.decode(byteArrayOf(1, 2, 3)))
        for (suffix in listOf("99|1000||UTC", "7|-1||UTC", "7|1000|999|UTC",
                "7|1000|bad|UTC", "7|1000||not-a-timezone", "7|1000||UTC|extra")) {
            assertNull(NxWearWorkout.decode("NX_WEAR_B8_WORKOUT_V1|$suffix".toByteArray()))
        }
    }

    @Test
    fun sumsOnlyObservedInWindowIntervalsWithoutDuplicatingDailyCounters() {
        val workout = NxWearWorkout(7, 1000, 5000, "UTC")
        val records = listOf(reading(0, 1000), reading(1000, 1010), reading(2000, 1020),
            reading(3000, 1030), reading(5000, 1040), reading(6000, 1060))
        val expected = NxWearWorkout.Totals(30, 60, 3000)
        assertEquals(expected, workout.totals(records))
        assertEquals(expected, workout.totals(records.reversed() + records))
        assertNull(workout.totals(listOf(records.first(), records.last())))
        assertNull(workout.totals(listOf(records[1])))
        assertNull(NxWearWorkout.start(7, 1000).totals(records))
    }

    @Test
    fun counterResetDoesNotCountTheNewBaselineAsWorkoutActivity() {
        val workout = NxWearWorkout(11, 1000, 4000, "UTC")
        assertEquals(NxWearWorkout.Totals(20, 40, 2000), workout.totals(listOf(
            reading(1000, 100), reading(2000, 110), reading(3000, 5), reading(4000, 15))))
    }

    @Test
    fun savedTimezoneControlsMidnightResetBoundaryAfterRecovery() {
        val zone = TimeZone.getTimeZone("Europe/Madrid")
        val midnight = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.OCTOBER, 4, 0, 0, 0)
        }.timeInMillis
        val workout = NxWearWorkout(7, midnight - 2000, midnight + 1000, zone.id)
        val recovered = requireNotNull(NxWearWorkout.decode(workout.encode()))
        assertEquals(zone.id, recovered.timezoneId)
        // Even increasing counters cannot bridge midnight in the session's original timezone.
        assertEquals(NxWearWorkout.Totals(10, 20, 1000), recovered.totals(listOf(
            reading(midnight - 2000, 10), reading(midnight - 1000, 20), reading(midnight, 30))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedSportCannotStartAWorkout() {
        NxWearWorkout.start(255, 1000)
    }

    private fun reading(timestamp: Long, steps: Int) =
        NxWearSportReading(timestamp, steps, steps * 2, steps * 100, 0)
}
