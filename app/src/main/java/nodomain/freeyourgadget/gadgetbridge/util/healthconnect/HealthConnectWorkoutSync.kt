/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.util.healthconnect

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.HealthConnectWorkoutSyncFailure
import nodomain.freeyourgadget.gadgetbridge.entities.HealthConnectWorkoutSyncFailureDao
import org.slf4j.LoggerFactory

/**
 * Workouts whose Health Connect insert failed.
 *
 * The syncer logs and skips such a workout, and the WORKOUTS cursor moves past it once a later
 * workout goes through, so the failure is kept in [HealthConnectWorkoutSyncFailure] until a later
 * insert of the same workout succeeds or the sync state is reset.
 */
object HealthConnectWorkoutSync {
    /** Local broadcast sent when workout sync failures may have changed: a sync ended or was reset. */
    const val ACTION_STATE_CHANGED = "nodomain.freeyourgadget.gadgetbridge.healthconnect.action.workout_sync_state_changed"

    private val LOG = LoggerFactory.getLogger(HealthConnectWorkoutSync::class.java)

    /** Ids of the given summaries that failed to sync. */
    fun failedIn(summaries: Collection<BaseActivitySummary>): Set<Long> {
        val ids = summaries.mapNotNullTo(HashSet()) { it.id }
        if (ids.isEmpty()) return emptySet()
        return try {
            GBApplication.acquireDbReadOnly().use { db ->
                // A query, not loadAll(), for the reason given in failureOf. The table only holds
                // failed workouts, so it is read whole rather than binding every id of the list.
                db.daoSession.healthConnectWorkoutSyncFailureDao.queryBuilder()
                    .list()
                    .mapNotNullTo(HashSet()) { failure -> failure.summaryId.takeIf { it in ids } }
            }
        } catch (e: Exception) {
            LOG.error("Failed to read Health Connect sync failures of {} workouts", ids.size, e)
            emptySet()
        }
    }

    fun failureOf(summaryId: Long): HealthConnectWorkoutSyncFailure? {
        return try {
            GBApplication.acquireDbReadOnly().use { db ->
                // A query, not load(): resets delete rows by query, which leaves them in the session cache
                db.daoSession.healthConnectWorkoutSyncFailureDao.queryBuilder()
                    .where(HealthConnectWorkoutSyncFailureDao.Properties.SummaryId.eq(summaryId))
                    .unique()
            }
        } catch (e: Exception) {
            LOG.error("Failed to read Health Connect sync failure of summary {}", summaryId, e)
            null
        }
    }

    fun recordFailure(summary: BaseActivitySummary, error: String) {
        val summaryId = summary.id ?: return
        try {
            GBApplication.acquireDB().use { db ->
                db.daoSession.healthConnectWorkoutSyncFailureDao.insertOrReplace(
                    HealthConnectWorkoutSyncFailure(summaryId, summary.deviceId, System.currentTimeMillis(), error)
                )
            }
        } catch (e: Exception) {
            LOG.error("Failed to record Health Connect sync failure of summary {}", summaryId, e)
        }
    }

    fun clearFailure(summaryId: Long) {
        try {
            GBApplication.acquireDB().use { db ->
                db.daoSession.healthConnectWorkoutSyncFailureDao.deleteByKey(summaryId)
            }
        } catch (e: Exception) {
            LOG.error("Failed to clear Health Connect sync failure of summary {}", summaryId, e)
        }
    }

    @JvmStatic
    fun notifyStateChanged(context: Context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_STATE_CHANGED))
    }
}
