package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * The sync status of a [WorkoutTemplate], as displayed in the template list.
 */
enum class WorkoutSyncStatus(override val label: Int) : LabeledEntry {
    NOT_SYNCED(R.string.workout_sync_status_not_synced),
    SYNCED(R.string.workout_sync_status_synced),
    OUT_OF_DATE(R.string.workout_sync_status_out_of_date),
    IN_PROGRESS(R.string.workout_sync_status_in_progress),
    FAILED(R.string.workout_sync_status_failed),
}

/**
 * The persisted sync status of a [WorkoutTemplate].
 */
enum class WorkoutSyncStateStatus {
    PENDING,
    SUCCESS,
    FAILED,
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutSyncStateStatus? = enumByName(name)
    }
}
