package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * The end condition of a step.
 */
enum class WorkoutDurationType(override val label: Int) : LabeledEntry {
    TIME(R.string.lap_time),
    DISTANCE(R.string.distance),
    LAPS(R.string.laps),
    CALORIES(R.string.calories),
    HR_ABOVE(R.string.workout_duration_hr_above),
    HR_BELOW(R.string.workout_duration_hr_below),
    POWER_ABOVE(R.string.workout_duration_power_above),
    POWER_BELOW(R.string.workout_duration_power_below),
    REPS(R.string.workout_repetitions),
    STROKES(R.string.Strokes),

    /**
     * The step ends when the user presses a button.
     * Garmin "lap button press", Zepp OS "when the skip button is tapped".
     */
    BUTTON_PRESS(R.string.workout_duration_button_press),

    SEND_OFF_TIME(R.string.workout_duration_swimming_send_off_time),
    CSS_SEND_OFF_TIME(R.string.workout_duration_swimming_css_send_off_time),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutDurationType? = enumByName(name)
    }
}
