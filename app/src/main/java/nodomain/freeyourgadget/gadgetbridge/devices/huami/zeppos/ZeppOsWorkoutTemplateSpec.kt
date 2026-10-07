package nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.BUTTON_PRESS
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.CALORIES
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.DISTANCE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.HR_ABOVE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.HR_BELOW
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.LAPS
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.REPS
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.STROKES
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.TIME
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.ACTIVE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.COOLDOWN
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.RECOVER
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.REST
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.WARMUP
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.CADENCE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.HR_RANGE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.HR_ZONE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.PACE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.SPEED
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.STROKE_RATE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.SportSpecBuilder
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.cadence
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.calories
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.count
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.distanceKm
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.heartRate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.heartRateZone
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.pace
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.range
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.speedKmh
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.strokeRate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.time
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.weightKg
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.workoutTemplates
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts.ZeppOsExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts.ZeppOsExerciseLists
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object ZeppOsWorkoutTemplateSpec {
    const val VENDOR_ID = "zeppos"

    private val TIME_RANGE = time(10.seconds, 9.hours + 59.minutes + 55.seconds, 5.seconds)
    private val CALORIES_RANGE = calories(5, 995, 5)
    private val HEART_RATE_RANGE = heartRate(81, 163, default = 120)
    private val PACE_RANGE = range(pace(2.minutes, 14.minutes + 59.seconds), 5.minutes + 30.seconds, 6.minutes)
    private val SPEED_RANGE = range(speedKmh(5.0, 100.0, 1.0), 22, 25)
    private val RUN_CADENCE_RANGE = range(cadence(120, 240), 175, 185)

    fun build(): WorkoutTemplateSpec {
        return workoutTemplates(VENDOR_ID, ZeppOsExerciseCatalog.INSTANCE) {
            maxTemplates = 50
            nameMaxLength = 20
            noteMaxLength = 20

            // TODO: Disabled since we have no captures to know how to encode track run
            // sport(ActivityKind.TRACK_RUN) { runningOnPlayground() } // "Running on playground"
            sport(ActivityKind.RACE_WALKING) { runningOnPlayground(laps = false) }
            sport(ActivityKind.BMX) { bikeLike() }
            sport(ActivityKind.MOUNTAIN_BIKE) { bikeLike() }
            sport(ActivityKind.ROWING_MACHINE) { rowingMachine() }
            sport(ActivityKind.STAIR_CLIMBER) { hrOnly() }
            sport(ActivityKind.JUMP_ROPING) { jumpRope() }
            // TODO: Disabled since we have no captures to know how to encode pool swim
            // sport(ActivityKind.POOL_SWIM) { poolSwimming() }
            sport(ActivityKind.CORE_TRAINING) { hrOnly() }
            sport(ActivityKind.STRENGTH_TRAINING) { strengthTraining() }
            sport(ActivityKind.INDOOR_FITNESS) { hrOnly() }
            sport(ActivityKind.INDOOR_CYCLING) { hrOnly() }
            sport(ActivityKind.OUTDOOR_CYCLING) { outdoorCycling() }
            sport(ActivityKind.TREADMILL) { runLike(hasDistance = false) }
            sport(ActivityKind.OUTDOOR_RUNNING) { runLike(hasDistance = true) }
        }
    }

    /**
     * The step types, repeat range, note length, unit field and heart rate options shared by every
     * sport.
     */
    private fun SportSpecBuilder.common() {
        stepTypes(WARMUP, ACTIVE, RECOVER, REST, COOLDOWN)
        repeat(2, 30)
        stepNoteMaxLength = 40
        fields { unit() }
        duration(BUTTON_PRESS)
        duration(HR_ABOVE, HEART_RATE_RANGE)
        duration(HR_BELOW, HEART_RATE_RANGE)
        target(HR_ZONE, heartRateZone())
        target(HR_RANGE, range(HEART_RATE_RANGE, 120, 130))
    }

    private fun SportSpecBuilder.runningOnPlayground(laps: Boolean = true) {
        common()
        duration(DISTANCE, distanceKm(0.10, 99.99, 0.01))
        if (laps) duration(LAPS, count(1, 99))
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
        target(PACE, PACE_RANGE)
        target(CADENCE, RUN_CADENCE_RANGE)
    }

    private fun SportSpecBuilder.bikeLike() {
        common()
        duration(DISTANCE, distanceKm(0.10, 99.99, 0.01))
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
        target(SPEED, SPEED_RANGE)
    }

    private fun SportSpecBuilder.rowingMachine() {
        common()
        duration(STROKES, count(5, 1000, 5))
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
        target(STROKE_RATE, range(strokeRate(15, 80)))
    }

    /**
     * Time, calories and heart rate only.
     */
    private fun SportSpecBuilder.hrOnly() {
        common()
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
    }

    private fun SportSpecBuilder.jumpRope() {
        common()
        duration(REPS, count(50, 5000, 50))
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
    }

    private fun SportSpecBuilder.poolSwimming() {
        stepTypes(WARMUP, ACTIVE, RECOVER, REST, COOLDOWN)
        repeat(2, 30)
        stepNoteMaxLength = 40
        fields { unit() }
        duration(BUTTON_PRESS)
        duration(LAPS, count(1, 99)) // "by trip"
        duration(TIME, TIME_RANGE)
        // Pool swimming has no alerts, so no target
    }

    private fun SportSpecBuilder.strengthTraining() {
        common()
        duration(REPS, count(1, 999))
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
        fields {
            unit()
            exercise(R.string.workout_field_action_name, ZeppOsExerciseLists.STRENGTH)
            weight(
                WorkoutWeightType.BODY_WEIGHT, WorkoutWeightType.MANUAL, WorkoutWeightType.RM,
                specs = mapOf(
                    WorkoutWeightType.MANUAL to weightKg(0.0, 999.5, 0.5),
                    WorkoutWeightType.RM to count(1, 50)
                ),
            )
        }
    }

    private fun SportSpecBuilder.outdoorCycling() {
        common()
        duration(DISTANCE, distanceKm(0.10, 99.99, 0.01))
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
        target(SPEED, SPEED_RANGE)
        target(CADENCE, range(cadence(120, 240), 80, 90))
    }

    /**
     * Pace and cadence targets. [hasDistance] adds the distance duration.
     */
    private fun SportSpecBuilder.runLike(hasDistance: Boolean) {
        common()
        if (hasDistance) duration(DISTANCE, distanceKm(0.10, 99.99, 0.01))
        duration(TIME, TIME_RANGE)
        duration(CALORIES, CALORIES_RANGE)
        target(PACE, PACE_RANGE)
        target(CADENCE, RUN_CADENCE_RANGE)
    }
}
