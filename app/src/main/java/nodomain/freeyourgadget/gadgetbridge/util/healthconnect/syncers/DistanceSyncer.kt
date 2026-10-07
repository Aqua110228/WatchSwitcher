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
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

internal object DistanceSyncer : AbstractActivitySampleSyncer<DistanceRecord>() {
    override val logger: Logger = LoggerFactory.getLogger(DistanceSyncer::class.java)
    override val recordClass: KClass<DistanceRecord> = DistanceRecord::class

    internal const val RECORD_TYPE = "distance"

    /**
     * Drops the minutes covered by a recorded workout with a summary distance. HC does not add up
     * overlapping records from one app, so per-minute records there would compete with the
     * workout's session-wide DistanceRecord instead of adding to it; see
     * [RecordedWorkoutSyncer.summaryDistanceMeters].
     */
    internal fun excludeWorkoutWindows(samples: List<ActivitySample>, windows: List<WorkoutWindow>): List<ActivitySample> {
        if (windows.isEmpty()) {
            return samples
        }
        return samples.filter { sample ->
            val endTs = Instant.ofEpochSecond(minuteBucketEnd(sample.timestamp.toLong()))
            windows.none { it.overlapsMinuteEndingAt(endTs) }
        }
    }

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
        // Workouts are synced only with the ExerciseSession permission; without it these
        // minutes keep their per-minute records.
        val workoutsSynced = gbDevice.deviceCoordinator.supportsRecordedActivities(gbDevice) &&
            HealthPermission.getWritePermission(ExerciseSessionRecord::class) in grantedPermissions
        val samples = if (workoutsSynced && deviceSamples.isNotEmpty()) {
            val from = Instant.ofEpochSecond(deviceSamples.minOf { it.timestamp }.toLong() - 60)
            val to = Instant.ofEpochSecond(deviceSamples.maxOf { it.timestamp }.toLong())
            excludeWorkoutWindows(deviceSamples, RecordedWorkoutSyncer.queryWorkoutWindows(gbDevice, from, to))
        } else {
            deviceSamples
        }
        return super.sync(healthConnectClient, gbDevice, metadata, offset, sliceStartBoundary, sliceEndBoundary, grantedPermissions, samples)
    }

    override fun convertMinute(
        endTs: Instant,
        minuteSamples: List<ActivitySample>,
        offset: ZoneOffset,
        metadata: Metadata,
        deviceName: String,
        version: Long
    ): DistanceRecord? {
        // Sum the minute's samples; NOT_MEASURED (-1) and 0 both mean "no distance" and contribute nothing.
        val distanceCm = minuteSamples.sumOf { if (it.distanceCm > 0) it.distanceCm else 0 }
        if (distanceCm <= 0) {
            return null
        }
        // HC's DistanceRecord caps distance at 1_000_000 m (= 1e8 cm).
        if (distanceCm > 100_000_000) {
            logger.skipOutOfRange(deviceName, "Distance", "$distanceCm cm", "<= 1000000 m per record")
            return null
        }

        val startTs = endTs.minus(1, ChronoUnit.MINUTES)

        return DistanceRecord(
            startTime = startTs,
            startZoneOffset = offset,
            endTime = endTs,
            endZoneOffset = offset,
            distance = Length.meters(distanceCm / 100.0),
            metadata = clientRecordMetadata(metadata, RECORD_TYPE,endTs.epochSecond, version)
        )
    }
}
