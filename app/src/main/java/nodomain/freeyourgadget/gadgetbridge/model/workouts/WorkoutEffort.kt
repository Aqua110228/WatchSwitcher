package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * The effort of a pool swim step (the value of a [WorkoutTargetType.EFFORT] target).
 */
enum class WorkoutEffort(override val label: Int) : LabeledEntry {
    ASCENDING(R.string.workout_effort_ascending),
    DESCENDING(R.string.workout_effort_descending),
    RECOVERY(R.string.workout_effort_recovery),
    EASY(R.string.hrZoneEasy),
    MODERATE(R.string.training_readiness_zone_moderate),
    HARD(R.string.workout_effort_hard),
    VERY_HARD(R.string.workout_effort_very_hard),
    ALL_OUT(R.string.workout_effort_all_out),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutEffort? = enumByName(name)
    }
}
