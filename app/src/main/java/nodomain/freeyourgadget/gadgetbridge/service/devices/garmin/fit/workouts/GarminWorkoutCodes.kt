package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEffort
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEquipment
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutPoolLengthUnit
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimDrill
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimStroke
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Intensity
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.SwimStroke
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.WktStepDuration
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.WktSwimDrill
import kotlin.math.roundToLong
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.WorkoutEquipment as FitWorkoutEquipment

object GarminWorkoutCodes {
    /**
     * `workout_step.intensity`. [WorkoutStepType.OTHER] and [WorkoutStepType.ACTIVE] both encode
     * to [Intensity.ACTIVE], since captured FIT files never use [Intensity.OTHER].
     */
    fun intensityOf(stepType: WorkoutStepType): Intensity = when (stepType) {
        WorkoutStepType.ACTIVE, WorkoutStepType.OTHER -> Intensity.ACTIVE
        WorkoutStepType.MAIN -> Intensity.MAIN
        WorkoutStepType.REST -> Intensity.REST
        WorkoutStepType.WARMUP -> Intensity.WARMUP
        WorkoutStepType.COOLDOWN -> Intensity.COOLDOWN
        WorkoutStepType.RECOVER -> Intensity.RECOVERY
    }

    fun durationTypeCode(type: WorkoutDurationType): WktStepDuration = when (type) {
        WorkoutDurationType.TIME -> WktStepDuration.TIME
        WorkoutDurationType.DISTANCE -> WktStepDuration.DISTANCE
        WorkoutDurationType.HR_BELOW -> WktStepDuration.HR_LESS_THAN
        WorkoutDurationType.HR_ABOVE -> WktStepDuration.HR_GREATER_THAN
        WorkoutDurationType.CALORIES -> WktStepDuration.CALORIES
        WorkoutDurationType.BUTTON_PRESS -> WktStepDuration.OPEN
        WorkoutDurationType.POWER_BELOW -> WktStepDuration.POWER_LESS_THAN
        WorkoutDurationType.POWER_ABOVE -> WktStepDuration.POWER_GREATER_THAN
        WorkoutDurationType.REPS -> WktStepDuration.REPS
        WorkoutDurationType.SEND_OFF_TIME -> WktStepDuration.REPETITION_TIME
        WorkoutDurationType.CSS_SEND_OFF_TIME -> WktStepDuration.CSS_REPETITION_TIME
        else -> WktStepDuration.OPEN
    }

    /**
     * Bpm are offset by +100 in the heart rate fields of a `workout_step`.
     */
    const val HR_OFFSET = 100

    /**
     * Watts are offset by +1000 in a custom power target. A percent-of-FTP range has no offset.
     */
    const val POWER_OFFSET = 1000

    /**
     * A critical swim speed offset, in seconds, is offset by +1000 in a `workout_step` target or
     * duration value. A negative offset is positive on the FIT file.
     */
    const val CSS_OFFSET = 1000

    /**
     * `workout_step.target_value` of a `SWIM_STROKE` target for a step with no set stroke.
     */
    const val SWIM_STROKE_CHOICE = 255L

    /**
     * `workout_step.target_value` of a `SWIM_STROKE` target.
     */
    fun swimStrokeCode(stroke: WorkoutSwimStroke?): Long = when (stroke) {
        WorkoutSwimStroke.FREESTYLE -> SwimStroke.FREESTYLE.num.toLong()
        WorkoutSwimStroke.BACKSTROKE -> SwimStroke.BACKSTROKE.num.toLong()
        WorkoutSwimStroke.BREASTSTROKE -> SwimStroke.BREASTSTROKE.num.toLong()
        WorkoutSwimStroke.BUTTERFLY -> SwimStroke.BUTTERFLY.num.toLong()
        WorkoutSwimStroke.DRILL -> SwimStroke.DRILL.num.toLong()
        WorkoutSwimStroke.MIXED -> SwimStroke.MIXED.num.toLong()
        WorkoutSwimStroke.INDIVIDUAL_MEDLEY -> SwimStroke.IM.num.toLong()
        WorkoutSwimStroke.IM_BY_ROUND -> SwimStroke.IM_BY_ROUND.num.toLong()
        WorkoutSwimStroke.REVERSE_IM_ORDER -> SwimStroke.REVERSE_IM_ORDER.num.toLong()
        WorkoutSwimStroke.CHOICE, null -> SWIM_STROKE_CHOICE
    }

