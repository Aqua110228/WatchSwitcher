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

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

internal object ActiveCaloriesSyncer : AbstractActivitySampleSyncer<ActiveCaloriesBurnedRecord>() {
    override val logger: Logger = LoggerFactory.getLogger(ActiveCaloriesSyncer::class.java)
    override val recordClass: KClass<ActiveCaloriesBurnedRecord> = ActiveCaloriesBurnedRecord::class

    override fun convertMinute(
        endTs: Instant,
        minuteSamples: List<ActivitySample>,
        offset: ZoneOffset,
        metadata: Metadata,
        deviceName: String,
        version: Long
    ): ActiveCaloriesBurnedRecord? {
        // Sum the minute's samples; NOT_MEASURED (-1) and 0 both mean "no calories" and contribute nothing.
        val caloriesInMinute = minuteSamples.sumOf { if (it.activeCalories > 0) it.activeCalories else 0 }
        if (caloriesInMinute <= 0) {
            return null
        }
        // HC's ActiveCaloriesBurnedRecord caps energy at 1_000_000 kcal (= 1e9 cal).
        if (caloriesInMinute > 1_000_000_000) {
            logger.skipOutOfRange(deviceName, "ActiveCalories", "$caloriesInMinute cal", "<= 1000000 kcal per record")
            return null
        }

        val startTs = endTs.minus(1, ChronoUnit.MINUTES)

        return ActiveCaloriesBurnedRecord(
            startTs,
            offset,
            endTs,
            offset,
            Energy.calories(caloriesInMinute.toDouble()),
            clientRecordMetadata(metadata, "calories", endTs.epochSecond, version)
        )
    }
}
