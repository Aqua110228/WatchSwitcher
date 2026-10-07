package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

// Guards issue #5735: a sub-minute (per-second) device such as InfiniTime/PineTime emitted one
// 1-minute StepsRecord per sample, so nine samples in a minute produced nine overlapping records
// that Health Connect did not sum (it kept one), dropping most of the steps. Samples must collapse
// to a single non-overlapping per-minute record.
class ActivitySampleMinuteBucketingTest {

    private val offset: ZoneOffset = ZoneOffset.UTC
    private val device = "test-device"
    private val metadata: Metadata = Metadata.autoRecorded(
        Device(type = Device.TYPE_WATCH, manufacturer = "test", model = "test")
    )
    private val version = 1_700_000_500_000L

    // A concrete syncer so the base-class bucketByMinute can be exercised; convertMinute is unused.
    private object ProbeSyncer : AbstractActivitySampleSyncer<StepsRecord>() {
        override val logger: Logger = LoggerFactory.getLogger("test")
        override val recordClass: KClass<StepsRecord> = StepsRecord::class
        override fun convertMinute(
            endTs: Instant,
            minuteSamples: List<ActivitySample>,
            offset: ZoneOffset,
            metadata: Metadata,
            deviceName: String,
            version: Long
        ): StepsRecord? = null
    }

    private fun sample(
        ts: Long,
        steps: Int = 0,
        distanceCm: Int = ActivitySample.NOT_MEASURED,
        calories: Int = ActivitySample.NOT_MEASURED
    ): ActivitySample = object : ActivitySample {
        override fun getProvider() = null
        override fun getRawKind() = 0
        override fun getKind() = ActivityKind.ACTIVITY
        override fun getRawIntensity() = ActivitySample.NOT_MEASURED
        override fun getIntensity() = 0f
        override fun getSteps() = steps
        override fun getDistanceCm() = distanceCm
        override fun getActiveCalories() = calories
        override fun getHeartRate() = ActivitySample.NOT_MEASURED
        override fun setHeartRate(value: Int) {}
        override fun getTimestamp() = ts.toInt()
    }

    // The issue #5735 shape: 9 per-second samples (total 26) inside one minute.
    private val minute = (1_700_000_040L / 60) * 60
    private val perSecondSamples = listOf(
        sample(minute + 13, 10),
        sample(minute + 15, 2),
        sample(minute + 16, 2),
        sample(minute + 18, 2),
        sample(minute + 19, 2),
        sample(minute + 21, 2),
        sample(minute + 22, 2),
        sample(minute + 23, 2),
        sample(minute + 24, 2)
    )

    @Test
    fun perSecondSamples_collapseToOneBucket() {
        val buckets = ProbeSyncer.bucketByMinute(perSecondSamples)
        assertEquals(1, buckets.size)
        assertEquals(9, buckets.first().second.size)
        // Samples at minute+13..minute+24 land in the [minute, minute+60) bucket that contains them.
        assertEquals(Instant.ofEpochSecond(minute + 60), buckets.first().first)
    }

    @Test
    fun bucketByMinute_boundarySampleClosesPrecedingMinute() {
        // A sample exactly on a minute boundary is attributed to the record ending at that
        // boundary, so a per-minute device (which stamps the end of the minute) maps unchanged.
        val buckets = ProbeSyncer.bucketByMinute(listOf(sample(minute, 5)))
        assertEquals(1, buckets.size)
        assertEquals(Instant.ofEpochSecond(minute), buckets.first().first)
    }

    // The issue #5735 guard: driving the real record-building path (not just bucketByMinute or
    // convertMinute in isolation) must yield exactly one record per minute, summed, whose interval
    // contains the samples. Reverting to one-record-per-sample produces nine records and fails here.
    @Test
    fun buildRecords_perSecondSamplesProduceOneRecordPerMinute_withSummedCount() {
        val built = StepsSyncer.buildRecords(
            perSecondSamples,
            ZoneOffset.UTC,
            metadata,
            device,
            version,
            Instant.ofEpochSecond(minute - 3600),
            Instant.ofEpochSecond(minute + 3600)
        )
        val records = built.records
        assertEquals(1, records.size)
        assertEquals(26L, records.first().count)
        assertEquals(Instant.ofEpochSecond(minute), records.first().startTime)
        assertEquals(Instant.ofEpochSecond(minute + 60), records.first().endTime)
        assertEquals(0, built.skippedCount)
        // The cursor is the latest raw sample timestamp, not the ceil'd bucket end (minute+60),
        // so it never advances past the samples it covers.
        assertEquals(Instant.ofEpochSecond(minute + 24), built.latestSampleTs)
    }

