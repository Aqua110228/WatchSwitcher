package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.BUTTON_PRESS
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.CALORIES
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.CSS_SEND_OFF_TIME
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.DISTANCE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.HR_ABOVE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.HR_BELOW
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.POWER_ABOVE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.POWER_BELOW
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.REPS
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.SEND_OFF_TIME
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType.TIME
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEffort
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEquipment
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.ACTIVE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.COOLDOWN
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.MAIN
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.OTHER
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.RECOVER
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.REST
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType.WARMUP
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.CADENCE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.CSS_OFFSET
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.EFFORT
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.HR_RANGE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.HR_ZONE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.PACE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.POWER_FTP_RANGE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.POWER_RANGE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.POWER_ZONE
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType.SPEED
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.SportSpecBuilder
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.TargetOption
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.cadence
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.calories
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.count
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.distanceKm
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.distanceM
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.enumOf
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.heartRate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.heartRateZone
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.pace
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.percent
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.power
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.powerZone
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.range
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.secondsOffset
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.speedKmh
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.swimPace
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.time
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.weightKg
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.workoutTemplates
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts.GarminExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts.GarminExerciseLists
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object GarminWorkoutTemplateSpec {
    const val VENDOR_ID = "garmin"

    private val ALL_STEP_TYPES = listOf(WARMUP, ACTIVE, RECOVER, REST, COOLDOWN, OTHER)
    private val TIME_23H59 = 23.hours + 59.minutes + 59.seconds
    private val TIME_29H59 = 29.hours + 59.minutes + 59.seconds
    private val TIME_59M59 = 59.minutes + 59.seconds
    private val POOL_LENGTH_CUSTOM = distanceM(5.0, 200.0, 0.1)

    fun build(coordinator: GarminCoordinator, device: GBDevice): WorkoutTemplateSpec? {
        if (!coordinator.supports(device, GarminCapability.WORKOUT_DOWNLOAD)) return null

        val unsupportedFiles = GBApplication.getDevicePrefs(device).installUnsupportedFiles()

        return workoutTemplates(VENDOR_ID, GarminExerciseCatalog.INSTANCE) {
            maxTemplates = 200
            nameMaxLength = 40
            noteMaxLength = 200

            // Every sport declared through leg() is also a valid multisport leg
            val legKinds = mutableListOf<ActivityKind>()
            fun leg(activityKind: ActivityKind, block: SportSpecBuilder.() -> Unit) {
                sport(activityKind, block)
                legKinds += activityKind
            }

            if (coordinator.supports(device, GarminCapability.SPORT_RUNNING) || unsupportedFiles) {
                leg(ActivityKind.RUNNING) { runOrBike(bike = false) }
            }
            if (coordinator.supports(device, GarminCapability.SPORT_CYCLING) || unsupportedFiles) {
                leg(ActivityKind.CYCLING) { runOrBike(bike = true) }
            }
            if (coordinator.supports(device, GarminCapability.SPORT_SWIMMING) || unsupportedFiles) {
                leg(ActivityKind.POOL_SWIM) { poolSwim() }
            }
            if (coordinator.supports(device, GarminCapability.SPORT_STRENGTH) || unsupportedFiles) {
                leg(ActivityKind.STRENGTH_TRAINING) { strengthOrCardio(scaleFromBenchmark = true) }
            }
            if (coordinator.supports(device, GarminCapability.SPORT_CARDIO) || unsupportedFiles) {
                leg(ActivityKind.CARDIO) { strengthOrCardio(scaleFromBenchmark = false) }
                leg(ActivityKind.HIIT) { hiit() }
            }
            leg(ActivityKind.YOGA) {
                yogaLike(poseLabel = R.string.workout_field_yoga_pose, hasReps = false, list = GarminExerciseLists.YOGA)
            }
            leg(ActivityKind.PILATES) {
                yogaLike(poseLabel = R.string.workout_field_pilates_move, hasReps = true, list = GarminExerciseLists.PILATES)
            }
            leg(ActivityKind.MOBILITY) {
                yogaLike(poseLabel = R.string.workout_field_pilates_move, hasReps = true, list = GarminExerciseLists.MOBILITY)
            }
            leg(ActivityKind.CUSTOM) { runOrBike(bike = false) }

            if (coordinator.supports(device, GarminCapability.SPORT_TRANSITION) || unsupportedFiles) {
                sport(ActivityKind.MULTISPORT) {
                    transitions = true
                    poolLengthCustom = POOL_LENGTH_CUSTOM
                    legs(*legKinds.toTypedArray())
                }
            }
        }
    }

    private fun SportSpecBuilder.runOrBike(bike: Boolean) {
        stepTypes(*ALL_STEP_TYPES.toTypedArray())
        repeat(1, 40)

        duration(TIME, time(0.seconds, TIME_23H59, 1.seconds))
        duration(DISTANCE, distanceKm(0.0, 99999.99, 0.01))
        duration(BUTTON_PRESS)
        duration(CALORIES, calories(1, 999))
        duration(HR_ABOVE, heartRate(1, 255, default = 150))
        duration(HR_BELOW, heartRate(1, 255, default = 150))

        if (bike) {
            duration(POWER_ABOVE, power(1, 999))
            duration(POWER_BELOW, power(1, 999))
            val targets = listOf(
                TargetOption(SPEED, range(speedKmh(0.0, 9999.0, 0.1), 22, 25)),
                TargetOption(CADENCE, range(cadence(1, 9999), 80, 90)),
                TargetOption(HR_ZONE, heartRateZone()),
                TargetOption(HR_RANGE, range(heartRate(1, 255), 120, 130)),
                TargetOption(POWER_ZONE, powerZone()),
                TargetOption(POWER_RANGE, range(power(1, 1000), 190, 210)),
                TargetOption(POWER_FTP_RANGE, range(percent(1, 500), 95, 105)),
            )
            targets.forEach { target(it.type, it.value) }
            targets.forEach { secondaryTarget(it.type, it.value) }
        } else {
            target(PACE, range(pace(0.minutes, 59.minutes + 59.seconds), 5.minutes + 30.seconds, 6.minutes))
            target(SPEED, range(speedKmh(0.0, 9999.0, 0.1), 22, 25))
            target(CADENCE, range(cadence(0, 300), 175, 185))
            target(HR_ZONE, heartRateZone())
            target(HR_RANGE, range(heartRate(1, 255), 120, 130))
        }
    }

    private fun SportSpecBuilder.poolSwim() {
        activeLabel = R.string.workout_step_type_swim
        stepTypes(WARMUP, ACTIVE, MAIN, COOLDOWN, REST)
        repeat(1, 40)
        poolLengthCustom = POOL_LENGTH_CUSTOM

        duration(TIME, time(0.seconds, TIME_23H59, 1.seconds))
        duration(DISTANCE, distanceKm(0.0, 10.0, 0.01)) // TODO in pool lengths, up to 10km
        duration(BUTTON_PRESS)

        target(EFFORT, enumOf(WorkoutEffort.entries))
        target(PACE, swimPace(30.seconds, 5.minutes))
        target(CSS_OFFSET, secondsOffset(-60, 60))

        fields {
            stroke()
            drill()
            equipment(
                WorkoutEquipment.NONE,
                WorkoutEquipment.FINS,
                WorkoutEquipment.KICKBOARD,
                WorkoutEquipment.PADDLES,
                WorkoutEquipment.PULL_BUOY,
                WorkoutEquipment.SNORKEL,
            )
        }

        stepType(REST) {
            noDurations()
            duration(BUTTON_PRESS)
            duration(TIME, time(0.seconds, TIME_59M59, 1.seconds))
            duration(SEND_OFF_TIME, time(0.seconds, TIME_59M59, 1.seconds))
            duration(CSS_SEND_OFF_TIME, secondsOffset(-60, 60))
            noTargets()
            fields { }
        }
    }

    private fun SportSpecBuilder.strengthOrCardio(scaleFromBenchmark: Boolean) {
        stepTypes(*ALL_STEP_TYPES.toTypedArray())
        repeat(1, 40)

        duration(REPS, count(1, 999))
        duration(TIME, time(0.seconds, TIME_29H59, 1.seconds))
        duration(BUTTON_PRESS)
        duration(CALORIES, calories(1, 999))
        duration(HR_ABOVE, heartRate(1, 255, default = 150))
        duration(HR_BELOW, heartRate(1, 255, default = 150))

        fields {
            exercise(R.string.activity_type_exercise, GarminExerciseLists.STRENGTH)
            if (scaleFromBenchmark) {
                weight(
                    WorkoutWeightType.BODY_WEIGHT, WorkoutWeightType.MANUAL, WorkoutWeightType.PERCENT_1RM,
                    specs = mapOf(
                        WorkoutWeightType.MANUAL to weightKg(0.0, 999.9, 0.1),
                        WorkoutWeightType.PERCENT_1RM to percent(0, 100)
                    ),
                )
            } else {
                weight(
                    WorkoutWeightType.BODY_WEIGHT,
                    WorkoutWeightType.MANUAL,
                    specs = mapOf(WorkoutWeightType.MANUAL to weightKg(0.0, 999.9, 0.1))
                )
            }
        }
    }

    private fun SportSpecBuilder.hiit() {
        stepTypes(*ALL_STEP_TYPES.toTypedArray())
        repeat(1, 40)

        duration(REPS, count(1, 999))
        duration(TIME, time(0.seconds, TIME_29H59, 1.seconds))
        duration(BUTTON_PRESS)
        duration(CALORIES, calories(1, 999))
        duration(HR_ABOVE, heartRate(1, 255, default = 150))
        duration(HR_BELOW, heartRate(1, 255, default = 150))

        target(HR_ZONE, heartRateZone())

        fields {
            exercise(R.string.activity_type_exercise, GarminExerciseLists.STRENGTH)
            weight(WorkoutWeightType.MANUAL, specs = mapOf(WorkoutWeightType.MANUAL to weightKg(0.0, 999.9, 0.1)))
        }

        stepType(REST) {
            // manual weight not available on rest steps
            fields { }
        }
    }

    private fun SportSpecBuilder.yogaLike(poseLabel: Int, hasReps: Boolean, list: String) {
        stepTypes(*ALL_STEP_TYPES.toTypedArray())
        repeat(1, 40)

        if (hasReps) duration(REPS, count(1, 999))
        duration(TIME, time(0.seconds, TIME_29H59, 1.seconds))
        duration(BUTTON_PRESS)
        duration(CALORIES, calories(1, 999))
        duration(HR_ABOVE, heartRate(1, 255, default = 150))
        duration(HR_BELOW, heartRate(1, 255, default = 150))

        fields { exercise(poseLabel, list) }

        stepType(REST) {
            noDurations()
            duration(TIME, time(0.seconds, TIME_29H59, 1.seconds))
            duration(BUTTON_PRESS)
            duration(HR_ABOVE, heartRate(1, 255, default = 150))
            duration(HR_BELOW, heartRate(1, 255, default = 150))
            // no pose, reps or calories on rest steps
            fields { }
        }
    }
}
