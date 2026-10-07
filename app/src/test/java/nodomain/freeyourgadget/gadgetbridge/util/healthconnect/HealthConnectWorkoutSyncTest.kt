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

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Date

class HealthConnectWorkoutSyncTest : TestBase() {

    @Before
    override fun setUp() {
        super.setUp()
        GBApplication.acquireDB().use { db ->
            db.daoSession.healthConnectWorkoutSyncFailureDao.deleteAll()
        }
    }

    private fun summary(id: Long?, deviceId: Long) =
        BaseActivitySummary().apply {
            this.id = id
            this.deviceId = deviceId
            startTime = Date(1_000_000)
            endTime = Date(2_000_000)
        }

    @Test
    fun workoutWithoutFailureIsNotListed() {
        assertEquals(emptySet<Long>(), HealthConnectWorkoutSync.failedIn(listOf(summary(10, 1))))
    }

    @Test
    fun failureIsListedUntilCleared() {
        val workout = summary(10, 1)
        val other = summary(11, 1)
        HealthConnectWorkoutSync.recordFailure(workout, "boom")

        assertEquals(setOf(10L), HealthConnectWorkoutSync.failedIn(listOf(workout, other)))
        assertEquals("boom", HealthConnectWorkoutSync.failureOf(10)?.error)

        HealthConnectWorkoutSync.clearFailure(10)
        assertEquals(emptySet<Long>(), HealthConnectWorkoutSync.failedIn(listOf(workout, other)))
        assertNull(HealthConnectWorkoutSync.failureOf(10))
    }

    @Test
    fun onlyFailuresOfTheGivenSummariesAreListed() {
        HealthConnectWorkoutSync.recordFailure(summary(10, 1), "boom")
        assertEquals(emptySet<Long>(), HealthConnectWorkoutSync.failedIn(listOf(summary(11, 1))))
    }

    @Test
    fun summariesWithoutIdAreIgnored() {
        assertEquals(emptySet<Long>(), HealthConnectWorkoutSync.failedIn(listOf(summary(null, 1))))
    }

    @Test
    fun resettingWorkoutsOfOneDeviceClearsItsFailuresOnly() {
        val device = GBApplication.acquireDB().use { db ->
            DBHelper.getDevice(createDummyGDevice("00:00:00:00:00:01"), db.daoSession).id!!
        }
        val other = GBApplication.acquireDB().use { db ->
            DBHelper.getDevice(createDummyGDevice("00:00:00:00:00:02"), db.daoSession).id!!
        }
        val failed = summary(10, device)
        val otherFailed = summary(11, other)
        HealthConnectWorkoutSync.recordFailure(failed, "boom")
        HealthConnectWorkoutSync.recordFailure(otherFailed, "boom")

        HealthConnectUtils.resetSyncState(
            createDummyGDevice("00:00:00:00:00:01"),
            HealthConnectPermissionManager.HealthConnectDataType.WORKOUTS
        )

        assertEquals(setOf(11L), HealthConnectWorkoutSync.failedIn(listOf(failed, otherFailed)))
        assertNull(HealthConnectWorkoutSync.failureOf(10))
    }
}
