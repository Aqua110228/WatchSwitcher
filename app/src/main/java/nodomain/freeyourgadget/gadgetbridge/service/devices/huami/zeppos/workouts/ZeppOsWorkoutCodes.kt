package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutMeasurementSystem
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import kotlin.math.roundToLong

object ZeppOsWorkoutCodes {
    const val TRAINING_TYPE_WARMUP = 0
    const val TRAINING_TYPE_TRAINING = 1
    const val TRAINING_TYPE_REST = 2
    const val TRAINING_TYPE_RECOVER = 3
    const val TRAINING_TYPE_COOLDOWN = 4

    /**
     * interval.trainingType
     */
    fun trainingType(stepType: WorkoutStepType?): Int = when (stepType) {
        WorkoutStepType.WARMUP -> TRAINING_TYPE_WARMUP
        WorkoutStepType.ACTIVE -> TRAINING_TYPE_TRAINING
        WorkoutStepType.RECOVER -> TRAINING_TYPE_RECOVER
        WorkoutStepType.REST -> TRAINING_TYPE_REST
        WorkoutStepType.COOLDOWN -> TRAINING_TYPE_COOLDOWN
        else -> throw IllegalArgumentException("Unsupported type $stepType")
    }

    const val DURATION_DISTANCE = 0
    const val DURATION_TIME = 1
    const val DURATION_CALORIES = 2
    const val DURATION_HR_BELOW = 3
    const val DURATION_HR_ABOVE = 4
    const val DURATION_STROKES = 5
    const val DURATION_LAPS = 6 // TODO "by laps" / "by trip" - not 100% confirmed
    const val DURATION_COUNT = 7
    const val DURATION_OPEN_OR_REPS = 8 // "skip button" when value == -1, otherwise reps

    /**
     * The `intervalVal` of a [DURATION_OPEN_OR_REPS] interval that ends on the skip button.
     */
    const val SKIP_BUTTON_VALUE = -1L

    /**
     * interval.intervalType. [WorkoutDurationType.REPS] is [DURATION_COUNT] for jump rope and
     * [DURATION_OPEN_OR_REPS] for every other sport. Null for a type Zepp OS does not have.
     */
    fun durationTypeCode(type: WorkoutDurationType, activityKind: ActivityKind): Int? = when (type) {
        WorkoutDurationType.DISTANCE -> DURATION_DISTANCE
        WorkoutDurationType.TIME -> DURATION_TIME
        WorkoutDurationType.CALORIES -> DURATION_CALORIES
        WorkoutDurationType.HR_BELOW -> DURATION_HR_BELOW
        WorkoutDurationType.HR_ABOVE -> DURATION_HR_ABOVE
        WorkoutDurationType.STROKES -> DURATION_STROKES
        WorkoutDurationType.LAPS -> DURATION_LAPS
        WorkoutDurationType.REPS -> if (activityKind == ActivityKind.JUMP_ROPING) DURATION_COUNT else DURATION_OPEN_OR_REPS
        WorkoutDurationType.BUTTON_PRESS -> DURATION_OPEN_OR_REPS
        WorkoutDurationType.POWER_ABOVE, WorkoutDurationType.POWER_BELOW -> null
        WorkoutDurationType.SEND_OFF_TIME, WorkoutDurationType.CSS_SEND_OFF_TIME -> null
    }

    // interval.remindType (alert)
    const val REMIND_OFF = 0
    const val REMIND_PACE = 1
    const val REMIND_CADENCE = 2
    const val REMIND_HEART_RATE = 3
    const val REMIND_SPEED = 4
    const val REMIND_STROKE_RATE = 5

    /**
     * The bpm bounds of heart rate zone [zone], for a maximum heart rate of [maxHeartRate]. Zone 1
     * starts at 50%, and every subsequent zone is 10% wide.
     *
     * TODO: Heart rate zones should be configurable by the user app-wide.
     */
    fun heartRateZoneBounds(zone: Int, maxHeartRate: Int): IntRange? {
        if (zone < 1 || zone > 5) return null
        val low = maxHeartRate * (40 + zone * 10) / 100
        val high = maxHeartRate * (50 + zone * 10) / 100
        return low..high
    }

    const val WEIGHT_MODE_MANUAL = 1
    const val WEIGHT_MODE_RM = 3
    const val WEIGHT_MODE_BODY_WEIGHT = 5

    /**
     * The mode suffix of `strengthWeight`. Null for a type Zepp OS does not have.
     */
    fun weightMode(type: WorkoutWeightType): Int? = when (type) {
        WorkoutWeightType.MANUAL -> WEIGHT_MODE_MANUAL
        WorkoutWeightType.RM -> WEIGHT_MODE_RM
        WorkoutWeightType.BODY_WEIGHT -> WEIGHT_MODE_BODY_WEIGHT
        WorkoutWeightType.NONE, WorkoutWeightType.PERCENT_1RM -> null
    }

    /**
     * The numeric part of `strengthWeight` is the display value times 10, for a weight in kg and
     * for an RM count.
     */
    fun manualWeightRaw(grams: Int): Long = (grams / 100.0).roundToLong()

    fun rmWeightRaw(rm: Int): Long = (rm * 10).toLong()

    fun measurementSystemCode(system: WorkoutMeasurementSystem?): Int = when (system) {
        WorkoutMeasurementSystem.IMPERIAL -> 1
        else -> 0
    }

    /**
     * The Zepp OS `sportType` of [activityKind], or null when it has none.
     */
    fun sportType(activityKind: ActivityKind): Int? = when (activityKind) {
        ActivityKind.OUTDOOR_RUNNING -> 1
        ActivityKind.TREADMILL -> 8
        ActivityKind.OUTDOOR_CYCLING -> 9
        ActivityKind.INDOOR_CYCLING -> 10
        ActivityKind.JUMP_ROPING -> 21
        ActivityKind.ROWING_MACHINE -> 23
        ActivityKind.INDOOR_FITNESS -> 24
        ActivityKind.MOUNTAIN_BIKE -> 47
        ActivityKind.BMX -> 48
        ActivityKind.CORE_TRAINING -> 50
        ActivityKind.STRENGTH_TRAINING -> 52
        ActivityKind.STAIR_CLIMBER -> 108
        ActivityKind.RACE_WALKING -> 131
        else -> null
    }
}
