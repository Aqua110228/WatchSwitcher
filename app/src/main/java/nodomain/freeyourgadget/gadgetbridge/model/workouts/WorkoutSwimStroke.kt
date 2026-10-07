package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * Pool swim step stroke.
 */
enum class WorkoutSwimStroke(override val label: Int) : LabeledEntry {
    FREESTYLE(R.string.freestyle),
    BACKSTROKE(R.string.backstroke),
    BREASTSTROKE(R.string.breaststroke),
    BUTTERFLY(R.string.swim_style_butterfly),
    DRILL(R.string.swim_style_drill),
    CHOICE(R.string.workout_swim_stroke_choice),
    INDIVIDUAL_MEDLEY(R.string.workout_swim_stroke_individual_medley),
    IM_BY_ROUND(R.string.workout_swim_stroke_im_by_round),
    REVERSE_IM_ORDER(R.string.workout_swim_stroke_reverse_im_order),
    MIXED(R.string.swim_style_mixed),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutSwimStroke? = enumByName(name)
    }
}
