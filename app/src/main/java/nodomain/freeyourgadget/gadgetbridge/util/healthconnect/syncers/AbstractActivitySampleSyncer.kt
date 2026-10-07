/*  Copyright (C) 2025 Gideon Zenz

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
package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectUtils
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/** End of the per-minute record a sample at [timestamp] (epoch seconds) belongs to; see [AbstractActivitySampleSyncer.bucketByMinute]. */
internal fun minuteBucketEnd(timestamp: Long): Long = ((timestamp + 59) / 60) * 60

internal abstract class AbstractActivitySampleSyncer<TRecord : Record> : ActivitySampleSyncer {
    protected abstract val logger: Logger
    protected abstract val recordClass: KClass<TRecord>

    // Re-emit samples this far before the slice start. The ACTIVITY cursor is shared across
    // steps/calories/distance and advances to the furthest delivered; when step or distance detail
    // trails the calorie summary, those minutes would be clipped and lost. The per-minute
    // clientRecordId makes the re-emitted overlap an upsert. Heart rate is a series keyed on its
    // start time, does not extend this base, and keeps strict boundaries. Must stay below the
    // query look-back (1 day): a larger value would let a bucket straddle the fetch floor and
    // upsert a partial sum over a previously complete minute.
    protected open val lateSampleLookback: Duration = Duration.ofHours(1)

    internal fun isWithinSlice(
        endTs: Instant,
        startTs: Instant,
        sliceStartBoundary: Instant,
        sliceEndBoundary: Instant
    ): Boolean {
        val effectiveStart = sliceStartBoundary.minus(lateSampleLookback)
        return !endTs.isBefore(effectiveStart) && !startTs.isAfter(sliceEndBoundary)
    }

    // Group samples by the minute they fall in: the bucket end is the minute boundary at or
    // after the sample's timestamp, so a sample at 12:00:13 lands in the [12:00, 12:01) record.
    // A sample exactly on a boundary (12:00:00) closes the preceding [11:59, 12:00) record.
    // Per-minute devices stamp the end of the minute, so they map unchanged (ceil(M) == M); a
    // sub-minute device's samples within one minute collapse to a single non-overlapping record.
    internal fun bucketByMinute(samples: List<ActivitySample>): List<Pair<Instant, List<ActivitySample>>> {
        val buckets = LinkedHashMap<Long, MutableList<ActivitySample>>()
        for (sample in samples) {
            val minuteEnd = minuteBucketEnd(sample.timestamp.toLong())
            buckets.getOrPut(minuteEnd) { mutableListOf() }.add(sample)
        }
        return buckets.map { (minuteEnd, list) -> Instant.ofEpochSecond(minuteEnd) to list }
    }

    internal data class BuiltRecords<TRecord : Record>(
        val records: List<TRecord>,
        val latestSampleTs: Instant?,
        val skippedCount: Int
    )

    // One record per minute, built from that minute's summed samples. Samples are grouped before
    // this is called, so a device reporting sub-minute (e.g. per-second) samples yields a single
    // non-overlapping per-minute record. Health Connect does not sum overlapping same-type
    // records; it keeps one, which silently dropped most of a high-resolution device's steps
    // (issue #5735).
    internal fun buildRecords(
        relevantSamples: List<ActivitySample>,
        zoneId: ZoneId,
        metadata: Metadata,
        deviceName: String,
        version: Long,
        sliceStartBoundary: Instant,
        sliceEndBoundary: Instant
    ): BuiltRecords<TRecord> {
        val recordTypeName = recordClass.simpleName ?: "Unknown"
        val records = mutableListOf<TRecord>()
        // The cursor is the latest raw sample timestamp, not the bucket end: the bucket end is
        // ceil'd to the next minute and can exceed sliceEndBoundary, which would advance the
        // shared ACTIVITY cursor past the slice and make strict-boundary syncers (heart rate)
        // drop the samples in between on the next run.
        var latestSampleTs: Instant? = null
        var skipped = 0
        for ((endTs, minuteSamples) in bucketByMinute(relevantSamples)) {
            val startTs = endTs.minus(1, ChronoUnit.MINUTES)
            if (!isWithinSlice(endTs, startTs, sliceStartBoundary, sliceEndBoundary)) {
                logger.trace(
                    "Skipping {} for device '{}' for minute ending at {} (interval {} to {}) as its interval is outside the slice {} - {}.",
                    recordTypeName,
                    deviceName,
                    endTs,
                    startTs,
                    endTs,
                    sliceStartBoundary,
                    sliceEndBoundary
                )
                continue
            }
            val record = convertMinute(endTs, minuteSamples, zoneId.rules.getOffset(endTs), metadata, deviceName, version)
            if (record == null) {
                skipped++
                continue
            }
            records.add(record)
            val sampleTs = Instant.ofEpochSecond(minuteSamples.maxOf { it.timestamp.toLong() })
            if (latestSampleTs == null || sampleTs.isAfter(latestSampleTs)) {
                latestSampleTs = sampleTs
            }
        }
        return BuiltRecords(records, latestSampleTs, skipped)
    }

