package nodomain.freeyourgadget.gadgetbridge.widgets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StepAnalysis
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import java.util.Date
import java.util.GregorianCalendar
import kotlin.math.roundToInt

/**
 * The selected-day query parameters shared by every widget instance in one refresh.
 */
data class DashboardQuery(
    val timeFrom: Int,
    val timeTo: Int,
)

/**
 * Per-widget-instance view over one dashboard refresh: the selected day ([query]) and the devices
 * this instance resolved to.
 */
class WidgetDataScope(
    val query: DashboardQuery,
    val devices: List<GBDevice>,
) {
    /**
     * Runs [block] against a freshly-acquired read handle, closed when [block] returns.
     */
    suspend fun <T> db(block: (DBHandler) -> T): T = withContext(Dispatchers.IO) {
        GBApplication.acquireDbReadOnly().use { block(it) }
    }

    private fun dailyTotals(db: DBHandler, device: GBDevice): DailyTotals {
        val day = GregorianCalendar.getInstance()
        day.timeInMillis = query.timeTo * 1000L
        return DailyTotals.getDailyTotalsForDevice(device, day, db)
    }

    suspend fun allSamples(device: GBDevice): List<ActivitySample> = db { db ->
        val provider = device.deviceCoordinator.getSampleProvider(device, db.daoSession)
        provider?.getAllActivitySamples(query.timeFrom, query.timeTo) ?: emptyList()
    }

    /**
     * The device's numeric DB id.
     */
    suspend fun dbDeviceId(device: GBDevice): Long = db { db ->
        DBHelper.getDevice(device, db.daoSession).id ?: 0L
    }

    suspend fun workoutSummaries(deviceIds: List<Long>): List<BaseActivitySummary> {
        if (deviceIds.isEmpty()) return emptyList()
        return db { db ->
            db.daoSession.baseActivitySummaryDao.queryBuilder().where(
                BaseActivitySummaryDao.Properties.StartTime.gt(Date(query.timeFrom * 1000L)),
                BaseActivitySummaryDao.Properties.EndTime.lt(Date(query.timeTo * 1000L)),
                BaseActivitySummaryDao.Properties.DeviceId.`in`(deviceIds),
            ).build().list()
        }
    }

    //
    // Aggregates shared by multiple widgets.
    //

    suspend fun stepsTotal(): Int = db { db ->
        devices.filter { it.deviceCoordinator.supportsStepCounter(it) }
            .sumOf { dailyTotals(db, it).steps.toInt() }
    }

    fun stepsGoalFactor(stepsTotal: Int): Float {
        val goal = ActivityUser().stepsGoal.toFloat()
        return (stepsTotal / goal).coerceAtMost(1f)
    }

    suspend fun sleepMinutesTotal(): Long = db { db ->
        val sleepValues = devices.filter { it.deviceCoordinator.supportsSleepMeasurement(it) }
            .map { dailyTotals(db, it).sleep }
            .filter { it > 0 }
        if (sleepValues.isEmpty()) 0L else sleepValues.average().roundToInt().toLong()
    }

    fun sleepGoalFactor(sleepMinutesTotal: Long): Float {
        val goal = ActivityUser().sleepDurationGoal
        return (sleepMinutesTotal.toFloat() / goal).coerceAtMost(1f)
    }

    suspend fun distanceTotal(): Float = db { db ->
        val stepLength = ActivityUser().stepLengthCm
        var totalDistanceCm = 0L
        for (dev in devices.filter { it.deviceCoordinator.supportsStepCounter(it) }) {
            val totals = dailyTotals(db, dev)
            totalDistanceCm += if (totals.steps > 0 && totals.distance > 0) {
                totals.distance
            } else {
                totals.steps * stepLength
            }
        }
        totalDistanceCm * 0.01f
    }

    fun distanceGoalFactor(distanceTotal: Float): Float {
        val goal = ActivityUser().distanceGoalMeters.toFloat()
        return (distanceTotal / goal).coerceAtMost(1f)
    }

    suspend fun activeCaloriesTotal(): Int = db { db ->
        val total = devices.filter { it.deviceCoordinator.supportsActiveCalories(it) }
            .sumOf { dailyTotals(db, it).activeCalories.toInt() }
        // Convert calories to kcal
        total / 1000
    }

    fun activeCaloriesGoalFactor(activeCaloriesTotal: Int): Float {
        val goal = ActivityUser().caloriesBurntGoal.toFloat()
        return (activeCaloriesTotal / goal).coerceAtMost(1f)
    }

    suspend fun restingCaloriesTotal(): Int = db { db ->
        var total = 0
        var count = 0
        for (dev in devices.filter { it.deviceCoordinator.supportsActiveCalories(it) }) {
            val resting = dailyTotals(db, dev).restingCalories.toInt()
            if (resting > 0) {
                total += resting
                count++
            }
        }
        if (count == 0) 0 else (total / count.toFloat()).roundToInt()
    }

    suspend fun activeMinutes(device: GBDevice): Long {
        val samples = allSamples(device)
        val stepAnalysis = StepAnalysis()
        val sessions = stepAnalysis.calculateStepSessions(samples, emptyList())
        val summary = stepAnalysis.calculateSummary(sessions, sessions.isEmpty())
        return (summary.endTime.time - summary.startTime.time) / 1000 / 60
    }

    suspend fun activeMinutesTotal(): Long =
        devices.filter { it.deviceCoordinator.supportsStepCounter(it) }
            .sumOf { activeMinutes(it) }

    fun activeMinutesGoalFactor(activeMinutesTotal: Long): Float {
        val goal = ActivityUser().activeTimeGoalMinutes.toFloat()
        return (activeMinutesTotal / goal).coerceAtMost(1f)
    }
}
