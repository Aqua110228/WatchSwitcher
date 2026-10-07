package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * A pool length preset. [CUSTOM] uses the length stored on the template.
 */
enum class WorkoutPoolLengthUnit(
    override val label: Int,
    val lengthCm: Int? = null,
    val yards: Boolean = false,
) : LabeledEntry {
    UNSPECIFIED(R.string.workout_pool_length_unspecified),
    POOL_25M(R.string.workout_pool_length_25m, 2500),
    POOL_25YD(R.string.workout_pool_length_25yd, 2286, yards = true),
    POOL_50M(R.string.workout_pool_length_50m, 5000),
    POOL_33_1_3M(R.string.workout_pool_length_33_1_3m, 3333),
    POOL_33_1_3YD(R.string.workout_pool_length_33_1_3yd, 3048, yards = true),
    CUSTOM(R.string.custom),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutPoolLengthUnit? = enumByName(name)
    }
}