    // Samples are grouped into one entry per minute before this is called, so a device that
    // reports sub-minute (e.g. per-second) samples yields a single non-overlapping per-minute
    // record.
    internal abstract fun convertMinute(
        endTs: Instant,
        minuteSamples: List<ActivitySample>,
        offset: ZoneOffset,
        metadata: Metadata,
        deviceName: String,
        version: Long
    ): TRecord?

    override suspend fun sync(
        healthConnectClient: HealthConnectClient,
        gbDevice: GBDevice,
        metadata: Metadata,
        offset: ZoneId,
        sliceStartBoundary: Instant,
        sliceEndBoundary: Instant,
        grantedPermissions: Set<String>,
        deviceSamples: List<ActivitySample>
    ): SyncerStatistics {
        val deviceName = gbDevice.aliasOrName
        val recordTypeName = recordClass.simpleName ?: "Unknown"

        // 1. Permission Check
        if (HealthPermission.getWritePermission(recordClass) !in grantedPermissions) {
            logger.info("Skipping $recordTypeName sync for device '$deviceName'; $recordTypeName permission not granted.")
            return SyncerStatistics(recordType = recordTypeName)
        }

        // 2. Relevant Input Data Check
        val relevantSamples = deviceSamples.sortedBy { it.timestamp }
        if (relevantSamples.isEmpty()) {
            logger.info("No relevant step samples (>0) for device '$deviceName' in the provided deviceSamples for slice $sliceStartBoundary to $sliceEndBoundary.")
            return SyncerStatistics(recordType = recordTypeName)
        }

        logger.info("Processing ${relevantSamples.size} samples for steps for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.")

        // clientRecordVersion: every record in this slice shares the run's wall-clock so a later
        // sync always outranks the value it previously wrote for a minute. HC keeps the highest
        // version on a clientRecordId collision, so the freshest (most complete) re-read wins even
        // when a value is revised downward. Must not be the metric value itself: a downward
        // correction would then carry a lower version and be silently ignored.
        val recordVersion = System.currentTimeMillis()

        val built = buildRecords(
            relevantSamples, offset, metadata, deviceName, recordVersion, sliceStartBoundary, sliceEndBoundary
        )
        val recordsToInsert = built.records
        val skippedCount = built.skippedCount
        val latestSyncedTimestamp = built.latestSampleTs

        // 3. No Valid Records to Insert
        if (recordsToInsert.isEmpty()) {
            logger.info("No valid $recordTypeName created for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary after processing ${relevantSamples.size} samples.")
            return SyncerStatistics(recordsSkipped = skippedCount, recordType = recordTypeName)
        }

        // 4. Insertion (with chunking)
        logger.info("Attempting to insert ${recordsToInsert.size} $recordTypeName(s) for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.")
        for (chunk in recordsToInsert.chunked(HealthConnectUtils.CHUNK_SIZE)) {
            HealthConnectUtils.insertRecords(chunk, healthConnectClient)
        }

        logger.info("Successfully inserted ${recordsToInsert.size} $recordTypeName(s) for device '$deviceName' for slice $sliceStartBoundary to $sliceEndBoundary.")
        return SyncerStatistics(
            recordsSynced = recordsToInsert.size,
            recordsSkipped = skippedCount,
            recordType = recordTypeName,
            latestRecordTimestamp = latestSyncedTimestamp
        )
    }
}
