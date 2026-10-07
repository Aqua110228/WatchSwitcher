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

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.NxWearSportSampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import java.util.Date

/** The existing summary row is also the durable session journal; there is no separate pending queue. */
internal class NxWearWorkoutStore(private val device: GBDevice) {
    fun active(): NxWearWorkout? = GBApplication.acquireDbReadOnly().use { db ->
        workouts(db.daoSession).firstOrNull { it.second.isRunning }?.second
    }

    fun start(sportType: Int, nowMillis: Long): NxWearWorkout? = GBApplication.acquireDB().use { db ->
        val session = db.daoSession
        val existing = workouts(session)
        if (existing.any { it.second.isRunning }) return null
        val workout = NxWearWorkout.startIfIdle(existing.lastOrNull()?.second, sportType, nowMillis) ?: return null
        val gbDevice = device
        val summary = BaseActivitySummary().apply {
            deviceId = requireNotNull(DBHelper.getDevice(gbDevice, session).id)
            userId = requireNotNull(DBHelper.getUser(session).id)
            startTime = Date(workout.startMillis)
            endTime = startTime
            activityKind = workout.activityKind.code
            rawSummaryData = workout.encode()
            summaryData = ActivitySummaryData().toJson()
        }
        session.baseActivitySummaryDao.insert(summary)
        workout
    }

    fun stop(nowMillis: Long): NxWearWorkout? = GBApplication.acquireDB().use { db ->
        val session = db.daoSession
        val (summary, active) = workouts(session).firstOrNull { it.second.isRunning } ?: return null
        val stopped = active.stop(nowMillis)
        summary.endTime = Date(requireNotNull(stopped.endMillis))
        summary.rawSummaryData = stopped.encode()
        summary.summaryData = ActivitySummaryData().apply {
            add(ActivitySummaryEntries.ACTIVE_SECONDS, stopped.durationSeconds, ActivitySummaryEntries.UNIT_SECONDS, true)
        }.toJson()
        session.baseActivitySummaryDao.update(summary)
        stopped
    }

    /** Recompute instead of incrementing, so retransmissions, retries and later syncs are idempotent. */
    fun enrich(timestampFrom: Long = 0, timestampTo: Long = Long.MAX_VALUE) = GBApplication.acquireDB().use { db ->
        val session = db.daoSession
        for ((summary, workout) in workouts(session, timestampFrom, timestampTo)) {
            val end = workout.endMillis ?: continue
            val records = session.nxWearSportSampleDao.queryBuilder().where(
                NxWearSportSampleDao.Properties.DeviceId.eq(summary.deviceId),
                NxWearSportSampleDao.Properties.Timestamp.ge(workout.startMillis),
                NxWearSportSampleDao.Properties.Timestamp.le(end),
            ).orderAsc(NxWearSportSampleDao.Properties.Timestamp).list().map {
                NxWearSportReading(it.timestamp, it.steps, it.distance, it.calories, it.duration)
            }
            val data = ActivitySummaryData()
            data.add(ActivitySummaryEntries.ACTIVE_SECONDS, workout.durationSeconds, ActivitySummaryEntries.UNIT_SECONDS, true)
            workout.totals(records)?.let {
                data.add(ActivitySummaryEntries.STEPS, it.steps, ActivitySummaryEntries.UNIT_STEPS, true)
                data.add(ActivitySummaryEntries.DISTANCE_METERS, it.distanceMeters, ActivitySummaryEntries.UNIT_METERS, true)
                data.add(ActivitySummaryEntries.CALORIES_BURNT, it.milliCalories / 1000.0, ActivitySummaryEntries.UNIT_KCAL, true)
            }
            val heartRates = GenericHeartRateSampleProvider(device, session)
                .getAllSamples(workout.startMillis, end).map { it.heartRate }.filter { it > 0 }
            if (heartRates.isNotEmpty()) {
                data.add(ActivitySummaryEntries.HR_AVG, heartRates.average(), ActivitySummaryEntries.UNIT_BPM)
                data.add(ActivitySummaryEntries.HR_MIN, heartRates.min(), ActivitySummaryEntries.UNIT_BPM)
                data.add(ActivitySummaryEntries.HR_MAX, heartRates.max(), ActivitySummaryEntries.UNIT_BPM)
            }
            summary.summaryData = data.toJson()
            session.baseActivitySummaryDao.update(summary)
        }
    }

    private fun workouts(session: DaoSession, timestampFrom: Long = 0, timestampTo: Long = Long.MAX_VALUE): List<Pair<BaseActivitySummary, NxWearWorkout>> {
        val deviceId = DBHelper.findDevice(device, session)?.id ?: return emptyList()
        return session.baseActivitySummaryDao.queryBuilder().where(
            BaseActivitySummaryDao.Properties.DeviceId.eq(deviceId),
            BaseActivitySummaryDao.Properties.UserId.eq(DBHelper.getUser(session).id),
            BaseActivitySummaryDao.Properties.EndTime.ge(Date(timestampFrom)),
            BaseActivitySummaryDao.Properties.StartTime.le(Date(timestampTo)),
        ).orderAsc(BaseActivitySummaryDao.Properties.StartTime).list().mapNotNull { summary ->
            NxWearWorkout.decode(summary.rawSummaryData)?.let { summary to it }
        }
    }
}
