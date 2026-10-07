package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * How the weight of a strength step is specified.
 */
enum class WorkoutWeightType(override val label: Int) : LabeledEntry {
    NONE(R.string.none),

    /**
     * No added weight.
     */
    BODY_WEIGHT(R.string.workout_weight_body_weight),

    /**
     * A weight in grams.
     */
    MANUAL(R.string.manual),

    /**
     * A percentage of the one-rep max.
     */
    PERCENT_1RM(R.string.workout_weight_percent_1rm),

    /**
     * A rep-max count, for example 5RM.
     */
    RM(R.string.workout_weight_rm),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutWeightType? = enumByName(name)
    }
}
