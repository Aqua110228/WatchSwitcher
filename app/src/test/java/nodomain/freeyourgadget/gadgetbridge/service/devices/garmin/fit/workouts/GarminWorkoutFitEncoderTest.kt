package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEffort
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEquipment
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutPoolLengthUnit
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimDrill
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSwimStroke
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupportTest
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.exception.FitParseException
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private const val VENDOR_ID = "garmin"

@Suppress("SameParameterValue")
class GarminWorkoutFitEncoderTest : TestBase() {
    private fun assertEncodes(template: WorkoutTemplate, resourceName: String) {
        val fitFile = GarminWorkoutFitEncoder.encode(template, timestampSeconds = 0L)
        assertNotNull("Failed to encode $template", fitFile)
        val expected = GarminSupportTest.readTextResource(resourceName).trim()
        val actual = fitFile!!.toString().replace("}, Fit", "},\nFit").replace("}, RecordData{", "},\nRecordData{")
        assertEquals(expected, actual)
    }

    @Test
    fun testRun() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_run_full",
            activityKind = ActivityKind.RUNNING,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(45_000), noTarget),
                step(
                    WorkoutStepType.ACTIVE, distanceCm(40_000), pace(284_981, 314_961),
                    note = "CaptureNote".repeat(18) + "Ca",
                ),
                step(WorkoutStepType.ACTIVE, time(510_000), cadence(180, 185)),
                step(WorkoutStepType.ACTIVE, calories(45), cadence(164, 170)),
                step(WorkoutStepType.ACTIVE, hrAbove(120), hrZone(2)),
                step(WorkoutStepType.ACTIVE, hrBelow(165), hrRange(150, 175)),
                step(WorkoutStepType.RECOVER, buttonPress, noTarget),
                step(WorkoutStepType.REST, time(45_000), noTarget),
                step(WorkoutStepType.OTHER, time(45_000), noTarget),
                repeat(
                    3,
                    step(WorkoutStepType.ACTIVE, distanceCm(40_000), pace(464_900, 495_050)),
                    step(WorkoutStepType.RECOVER, time(45_000), noTarget),
                ),
                step(WorkoutStepType.ACTIVE, time(86_399_000), pace(284_981, 314_961)),
                step(WorkoutStepType.COOLDOWN, distanceCm(2_110_000), noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_run_full.txt")
    }

    @Test
    fun testBike() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_bike_full",
            activityKind = ActivityKind.CYCLING,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(45_000), noTarget),
                step(WorkoutStepType.ACTIVE, distanceCm(40_000), speed(5000, 5556)),
                step(
                    WorkoutStepType.ACTIVE, powerAbove(180), powerZone(2),
                    secondaryTarget = cadence(100, 105),
                ),
                step(WorkoutStepType.ACTIVE, powerBelow(320), powerRange(280, 330)),
                step(WorkoutStepType.ACTIVE, time(510_000), powerFtpRange(70, 85)),
                step(WorkoutStepType.ACTIVE, calories(45), hrZone(4)),
                step(WorkoutStepType.ACTIVE, hrAbove(120), hrRange(120, 140)),
                step(WorkoutStepType.RECOVER, buttonPress, noTarget),
                step(WorkoutStepType.REST, time(45_000), noTarget),
                step(WorkoutStepType.OTHER, time(45_000), noTarget),
                step(WorkoutStepType.COOLDOWN, distanceCm(2_110_000), noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_bike_full.txt")
    }

    @Test
    fun testHiit() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_hiit_full",
            activityKind = ActivityKind.HIIT,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(45_000), noTarget),
                step(
                    WorkoutStepType.ACTIVE, reps(8), noTarget,
                    exerciseId = "bench_press",
                ),
                step(
                    WorkoutStepType.ACTIVE, time(510_000), hrZone(2),
                    exerciseId = "squat/barbell_back_squat",
                ),
                step(
                    WorkoutStepType.ACTIVE, buttonPress, noTarget,
                    exerciseId = "pull_up/pull_up",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, calories(45), noTarget,
                    exerciseId = "plank/plank",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 87_500,
                ),
                step(
                    WorkoutStepType.ACTIVE, hrAbove(120), hrZone(4),
                    exerciseId = "lunge/dumbbell_lunge",
                ),
                step(WorkoutStepType.RECOVER, time(45_000), noTarget),
                step(WorkoutStepType.REST, time(45_000), noTarget),
                step(
                    WorkoutStepType.OTHER, time(45_000), noTarget,
                    exerciseId = "bench_press",
                ),
                step(WorkoutStepType.COOLDOWN, time(45_000), noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_hiit_full.txt")
    }

    @Test
    fun testYoga() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_yoga_full",
            activityKind = ActivityKind.YOGA,
            steps = mutableListOf(
                // TODO warmup with pose?
                step(WorkoutStepType.WARMUP, time(45_000), noTarget),
                step(
                    // TODO Garmin Connect writes exercise_weight=0 on every pose step?
                    WorkoutStepType.ACTIVE, time(510_000), noTarget,
                    exerciseId = "pose/downward_facing_dog",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, buttonPress, noTarget,
                    exerciseId = "pose/warrior_two",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, calories(45), noTarget,
                    exerciseId = "pose/tree",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, hrAbove(120), noTarget,
                    exerciseId = "pose/childs",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, hrBelow(165), noTarget,
                    exerciseId = "pose/baby_cobra",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(WorkoutStepType.RECOVER, time(45_000), noTarget),
                step(WorkoutStepType.REST, time(45_000), noTarget),
                step(WorkoutStepType.ACTIVE, time(45_000), noTarget), // "Other", no pose set
                step(WorkoutStepType.COOLDOWN, time(45_000), noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_yoga_full.txt")
    }

    @Test
    fun testPilates() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_pilates_full",
            activityKind = ActivityKind.PILATES,
            steps = mutableListOf(
                // TODO with a move?
                step(WorkoutStepType.WARMUP, time(45_000), noTarget),
                step(
                    // TODO Garmin Connect writes exercise_weight=0 on every move step?
                    WorkoutStepType.ACTIVE, time(510_000), noTarget,
                    exerciseId = "core/the_hundred",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, buttonPress, noTarget,
                    exerciseId = "core/roll_up",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, calories(45), noTarget,
                    exerciseId = "core/single_leg_circles",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, hrAbove(120), noTarget,
                    exerciseId = "move/saw",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, hrBelow(165), noTarget,
                    exerciseId = "core/teaser",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                // TODO with a move?
                step(WorkoutStepType.RECOVER, time(45_000), noTarget),
                step(WorkoutStepType.REST, time(45_000), noTarget),
                step(WorkoutStepType.ACTIVE, time(45_000), noTarget), // "Other", no move set
                step(
                    WorkoutStepType.ACTIVE, reps(8), noTarget,
                    exerciseId = "core/the_hundred",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(WorkoutStepType.COOLDOWN, time(45_000), noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_pilates_full.txt")
    }

    @Test
    fun testStrength() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_strength_full",
            activityKind = ActivityKind.STRENGTH_TRAINING,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(45_000), noTarget),
                repeat(
                    4,
                    step(
                        WorkoutStepType.ACTIVE, reps(8), noTarget,
                        exerciseId = "lunge/dumbbell_lunge",
                        weightType = WorkoutWeightType.MANUAL, weightValue = 20_000,
                    ),
                    step(WorkoutStepType.RECOVER, time(45_000), noTarget),
                ),
                step(
                    WorkoutStepType.ACTIVE, reps(8), noTarget,
                    exerciseId = "bench_press",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, reps(45), noTarget,
                    exerciseId = "squat/barbell_back_squat",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 62_500,
                ),
                step(
                    WorkoutStepType.ACTIVE, time(45_000), noTarget,
                    exerciseId = "pull_up/pull_up",
                ),
                step(
                    WorkoutStepType.ACTIVE, time(510_000), noTarget,
                    exerciseId = "plank/plank",
                ),
                step(
                    WorkoutStepType.ACTIVE, buttonPress, noTarget,
                    exerciseId = "bench_press/barbell_bench_press",
                    weightType = WorkoutWeightType.PERCENT_1RM, weightValue = 75,
                ),
                step(
                    WorkoutStepType.ACTIVE, calories(45), noTarget,
                    exerciseId = "bench_press/barbell_bench_press",
                    weightType = WorkoutWeightType.PERCENT_1RM, weightValue = 90,
                ),
                step(
                    WorkoutStepType.ACTIVE, hrAbove(120), noTarget,
                    exerciseId = "squat/barbell_back_squat",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 20_000,
                ),
                step(WorkoutStepType.RECOVER, time(45_000), noTarget),
                step(WorkoutStepType.REST, time(45_000), noTarget),
                step(
                    WorkoutStepType.OTHER, time(45_000), noTarget,
                    exerciseId = "bench_press",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(WorkoutStepType.COOLDOWN, time(45_000), noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_strength_full.txt")
    }

    @Test
    fun testCardio() {
        val dumbbellLungeReps8 = step(
            WorkoutStepType.ACTIVE, reps(8), noTarget,
            exerciseId = "lunge/dumbbell_lunge",
            weightType = WorkoutWeightType.MANUAL, weightValue = 20_000,
        )
        // TODO no target? UI allows it
        val recover = step(WorkoutStepType.RECOVER, time(45_000), noTarget)

        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_cardio_full",
            activityKind = ActivityKind.CARDIO,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(45_000), noTarget),
                step(
                    WorkoutStepType.ACTIVE, reps(8), noTarget,
                    exerciseId = "bench_press",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                step(
                    WorkoutStepType.ACTIVE, reps(45), noTarget,
                    exerciseId = "squat/barbell_back_squat",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 62_500,
                ),
                step(
                    WorkoutStepType.ACTIVE, time(45_000), noTarget,
                    exerciseId = "pull_up/pull_up",
                ),
                step(
                    WorkoutStepType.ACTIVE, time(510_000), noTarget,
                    exerciseId = "plank/plank",
                ),
                step(
                    WorkoutStepType.ACTIVE, buttonPress, noTarget,
                    exerciseId = "lunge/dumbbell_lunge",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 45_000,
                ),
                step(
                    WorkoutStepType.ACTIVE, calories(45), noTarget,
                    exerciseId = "bench_press",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 80_000,
                ),
                step(
                    WorkoutStepType.ACTIVE, hrAbove(120), noTarget,
                    exerciseId = "squat/barbell_back_squat",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 20_000,
                ),
                // TODO no target? UI allows it
                step(WorkoutStepType.RECOVER, time(45_000), noTarget),
                step(WorkoutStepType.REST, time(45_000), noTarget),
                // TODO "Other" ?
                step(
                    WorkoutStepType.ACTIVE, time(45_000), noTarget,
                    exerciseId = "bench_press",
                    weightType = WorkoutWeightType.MANUAL, weightValue = 0,
                ),
                repeat(4, dumbbellLungeReps8, recover),
                step(WorkoutStepType.COOLDOWN, time(45_000), noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_cardio_full.txt")
    }

    @Test
    fun testMobility() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_mobility_full",
            activityKind = ActivityKind.MOBILITY,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(60_000), noTarget),
                step(WorkoutStepType.ACTIVE, reps(12), noTarget, exerciseId = "pose/childs"),
                step(WorkoutStepType.ACTIVE, time(45_000), noTarget, exerciseId = "move/arm_stretch"),
                step(WorkoutStepType.ACTIVE, reps(10), noTarget, exerciseId = "warm_up/hip_circles"),
                step(WorkoutStepType.COOLDOWN, buttonPress, noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_mobility_full.txt")
    }

    @Test
    fun testCustom() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_custom_full",
            activityKind = ActivityKind.CUSTOM,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(300_000), noTarget),
                step(WorkoutStepType.ACTIVE, distanceCm(500_000), speed(200, 250)),
                step(WorkoutStepType.COOLDOWN, buttonPress, noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_custom_full.txt")
    }

    /**
     * A secondary target with an offset (heart rate, power) uses the same offsets as a primary one.
     */
    @Test
    fun testBikeSecondary() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_bike_secondary",
            activityKind = ActivityKind.CYCLING,
            steps = mutableListOf(
                step(WorkoutStepType.WARMUP, time(300_000), noTarget),
                step(WorkoutStepType.ACTIVE, time(600_000), cadence(85, 95), secondaryTarget = hrRange(120, 140)),
                step(WorkoutStepType.ACTIVE, time(600_000), cadence(85, 95), secondaryTarget = powerRange(280, 330)),
                step(WorkoutStepType.ACTIVE, time(600_000), cadence(85, 95), secondaryTarget = hrZone(3)),
                step(WorkoutStepType.COOLDOWN, buttonPress, noTarget),
            ),
        )

        assertEncodes(template, "/garmin/garmin_bike_secondary.txt")
    }

    /**
     * Every stroke, drill and equipment value, and the four rest durations.
     */
    @Test
    fun testSwim() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_swim_full",
            activityKind = ActivityKind.POOL_SWIM,
            poolLengthUnit = WorkoutPoolLengthUnit.POOL_25M,
            steps = mutableListOf(
                swim(WorkoutStepType.WARMUP, distanceCm(40_000), WorkoutSwimStroke.FREESTYLE),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.CHOICE),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.BACKSTROKE),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.BREASTSTROKE),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.DRILL),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.BUTTERFLY),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.FREESTYLE),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.INDIVIDUAL_MEDLEY),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.MIXED),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.IM_BY_ROUND),
                swim(WorkoutStepType.MAIN, distanceCm(10_000), WorkoutSwimStroke.REVERSE_IM_ORDER),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    drill = WorkoutSwimDrill.KICK
                ),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    drill = WorkoutSwimDrill.PULL
                ),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    drill = WorkoutSwimDrill.DRILL
                ),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    equipment = WorkoutEquipment.FINS
                ),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    equipment = WorkoutEquipment.KICKBOARD
                ),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    equipment = WorkoutEquipment.PADDLES
                ),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    equipment = WorkoutEquipment.PULL_BUOY
                ),
                swim(
                    WorkoutStepType.MAIN,
                    distanceCm(10_000),
                    WorkoutSwimStroke.FREESTYLE,
                    equipment = WorkoutEquipment.SNORKEL
                ),
                step(WorkoutStepType.REST, time(30_000)),
                step(WorkoutStepType.REST, sendOffTime(120_000)),
                step(WorkoutStepType.REST, cssSendOffTime(10)),
                swim(WorkoutStepType.COOLDOWN, distanceCm(20_000), WorkoutSwimStroke.FREESTYLE),
            ),
        )

        assertEncodes(template, "/garmin/garmin_swim_full.txt")
    }

    /**
     * The target of a swim step is its secondary target. The primary one is the stroke.
     */
    @Test
    fun testSwimEffort() {
        fun rest() = step(WorkoutStepType.REST, buttonPress)
        fun main(
            target: WorkoutTarget,
            note: String? = null,
            drill: WorkoutSwimDrill? = null,
            equipment: WorkoutEquipment? = null
        ) =
            swim(WorkoutStepType.MAIN, distanceCm(20_000), WorkoutSwimStroke.FREESTYLE, target, note, drill, equipment)

        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_swim_effort",
            activityKind = ActivityKind.POOL_SWIM,
            poolLengthUnit = WorkoutPoolLengthUnit.POOL_25M,
            steps = mutableListOf(
                swim(WorkoutStepType.WARMUP, distanceCm(40_000), WorkoutSwimStroke.FREESTYLE),
                rest(),
                repeat(
                    8,
                    swim(
                        WorkoutStepType.MAIN,
                        distanceCm(10_000),
                        WorkoutSwimStroke.FREESTYLE,
                        effort(WorkoutEffort.ASCENDING)
                    ),
                    step(WorkoutStepType.REST, time(15_000)),
                ),
                rest(),
                main(swimPace(120_000), note = "target pace = 2 min / 100m"),
                rest(),
                main(cssOffset(-15), note = "css-based target pace = -15s"),
                rest(),
                main(noTarget, note = "drill = kick", drill = WorkoutSwimDrill.KICK),
                rest(),
                main(noTarget, note = "equipment = fins", equipment = WorkoutEquipment.FINS),
                rest(),
                main(effort(WorkoutEffort.RECOVERY), note = "effort = recovery"),
                rest(),
                main(effort(WorkoutEffort.EASY), note = "effort = easy"),
                rest(),
                main(effort(WorkoutEffort.MODERATE), note = "effort = moderate"),
                rest(),
                main(effort(WorkoutEffort.HARD), note = "effort = hard"),
                rest(),
                main(effort(WorkoutEffort.VERY_HARD), note = "effort = very hard"),
                rest(),
                main(effort(WorkoutEffort.ALL_OUT), note = "effort = all out"),
                rest(),
                swim(
                    WorkoutStepType.COOLDOWN,
                    distanceCm(20_000),
                    WorkoutSwimStroke.CHOICE,
                    effort(WorkoutEffort.DESCENDING)
                ),
            ),
        )

        assertEncodes(template, "/garmin/garmin_swim_effort.txt")
    }

    @Test
    fun testSwimCustomPool() {
        val template = WorkoutTemplate(
            vendorId = VENDOR_ID,
            name = "garmin_swim_custom_pool",
            activityKind = ActivityKind.POOL_SWIM,
            poolLengthUnit = WorkoutPoolLengthUnit.CUSTOM,
            poolLength = 2250,
            steps = mutableListOf(
                swim(WorkoutStepType.WARMUP, distanceCm(10_000), WorkoutSwimStroke.FREESTYLE),
                swim(WorkoutStepType.COOLDOWN, distanceCm(10_000), WorkoutSwimStroke.FREESTYLE),
            ),
        )

        assertEncodes(template, "/garmin/garmin_swim_custom_pool.txt")
    }

    @Test
    fun testMultisport() {
        assertEncodes(multisport("garmin_multi_on", transitions = true), "/garmin/garmin_multi_on.txt")
        assertEncodes(multisport("garmin_multi_off", transitions = false), "/garmin/garmin_multi_off.txt")
    }

    private fun multisport(name: String, transitions: Boolean) = WorkoutTemplate(
        vendorId = VENDOR_ID,
        name = name,
        activityKind = ActivityKind.MULTISPORT,
        poolLengthUnit = WorkoutPoolLengthUnit.POOL_25M,
        transitions = transitions,
        steps = mutableListOf(
            leg(
                ActivityKind.RUNNING,
                step(WorkoutStepType.WARMUP, time(300_000), noTarget),
                step(WorkoutStepType.ACTIVE, distanceCm(200_000), noTarget),
            ),
            leg(
                ActivityKind.CYCLING,
                step(WorkoutStepType.WARMUP, time(300_000), noTarget),
                step(WorkoutStepType.ACTIVE, distanceCm(200_000), noTarget),
            ),
            leg(
                ActivityKind.POOL_SWIM,
                swim(WorkoutStepType.WARMUP, time(300_000), WorkoutSwimStroke.FREESTYLE),
                swim(WorkoutStepType.ACTIVE, distanceCm(40_000), WorkoutSwimStroke.FREESTYLE),
            ),
        ),
    )

    //
    // Small DSL to simplify the templates above
    //

    private fun step(
        type: WorkoutStepType,
        duration: WorkoutDuration? = null,
        target: WorkoutTarget? = null,
        secondaryTarget: WorkoutTarget? = null,
        note: String? = null,
        exerciseId: String? = null,
        weightType: WorkoutWeightType? = null,
        weightValue: Int? = null,
    ): WorkoutStepNode = WorkoutStepNode.newStep(type).apply {
        this.duration = duration
        this.target = target
        this.secondaryTarget = secondaryTarget
        this.note = note
        this.exerciseId = exerciseId
        this.weightType = weightType
        this.weightValue = weightValue
    }

    private fun swim(
        type: WorkoutStepType,
        duration: WorkoutDuration,
        stroke: WorkoutSwimStroke,
        target: WorkoutTarget = noTarget,
        note: String? = null,
        drill: WorkoutSwimDrill? = null,
        equipment: WorkoutEquipment? = null,
    ): WorkoutStepNode = step(type, duration, target, note = note).apply {
        this.swimStroke = stroke
        this.swimDrill = drill
        this.swimEquipment = equipment
    }

    private fun repeat(count: Int, vararg children: WorkoutStepNode): WorkoutStepNode =
        WorkoutStepNode.newRepeat(count).apply { this.children = children.toMutableList() }

    private fun leg(activityKind: ActivityKind, vararg children: WorkoutStepNode): WorkoutStepNode =
        WorkoutStepNode.newLeg(activityKind).apply { this.children = children.toMutableList() }

    private fun time(ms: Long) = WorkoutDuration(WorkoutDurationType.TIME, ms)
    private fun distanceCm(cm: Long) = WorkoutDuration(WorkoutDurationType.DISTANCE, cm)
    private fun calories(kcal: Long) = WorkoutDuration(WorkoutDurationType.CALORIES, kcal)
    private fun reps(count: Long) = WorkoutDuration(WorkoutDurationType.REPS, count)
    private fun hrAbove(bpm: Long) = WorkoutDuration(WorkoutDurationType.HR_ABOVE, bpm)
    private fun hrBelow(bpm: Long) = WorkoutDuration(WorkoutDurationType.HR_BELOW, bpm)
    private fun powerAbove(watts: Long) = WorkoutDuration(WorkoutDurationType.POWER_ABOVE, watts)
    private fun powerBelow(watts: Long) = WorkoutDuration(WorkoutDurationType.POWER_BELOW, watts)
    private fun sendOffTime(ms: Long) = WorkoutDuration(WorkoutDurationType.SEND_OFF_TIME, ms)
    private fun cssSendOffTime(seconds: Long) = WorkoutDuration(WorkoutDurationType.CSS_SEND_OFF_TIME, seconds)

    private val buttonPress = WorkoutDuration(WorkoutDurationType.BUTTON_PRESS)

    private val noTarget = WorkoutTarget.NONE

    private fun pace(lowMsPerKm: Long, highMsPerKm: Long) =
        WorkoutTarget(WorkoutTargetType.PACE, low = lowMsPerKm, high = highMsPerKm)

    private fun speed(lowMmS: Long, highMmS: Long) =
        WorkoutTarget(WorkoutTargetType.SPEED, low = lowMmS, high = highMmS)

    private fun cadence(low: Long, high: Long) =
        WorkoutTarget(WorkoutTargetType.CADENCE, low = low, high = high)

    private fun hrZone(zone: Int) = WorkoutTarget(WorkoutTargetType.HR_ZONE, zone = zone)
    private fun hrRange(low: Long, high: Long) =
        WorkoutTarget(WorkoutTargetType.HR_RANGE, low = low, high = high)

    private fun powerZone(zone: Int) = WorkoutTarget(WorkoutTargetType.POWER_ZONE, zone = zone)
    private fun powerRange(low: Long, high: Long) =
        WorkoutTarget(WorkoutTargetType.POWER_RANGE, low = low, high = high)

    private fun powerFtpRange(low: Long, high: Long) =
        WorkoutTarget(WorkoutTargetType.POWER_FTP_RANGE, low = low, high = high)

    private fun effort(effort: WorkoutEffort) = WorkoutTarget(WorkoutTargetType.EFFORT, enumValue = effort.name)
    private fun swimPace(msPer100m: Long) = WorkoutTarget(WorkoutTargetType.PACE, low = msPer100m)
    private fun cssOffset(seconds: Long) = WorkoutTarget(WorkoutTargetType.CSS_OFFSET, low = seconds)

    //
    // Auxiliary txt generator from FIT
    //

    @Test
    @Ignore("helper test for development, remove this while debugging")
    @Throws(FitParseException::class, IOException::class)
    fun fitToTxt() {
        generateTxt("garmin_pilates_full")
        generateTxt("garmin_cardio_full")
        generateTxt("garmin_yoga_full")
        generateTxt("garmin_hiit_full")
        generateTxt("garmin_bike_full")
        generateTxt("garmin_run_full")
        generateTxt("garmin_strength_full")
        generateTxt("garmin_swim_full")
        generateTxt("garmin_swim_effort")
        generateTxt("garmin_swim_custom_pool")
        generateTxt("garmin_multi_on")
        generateTxt("garmin_multi_off")
        generateTxt("garmin_custom_full")
        generateTxt("garmin_mobility_full")
        generateTxt("garmin_bike_secondary")
    }

    fun generateTxt(filename: String?) {
        val fileContents = GarminSupportTest.readBinaryResource("/garmin/${filename}.fit")
        val fitFile = FitFile.parseIncoming(fileContents)
        val actualOutput = fitFile.toString().replace("}, Fit", "},\nFit").replace("}, RecordData{", "},\nRecordData{")
        Files.write(
            Path.of("src/test/resources/garmin/$filename.txt"),
            actualOutput.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
