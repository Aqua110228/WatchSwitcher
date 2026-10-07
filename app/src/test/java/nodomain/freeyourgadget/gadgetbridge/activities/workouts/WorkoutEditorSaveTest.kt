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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/**
 * Covers what an edit made on the workout detail screen writes: the field the user changed, and
 * never the summary data the screen re-parsed into the entity.
 */
class WorkoutEditorSaveTest : TestBase() {

    private fun stored(storedData: String?) = BaseActivitySummary().apply {
        startTime = Date(1_600_000_000_000L)
        endTime = Date(1_600_003_600_000L)
        activityKind = 0x10
        deviceId = 1L
        userId = 1L
        summaryData = storedData
        GBApplication.acquireDB().use { it.daoSession.baseActivitySummaryDao.insertOrReplace(this) }
    }

    private fun storedSummaryData(id: Long): String? =
        WorkoutUploadStore.storedSummaryData(listOf(id))[id]

    @Test
    fun aRenameKeepsTheStoredSummaryData() {
        val summary = stored("""{"stored":1}""")

        summary.summaryData = """{"reparsed":2}"""
        summary.name = "Renamed"
        WorkoutEditor.saveKeepingStoredSummaryData(summary)

        assertEquals("""{"stored":1}""", storedSummaryData(summary.id!!))
        assertEquals("""{"reparsed":2}""", summary.summaryData)
        val reloaded = GBApplication.acquireDbReadOnly().use {
            it.daoSession.baseActivitySummaryDao.queryBuilder().list().single { s -> s.id == summary.id }
        }
        assertEquals("Renamed", reloaded.name)
    }

    @Test
    fun aSummaryStoredWithoutDataStaysWithout() {
        val summary = stored(null)
        summary.summaryData = """{"reparsed":2}"""
        WorkoutEditor.saveKeepingStoredSummaryData(summary)

        GBApplication.acquireDbReadOnly().use { db ->
            db.daoSession.database.rawQuery(
                "SELECT SUMMARY_DATA FROM BASE_ACTIVITY_SUMMARY WHERE _id = ?",
                arrayOf(summary.id.toString())
            ).use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
            }
        }
    }
}
