package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

class WorkoutDistanceTest {

    private val baseTs = 1_700_000_040L
    private val workoutStart = Instant.ofEpochSecond(baseTs)
    private val workoutEnd = Instant.ofEpochSecond(baseTs + 30 * 60)
    private val window = WorkoutWindow(workoutStart, workoutEnd)

    private fun sample(endTs: Long, distanceCm: Int): ActivitySample =
        object : ActivitySample {
            override fun getTimestamp(): Int = endTs.toInt()
            override fun getProvider(): nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider<*>? = null
            override fun getRawKind(): Int = ActivityKind.UNKNOWN.code
            override fun getKind(): ActivityKind = ActivityKind.UNKNOWN
            override fun getRawIntensity(): Int = 0
            override fun getIntensity(): Float = 0f
            override fun getSteps(): Int = 0
            override fun getDistanceCm(): Int = distanceCm
            override fun getActiveCalories(): Int = 0
            override fun getHeartRate(): Int = 0
            override fun setHeartRate(value: Int) {}
        }

    /** One sample per minute of the workout, each carrying [cmPerMinute]. */
    private fun workoutMinutes(cmPerMinute: Int): List<ActivitySample> =
        (1..30).map { sample(baseTs + it * 60L, cmPerMinute) }

    private fun summaryWithDistance(meters: Number): ActivitySummaryData =
        ActivitySummaryData().apply {
            add(ActivitySummaryEntries.DISTANCE_METERS, meters, ActivitySummaryEntries.UNIT_METERS)
        }

    private fun workout(summaryData: ActivitySummaryData?, kind: ActivityKind = ActivityKind.RUNNING): BaseActivitySummary =
        BaseActivitySummary().apply {
            startTime = Date.from(workoutStart)
            endTime = Date.from(workoutEnd)
            activityKind = kind.code
            this.summaryData = summaryData?.toJson()
        }

    @Test
    fun summaryDistanceIsTheWorkoutDistance() {
        // Mi Band 9 Active run (#6875): the summary's GPS distance, not the per-minute stream.
        assertEquals(4040.0, RecordedWorkoutSyncer.summaryDistanceMeters(summaryWithDistance(4040)), 0.001)
    }

    @Test
    fun summaryWithoutDistanceGivesZero() {
        assertEquals(0.0, RecordedWorkoutSyncer.summaryDistanceMeters(ActivitySummaryData()), 0.001)
        assertEquals(0.0, RecordedWorkoutSyncer.summaryDistanceMeters(null), 0.001)
    }

    @Test
    fun distanceWindowsOnlyCoverWorkoutsWithSummaryDistance() {
        val windows = RecordedWorkoutSyncer.distanceWindows(
            listOf(
                workout(summaryWithDistance(1025)),
                workout(ActivitySummaryData()),
                workout(null),
                workout(summaryWithDistance(500), ActivityKind.SLEEP_ANY)
            )
        )
        assertEquals(listOf(window), windows)
    }

    @Test
    fun minutesOfWorkoutWithoutSummaryDistanceAreKept() {
        val samples = workoutMinutes(cmPerMinute = 1000)
        val windows = RecordedWorkoutSyncer.distanceWindows(listOf(workout(ActivitySummaryData())))
        assertEquals(samples, DistanceSyncer.excludeWorkoutWindows(samples, windows))
    }

    @Test
    fun minutesOfWorkoutWithSummaryDistanceAreDropped() {
        // Garmin pool swim (#6815): the summary distance replaces minutes that carry nothing.
        val samples = workoutMinutes(cmPerMinute = 0)
        val windows = RecordedWorkoutSyncer.distanceWindows(listOf(workout(summaryWithDistance(1025))))
        assertEquals(emptyList<ActivitySample>(), DistanceSyncer.excludeWorkoutWindows(samples, windows))
    }

    @Test
    fun excludeWorkoutWindowsDropsOnlyMinutesOverlappingAWorkout() {
        val before = sample(baseTs, 100) // minute ends exactly at workout start
        val inside = sample(baseTs + 60, 100)
        val lastMinute = sample(baseTs + 30 * 60L - 30, 100) // sub-minute sample, bucket ends at workout end
        val afterEnd = sample(baseTs + 30 * 60L + 30, 100) // sub-minute sample, bucket starts at workout end
        val after = sample(baseTs + 31 * 60L, 100) // minute starts exactly at workout end

        val kept = DistanceSyncer.excludeWorkoutWindows(listOf(before, inside, lastMinute, afterEnd, after), listOf(window))

        assertEquals(listOf(before, afterEnd, after), kept)
    }

    @Test
    fun excludeWorkoutWindowsKeepsAllWithoutWorkouts() {
        val samples = workoutMinutes(cmPerMinute = 100)
        assertEquals(samples, DistanceSyncer.excludeWorkoutWindows(samples, emptyList()))
    }

    @Test
    fun distanceClientRecordIdMatchesPerMinuteRecord() {
        val device = Device(type = Device.TYPE_WATCH, manufacturer = "Xiaomi", model = "Band")
        val metadata = Metadata.activelyRecorded(device)
        val endTs = baseTs + 60

        val record = DistanceSyncer.convertMinute(Instant.ofEpochSecond(endTs), listOf(sample(endTs, 100)), ZoneOffset.UTC, metadata, "dev", 1L)!!

        assertEquals(record.metadata.clientRecordId, distanceClientRecordId(device, endTs))
    }

    @Test
    fun subMinuteSampleKeysOnItsMinuteBucket() {
        val device = Device(type = Device.TYPE_WATCH, manufacturer = "Pine64", model = "PineTime")
        val metadata = Metadata.activelyRecorded(device)
        val sampleTs = baseTs + 60 + 13

        val (bucketEnd, minuteSamples) = DistanceSyncer.bucketByMinute(listOf(sample(sampleTs, 100))).single()
        val record = DistanceSyncer.convertMinute(bucketEnd, minuteSamples, ZoneOffset.UTC, metadata, "dev", 1L)!!

        assertEquals(record.metadata.clientRecordId, distanceClientRecordId(device, minuteBucketEnd(sampleTs)))
    }
}
