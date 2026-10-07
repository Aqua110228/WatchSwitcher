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

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider
import nodomain.freeyourgadget.gadgetbridge.service.devices.nxwear.NxWearWorkout
import org.slf4j.LoggerFactory
import java.util.Date

/** HR-only workout details: the B8 capture does not provide a GPS route. */
internal class NxWearActivityTrackProvider(private val device: GBDevice) : ActivityTrackProvider {
    override fun getActivityTrack(summary: BaseActivitySummary): ActivityTrack? {
        val workout = NxWearWorkout.decode(summary.rawSummaryData) ?: return null
        val end = workout.endMillis ?: return null
        return try {
            GBApplication.acquireDbReadOnly().use { db ->
                val samples = GenericHeartRateSampleProvider(device, db.daoSession)
                    .getAllSamples(workout.startMillis, end).filter { it.heartRate > 0 }
                if (samples.isEmpty()) return null
                ActivityTrack().apply {
                    user = summary.user
                    this.device = summary.device
                    name = summary.name
                    for (sample in samples) {
                        addTrackPoint(ActivityPoint(Date(sample.timestamp)).apply { heartRate = sample.heartRate })
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Unable to build Nx Wear B8 workout details", e)
            null
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(NxWearActivityTrackProvider::class.java)
    }
}
