package nodomain.freeyourgadget.gadgetbridge.model.workouts

import androidx.annotation.ColorRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * The type of a step. [colorRes] is the color of the step in the editor. A sport can display
 * [ACTIVE] with its own label, see [nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.SportSpec.activeLabel].
 */
enum class WorkoutStepType(override val label: Int, @field:ColorRes val colorRes: Int) : LabeledEntry {
    WARMUP(R.string.workout_step_type_warmup, R.color.workout_step_color_warmup),
    ACTIVE(R.string.activeSeconds, R.color.workout_step_color_active),

    /**
     * The main set of a pool swim - Garmin-specific.
     */
    MAIN(R.string.workout_step_type_main, R.color.workout_step_color_main),
    RECOVER(R.string.workout_step_type_recover, R.color.workout_step_color_recover),
    REST(R.string.workout_step_type_rest, R.color.workout_step_color_rest),
    COOLDOWN(R.string.workout_step_type_cooldown, R.color.workout_step_color_cooldown),
    OTHER(R.string.other, R.color.workout_step_color_other),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutStepType? = enumByName(name)
    }
}
