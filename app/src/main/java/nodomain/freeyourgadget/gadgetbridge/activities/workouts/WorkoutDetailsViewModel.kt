package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.LoggerFactory
import java.util.Date

class WorkoutDetailsViewModel : ViewModel() {
    private val _workoutId = MutableLiveData<Long>()
    val workoutId: LiveData<Long> = _workoutId

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    suspend fun loadSingleWorkout(workoutId: Long) {
        _isLoading.value = true
        _error.value = null

        try {
            val workout = withContext(Dispatchers.IO) {
                GBApplication.acquireDbReadOnly().use { dbHandler ->
                    val summaryDao = dbHandler.daoSession.baseActivitySummaryDao
                    summaryDao.load(workoutId)
                }
            }

            if (workout != null) {
                _workoutId.value = workoutId
            } else {
                _error.value = "Workout not found"
            }
        } catch (e: Exception) {
            LOG.error("Error loading single workout", e)
            _error.value = "Failed to load workout: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Resolves a single workout out of a filtered set, by position.
     */
    suspend fun loadFilteredWorkout(
        gbDevice: GBDevice?,
        activityKindFilter: Int,
        dateFromFilter: Long,
        dateToFilter: Long,
        nameContainsFilter: String?,
        deviceFilter: Long,
        itemsFilter: List<Long>?,
        position: Int
    ) {
        _isLoading.value = true
        _error.value = null

        try {
            val workouts = withContext(Dispatchers.IO) {
                loadWorkoutsFromDatabase(
                    gbDevice,
                    activityKindFilter,
                    dateFromFilter,
                    dateToFilter,
                    nameContainsFilter,
                    deviceFilter,
                    itemsFilter
                )
            }

            val validPosition = if (position in workouts.indices) position else 0
            val workout = workouts.getOrNull(validPosition)

            if (workout?.id != null) {
                _workoutId.value = workout.id
            } else {
                _error.value = "Workout not found"
            }
        } catch (e: Exception) {
            LOG.error("Error loading filtered workout", e)
            _error.value = "Failed to load workout: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    private fun loadWorkoutsFromDatabase(
        gbDevice: GBDevice?,
        activityKindFilter: Int,
        dateFromFilter: Long,
        dateToFilter: Long,
        nameContainsFilter: String?,
        deviceFilter: Long,
        itemsFilter: List<Long>?
    ): List<BaseActivitySummary> {
        return GBApplication.acquireDbReadOnly().use { dbHandler ->
            val summaryDao = dbHandler.daoSession.baseActivitySummaryDao
            val dbDevice = gbDevice?.let { DBHelper.findDevice(it, dbHandler.daoSession) }

            val queryBuilder = summaryDao.queryBuilder()

            // Apply device filter
            when {
                deviceFilter != 0L && deviceFilter != ALL_DEVICES -> {
                    queryBuilder.where(BaseActivitySummaryDao.Properties.DeviceId.eq(deviceFilter))
                }
                dbDevice != null -> {
                    queryBuilder.where(BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id))
                }
            }
            queryBuilder.orderDesc(BaseActivitySummaryDao.Properties.StartTime)

            if (activityKindFilter != 0) {
                queryBuilder.where(BaseActivitySummaryDao.Properties.ActivityKind.eq(activityKindFilter))
            }

            if (dateFromFilter != 0L) {
                queryBuilder.where(BaseActivitySummaryDao.Properties.StartTime.gt(Date(dateFromFilter)))
            }
            if (dateToFilter != 0L) {
                queryBuilder.where(BaseActivitySummaryDao.Properties.EndTime.lt(Date(dateToFilter)))
            }

            if (!nameContainsFilter.isNullOrEmpty()) {
                queryBuilder.where(BaseActivitySummaryDao.Properties.Name.like("%${nameContainsFilter}%"))
            }

            if (!itemsFilter.isNullOrEmpty()) {
                queryBuilder.where(BaseActivitySummaryDao.Properties.Id.`in`(itemsFilter))
            }

            queryBuilder.list()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WorkoutDetailsViewModel::class.java)

        const val ALL_DEVICES = 999L
    }
}