    @Test
    fun buildRecords_cursorNeverExceedsSliceEnd() {
        // A sample inside a non-minute-aligned slice end must not push the cursor past the slice:
        // the ceil'd bucket end (minute+60) exceeds sliceEnd, but the raw sample timestamp does not.
        val sliceEnd = Instant.ofEpochSecond(minute + 30)
        val built = StepsSyncer.buildRecords(
            listOf(sample(minute + 25, 5)),
            ZoneOffset.UTC,
            metadata,
            device,
            version,
            Instant.ofEpochSecond(minute - 3600),
            sliceEnd
        )
        assertNotNull(built.latestSampleTs)
        assertFalse(built.latestSampleTs!!.isAfter(sliceEnd))
    }

    @Test
    fun buildRecords_distancePerSecondSamplesProduceOneRecordPerMinute() {
        val built = DistanceSyncer.buildRecords(
            listOf(sample(minute + 13, distanceCm = 100), sample(minute + 20, distanceCm = 50)),
            ZoneOffset.UTC,
            metadata,
            device,
            version,
            Instant.ofEpochSecond(minute - 3600),
            Instant.ofEpochSecond(minute + 3600)
        )
        val records = built.records
        assertEquals(1, records.size)
        assertEquals(androidx.health.connect.client.units.Length.meters(1.5), (records.first() as DistanceRecord).distance)
    }

    @Test
    fun buildRecords_activeCaloriesPerSecondSamplesProduceOneRecordPerMinute() {
        val built = ActiveCaloriesSyncer.buildRecords(
            listOf(sample(minute + 13, calories = 10), sample(minute + 20, calories = 20)),
            ZoneOffset.UTC,
            metadata,
            device,
            version,
            Instant.ofEpochSecond(minute - 3600),
            Instant.ofEpochSecond(minute + 3600)
        )
        val records = built.records
        assertEquals(1, records.size)
        assertEquals(androidx.health.connect.client.units.Energy.calories(30.0), (records.first() as ActiveCaloriesBurnedRecord).energy)
    }

    @Test
    fun convertMinute_sumsPerSecondSamples() {
        val record = StepsSyncer.convertMinute(Instant.ofEpochSecond(minute), perSecondSamples, offset, metadata, device, version)
        assertNotNull(record)
        assertEquals(26L, record!!.count)
    }

    @Test
    fun convertMinute_intervalIsASingleNonOverlappingMinute() {
        val record = StepsSyncer.convertMinute(Instant.ofEpochSecond(minute), perSecondSamples, offset, metadata, device, version)
        assertEquals(Instant.ofEpochSecond(minute - 60), record!!.startTime)
        assertEquals(Instant.ofEpochSecond(minute), record.endTime)
    }

    @Test
    fun convertMinute_notMeasuredDoesNotCorruptSum() {
        val samples = listOf(sample(minute + 5, ActivitySample.NOT_MEASURED), sample(minute + 10, 5))
        val record = StepsSyncer.convertMinute(Instant.ofEpochSecond(minute), samples, offset, metadata, device, version)
        assertEquals(5L, record!!.count)
    }

    @Test
    fun convertMinute_perMinuteSampleUnchanged() {
        // A per-minute device (e.g. Garmin): one sample at the minute boundary maps to itself.
        val samples = listOf(sample(minute, 42))
        val record = StepsSyncer.convertMinute(Instant.ofEpochSecond(minute), samples, offset, metadata, device, version)
        assertEquals(42L, record!!.count)
        assertEquals(Instant.ofEpochSecond(minute - 60), record.startTime)
        assertEquals(Instant.ofEpochSecond(minute), record.endTime)
    }

    @Test
    fun convertMinute_allZeroReturnsNull() {
        val samples = listOf(sample(minute + 5, 0), sample(minute + 10, ActivitySample.NOT_MEASURED))
        assertNull(StepsSyncer.convertMinute(Instant.ofEpochSecond(minute), samples, offset, metadata, device, version))
    }

    @Test
    fun bucketByMinute_keepsAdjacentMinutesSeparate() {
        // One sample in each of two consecutive minutes stays in two buckets.
        val samples = listOf(sample(minute + 30, 3), sample(minute + 90, 7))
        assertEquals(2, ProbeSyncer.bucketByMinute(samples).size)
    }
}