    /**
     * `workout_step.swim_drill_type`, or null for a step with no drill.
     */
    fun swimDrillCode(drill: WorkoutSwimDrill?): WktSwimDrill? = when (drill) {
        WorkoutSwimDrill.KICK -> WktSwimDrill.KICK
        WorkoutSwimDrill.PULL -> WktSwimDrill.PULL
        WorkoutSwimDrill.DRILL -> WktSwimDrill.DRILL
        WorkoutSwimDrill.NONE, null -> null
    }

    /**
     * `workout_step.equipment`, or null for a step with no equipment.
     */
    fun equipmentCode(equipment: WorkoutEquipment?): FitWorkoutEquipment? = when (equipment) {
        WorkoutEquipment.FINS -> FitWorkoutEquipment.SWIM_FINS
        WorkoutEquipment.KICKBOARD -> FitWorkoutEquipment.SWIM_KICKBOARD
        WorkoutEquipment.PADDLES -> FitWorkoutEquipment.SWIM_PADDLES
        WorkoutEquipment.PULL_BUOY -> FitWorkoutEquipment.SWIM_PULL_BUOY
        WorkoutEquipment.SNORKEL -> FitWorkoutEquipment.SWIM_SNORKEL
        WorkoutEquipment.NONE, null -> null
    }

    /**
     * `workout_step.secondary_target_value` of a `SWIM_EFFORT` target.
     */
    fun effortCode(effort: WorkoutEffort): Long = when (effort) {
        WorkoutEffort.RECOVERY -> 0
        WorkoutEffort.EASY -> 2
        WorkoutEffort.MODERATE -> 3
        WorkoutEffort.HARD -> 4
        WorkoutEffort.VERY_HARD -> 5
        WorkoutEffort.ALL_OUT -> 6
        WorkoutEffort.ASCENDING -> 8
        WorkoutEffort.DESCENDING -> 9
    }

    /**
     * Whether a cadence is halved on the FIT file. A running cadence is stored in revolutions, of
     * two steps each.
     */
    fun isHalvedCadence(activityKind: ActivityKind): Boolean = when (activityKind) {
        ActivityKind.RUNNING, ActivityKind.TREADMILL, ActivityKind.INDOOR_TRACK_RUNNING,
        ActivityKind.TRACK_RUN, ActivityKind.TRAIL_RUN, ActivityKind.STREET_RUNNING,
        ActivityKind.OUTDOOR_RUNNING, ActivityKind.INDOOR_RUNNING, ActivityKind.CUSTOM,
            -> true

        else -> false
    }

    /**
     * Converts a pace range in ms/km to a speed range in mm/s. The slower pace is the low speed.
     */
    fun paceMsPerKmToSpeedMmPerS(paceLowMsPerKm: Long?, paceHighMsPerKm: Long?): Pair<Long, Long> {
        val speedLow = paceHighMsPerKm.toSpeedMmPerS(1_000_000_000.0) ?: 0L
        val speedHigh = paceLowMsPerKm.toSpeedMmPerS(1_000_000_000.0) ?: 100_000L
        return speedLow to speedHigh
    }

    /**
     * Converts a pool swim pace in ms per 100 m to a speed in mm/s.
     */
    fun swimPaceMsPer100mToSpeedMmPerS(paceMsPer100m: Long?): Long =
        paceMsPer100m.toSpeedMmPerS(100_000_000.0) ?: 100_000L

    /**
     * The speed of this pace in mm/s, or null if the pace is null or zero. [distanceFactor] is the
     * distance of the pace in mm, times 1000 for the pace in ms.
     */
    private fun Long?.toSpeedMmPerS(distanceFactor: Double): Long? =
        this?.takeIf { it > 0 }?.let { (distanceFactor / it).roundToLong() }

    /**
     * `workout.pool_length_unit`, a FIT `display_measure`: 0 metric, 1 statute.
     */
    fun poolLengthUnitCode(unit: WorkoutPoolLengthUnit): Int = if (unit.yards) 1 else 0
}
