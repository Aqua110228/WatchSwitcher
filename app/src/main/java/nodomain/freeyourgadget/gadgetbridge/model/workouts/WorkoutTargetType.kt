package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * The measurement a [WorkoutTargetType] applies to.
 */
enum class WorkoutTargetMetric {
    PACE,
    SPEED,
    CADENCE,
    HEART_RATE,
    POWER,
    STROKE_RATE,
    EFFORT,
    CSS_OFFSET,
}

/**
 * The target goal of a step (Garmin "intensity target", Zepp OS "workout alert").
 */
enum class WorkoutTargetType(override val label: Int, val metric: WorkoutTargetMetric?) : LabeledEntry {
    NONE(R.string.workout_target_none, null),

    /**
     * A range, in milliseconds per kilometer, or per 100 m for a pool swim.
     */
    PACE(R.string.Pace, WorkoutTargetMetric.PACE),

    /**
     * A range, in millimeters per second.
     */
    SPEED(R.string.Speed, WorkoutTargetMetric.SPEED),

    /**
     * A range, in steps/revolutions per minute.
     */
    CADENCE(R.string.workout_cadence, WorkoutTargetMetric.CADENCE),

    /**
     * A zone number.
     */
    HR_ZONE(R.string.workout_target_hr_zone, WorkoutTargetMetric.HEART_RATE),

    /**
     * A range, in beats per minute.
     */
    HR_RANGE(R.string.workout_target_hr_range, WorkoutTargetMetric.HEART_RATE),

    /**
     * A zone number.
     */
    POWER_ZONE(R.string.workout_target_bike_power_zone, WorkoutTargetMetric.POWER),

    /**
     * A range, in watts.
     */
    POWER_RANGE(R.string.workout_target_bike_power_range, WorkoutTargetMetric.POWER),

    /**
     * A range, in percent of FTP.
     */
    POWER_FTP_RANGE(R.string.workout_target_power_ftp_range, WorkoutTargetMetric.POWER),

    /**
     * A range, in strokes per minute.
     */
    STROKE_RATE(R.string.workout_target_stroke_rate, WorkoutTargetMetric.STROKE_RATE),

    /**
     * A [WorkoutEffort] name (pool swim only).
     */
    EFFORT(R.string.workout_target_effort, WorkoutTargetMetric.EFFORT),

    /**
     * An offset from the critical swim speed, in seconds per 100 m.
     */
    CSS_OFFSET(R.string.workout_target_css_offset, WorkoutTargetMetric.CSS_OFFSET),
    ;

    /**
     * Whether this target and [other] apply to the same metric. [NONE] never conflicts.
     */
    fun conflictsWith(other: WorkoutTargetType): Boolean = metric != null && metric == other.metric

    /**
     * Whether the value is a low/high range.
     */
    val isRange: Boolean
        get() = this == PACE || this == SPEED || this == CADENCE || this == HR_RANGE ||
                this == POWER_RANGE || this == POWER_FTP_RANGE || this == STROKE_RATE

    /**
     * Whether the value is a zone number.
     */
    val isZone: Boolean
        get() = this == HR_ZONE || this == POWER_ZONE

    /**
     * Whether the value is an enum name.
     */
    val isEnum: Boolean
        get() = this == EFFORT

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutTargetType? = enumByName(name)
    }
}
