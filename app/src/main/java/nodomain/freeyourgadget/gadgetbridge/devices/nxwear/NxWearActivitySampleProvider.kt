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
package nodomain.freeyourgadget.gadgetbridge.devices.nxwear

import de.greenrobot.dao.query.WhereCondition.StringCondition
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.generic_hr.GenericHeartRateActivitySampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericActivitySample
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepStageSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepStageSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.NxWearSportSample
import nodomain.freeyourgadget.gadgetbridge.entities.NxWearSportSampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.util.Calendar

class NxWearActivitySampleProvider(private val device: GBDevice, private val session: DaoSession) :
    GenericHeartRateActivitySampleProvider(device, session) {

    override fun getAllActivitySamples(timestampFrom: Int, timestampTo: Int): MutableList<GenericActivitySample> {
        val samples = super.getAllActivitySamples(timestampFrom, timestampTo)
        // Keep lookups by timestamp constant-time as daily sport records are merged with
        // potentially high-resolution heart-rate samples.
        val samplesByTimestamp = samples.associateBy { it.timestamp }.toMutableMap()
        val dbDevice = DBHelper.findDevice(device, session) ?: return samples
        val fromMillis = timestampFrom.toLong() * 1000L
        val toMillis = timestampTo.toLong() * 1000L
        val sportDao = session.nxWearSportSampleDao
        // Cumulative counters need only the immediately preceding record, not the whole day.
        var previous: NxWearSportSample? = sportDao.queryBuilder()
            .where(NxWearSportSampleDao.Properties.DeviceId.eq(dbDevice.id),
                NxWearSportSampleDao.Properties.Timestamp.lt(fromMillis))
            .orderDesc(NxWearSportSampleDao.Properties.Timestamp)
            .limit(1)
            .build()
            .unique()
        val records = sportDao.queryBuilder()
            .where(NxWearSportSampleDao.Properties.DeviceId.eq(dbDevice.id),
                NxWearSportSampleDao.Properties.Timestamp.ge(fromMillis),
                NxWearSportSampleDao.Properties.Timestamp.le(toMillis))
            .orderAsc(NxWearSportSampleDao.Properties.Timestamp)
            .build()
            .list()
        var previousDay = previous?.let { record ->
            Calendar.getInstance().apply { timeInMillis = record.timestamp }.let {
                it.get(Calendar.YEAR).toLong() * 1000L + it.get(Calendar.DAY_OF_YEAR)
            }
        } ?: Long.MIN_VALUE
        for (record in records) {
            val calendar = Calendar.getInstance().apply { timeInMillis = record.timestamp }
            val day = calendar.get(Calendar.YEAR).toLong() * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
            val baseline = previous?.takeIf { previousDay == day &&
                record.steps >= it.steps &&
                record.distance >= it.distance &&
                record.calories >= it.calories }
            val stepDelta = (record.steps - (baseline?.steps ?: 0)).coerceAtLeast(0)
            val distanceDelta = (record.distance - (baseline?.distance ?: 0)).coerceAtLeast(0)
            val calorieDelta = (record.calories - (baseline?.calories ?: 0)).coerceAtLeast(0)
            previous = record
            previousDay = day
            val timestamp = (record.timestamp / 1000L).toInt()
            if (timestamp !in timestampFrom..timestampTo) continue
            val sample = samplesByTimestamp[timestamp] ?: GenericActivitySample().also {
                it.provider = this
                it.timestamp = timestamp
                samples.add(it)
                samplesByTimestamp[timestamp] = it
            }
            sample.rawKind = ActivityKind.ACTIVITY.code
            sample.steps = stepDelta
            sample.distanceCm = (distanceDelta.toLong() * 100L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            // ActivitySample calories are milli-kcal; ActivityAnalysis converts the accumulated
            // value to kcal for the Calories chart. Keep the wire's milli-kcal precision here.
            sample.activeCalories = calorieDelta
        }
        val sleepProvider = GenericSleepStageSampleProvider(device, session)
        val stages = sleepProvider.getAllSamples(timestampFrom.toLong() * 1000L, timestampTo.toLong() * 1000L).toMutableList()
        sleepProvider.getLastSampleBefore(timestampFrom.toLong() * 1000L)?.let { stages.add(0, it) }
        val byTimestamp = samplesByTimestamp
        for (stage in stages) {
            val start = (stage.timestamp / 1000L).toInt()
            val end = start.toLong() + stage.duration * 60L
            var timestamp = maxOf(start.toLong(), timestampFrom.toLong())
            while (timestamp < end && timestamp <= timestampTo) {
                val sample = byTimestamp.getOrPut(timestamp.toInt()) {
                    GenericActivitySample().also {
                        it.provider = this
                        it.timestamp = timestamp.toInt()
                    }
                }
                sample.rawKind = stage.stage
                timestamp += 60
            }
            // Preserve heart-rate points with seconds offsets inside each sleep interval.
            for (sample in samples) {
                if (sample.timestamp.toLong() >= start && sample.timestamp.toLong() < end) sample.rawKind = stage.stage
            }
        }
        return byTimestamp.values.sortedBy { it.timestamp }.toMutableList()
    }

    override fun getLatestActivitySample(): GenericActivitySample? =
        getLatestActivitySample((System.currentTimeMillis() / 1000L).toInt())

    override fun getLatestActivitySample(until: Int): GenericActivitySample? =
        getBoundaryActivitySample(0, until, latest = true)

    override fun getFirstActivitySample(): GenericActivitySample? =
        getFirstActivitySample(0)

    override fun getFirstActivitySample(after: Int): GenericActivitySample? =
        getBoundaryActivitySample(after, (System.currentTimeMillis() / 1000L).toInt(), latest = false)

    private fun getBoundaryActivitySample(from: Int, to: Int, latest: Boolean): GenericActivitySample? {
        if (from > to) return null
        val deviceId = DBHelper.findDevice(device, session)?.id ?: return null
        val fromMillis = from.toLong() * 1000L
        val toMillis = to.toLong() * 1000L
        val timestamps = mutableListOf<Int>()

        val heartRateProvider = GenericHeartRateSampleProvider(device, session)
        val heartRate = if (latest) heartRateProvider.getLatestSample(toMillis)
            else heartRateProvider.getNextSampleAfter(fromMillis)
        heartRate?.takeIf { it.timestamp in fromMillis..toMillis }
            ?.let { timestamps.add((it.timestamp / 1000L).toInt()) }

        val sportQuery = session.nxWearSportSampleDao.queryBuilder()
            .where(NxWearSportSampleDao.Properties.DeviceId.eq(deviceId),
                NxWearSportSampleDao.Properties.Timestamp.ge(fromMillis),
                NxWearSportSampleDao.Properties.Timestamp.le(toMillis))
        if (latest) sportQuery.orderDesc(NxWearSportSampleDao.Properties.Timestamp)
        else sportQuery.orderAsc(NxWearSportSampleDao.Properties.Timestamp)
        sportQuery.limit(1).build().unique()?.let { timestamps.add((it.timestamp / 1000L).toInt()) }

        val sleepProvider = GenericSleepStageSampleProvider(device, session)
        val precedingStage = sleepProvider.getLastSampleBefore(fromMillis)
        getSleepBoundaryTimestamp(deviceId, from, to, latest, precedingStage)?.let { timestamps.add(it) }

        val timestamp = (if (latest) timestamps.maxOrNull() else timestamps.minOrNull()) ?: return null
        // Include sub-second records that collapse to this activity timestamp, without
        // exceeding the original upper bound. The next-second boundary is discarded.
        val mergeUntil = if (timestamp < to) timestamp + 1 else timestamp
        val sample = getAllActivitySamples(timestamp, mergeUntil).firstOrNull { it.timestamp == timestamp }
            ?: GenericActivitySample().also {
                it.provider = this
                it.timestamp = timestamp
            }
        val activeStage = getSleepStageAt(deviceId, from, to, timestamp)
        val activePrecedingStage = precedingStage?.takeIf {
            it.timestamp / 1000L + it.duration * 60L > timestamp
        }
        (activeStage ?: activePrecedingStage)?.let { sample.rawKind = it.stage }
        return sample
    }

    private fun getSleepBoundaryTimestamp(
        deviceId: Long,
        from: Int,
        to: Int,
        latest: Boolean,
        precedingStage: GenericSleepStageSample?
    ): Int? {
        val fromMillis = from.toLong() * 1000L
        val toMillis = to.toLong() * 1000L
        val precedingTimestamp = precedingStage?.let { stage ->
            val end = stage.timestamp / 1000L + stage.duration * 60L
            if (end <= from) null
            else if (latest) (from + (minOf(to.toLong(), end - 1) - from) / 60 * 60).toInt()
            else from
        }
        val query = session.genericSleepStageSampleDao.queryBuilder()
            .where(GenericSleepStageSampleDao.Properties.DeviceId.eq(deviceId),
                GenericSleepStageSampleDao.Properties.Timestamp.ge(fromMillis),
                GenericSleepStageSampleDao.Properties.Timestamp.le(toMillis),
                GenericSleepStageSampleDao.Properties.Duration.gt(0))
        if (latest) {
            val stageStartSeconds = "CAST(T.\"TIMESTAMP\" / 1000 AS INTEGER)"
            val lastMinuteOffset = "min(T.\"DURATION\" - 1, ($to - $stageStartSeconds) / 60)"
            query.orderRaw("$stageStartSeconds + 60 * $lastMinuteOffset DESC")
        } else {
            query.orderAsc(GenericSleepStageSampleDao.Properties.Timestamp)
        }
        val stageTimestamp = query.limit(1).build().unique()?.let { stage ->
            val start = stage.timestamp / 1000L
            if (latest) (start + minOf(stage.duration.toLong() - 1, (to - start) / 60) * 60).toInt()
            else start.toInt()
        }
        val timestamps = listOfNotNull(precedingTimestamp, stageTimestamp)
        return if (latest) timestamps.maxOrNull() else timestamps.minOrNull()
    }

    private fun getSleepStageAt(deviceId: Long, from: Int, to: Int, timestamp: Int): GenericSleepStageSample? {
        val fromMillis = from.toLong() * 1000L
        val toMillis = to.toLong() * 1000L
        return session.genericSleepStageSampleDao.queryBuilder()
            .where(GenericSleepStageSampleDao.Properties.DeviceId.eq(deviceId),
                GenericSleepStageSampleDao.Properties.Timestamp.ge(fromMillis),
                GenericSleepStageSampleDao.Properties.Timestamp.le(minOf(toMillis, timestamp.toLong() * 1000L + 999L)),
                StringCondition("CAST(T.\"TIMESTAMP\" / 1000 AS INTEGER) + T.\"DURATION\" * 60 > ?", timestamp))
            .orderDesc(GenericSleepStageSampleDao.Properties.Timestamp)
            .limit(1)
            .build()
            .unique()
    }
}
