package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutMeasurementSystem
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import java.time.LocalDate
import kotlin.math.roundToLong

@Suppress("SameParameterValue")
class ZeppOsWorkoutTemplateEncoderTest : TestBase() {
    /**
     * Set user age to match the HR zones in the captures.
     */
    @Before
    fun setUserAge() {
        GBApplication.getPrefs().preferences.edit()
            .putString(ActivityUser.PREF_USER_DATE_OF_BIRTH, LocalDate.now().minusYears(58).toString())
            .commit()
    }

    private fun workoutJson(name: String): JSONObject {
        val stream = javaClass.getResourceAsStream("/zepp_os/$name.json")
        assertNotNull("Missing workout json for '$name'", stream)
        return JSONObject(stream!!.bufferedReader(Charsets.UTF_8).readText())
    }

    /**
     * Encodes [template] and asserts whether it matches [workoutJson], field by field.
     *
     * @param ignoredRepsIndexes interval indices where the capture's `intervalVal` is `0`;
     * those intervals' `intervalVal` is not compared.
     * @param ignoredWeightTypeIndexes interval indices where the capture's `selfWeightType` is
     * `1`. It was only seen on one capture, Dynamic Plank, and the encoder always writes `0`, so
     * those intervals' `selfWeightType` is not compared.
     */
    private fun assertEncodesTo(
        template: WorkoutTemplate,
        workoutJson: String,
        ignoredRepsIndexes: Set<Int> = emptySet(),
        ignoredWeightTypeIndexes: Set<Int> = emptySet(),
    ) {
        val expected = workoutJson(workoutJson)
        val encoded = ZeppOsWorkoutTemplateEncoder.encode(template, templateId = expected.getLong("templateId"))
        assertNotNull("Failed to encode $workoutJson", encoded)
        assertEquals(expected.getInt("sportType"), encoded!!.getInt("sportType"))
        assertEquals(expected.getLong("templateId"), encoded.getLong("templateId"))
        assertEquals(expected.getString("templateName"), encoded.getString("templateName"))

        val expectedIntervals = expected.getJSONArray("intervals")
        val actualIntervals = encoded.getJSONArray("intervals")
        assertEquals("interval count", expectedIntervals.length(), actualIntervals.length())
        for (i in 0 until expectedIntervals.length()) {
            val e = expectedIntervals.getJSONObject(i)
            val a = actualIntervals.getJSONObject(i)
            val expectedKeys = e.keys().asSequence().map { it as String }.toSet()
            val actualKeys = a.keys().asSequence().map { it as String }.toSet()
            assertEquals("interval $i keys", expectedKeys, actualKeys)
            for (key in expectedKeys) {
                if (key == "intervalVal" && i in ignoredRepsIndexes) continue
                if (key == "selfWeightType" && i in ignoredWeightTypeIndexes) continue
                assertEquals("interval $i .$key", e.get(key).toString(), a.get(key).toString())
            }
        }
    }

    @Test
    fun testBmx() {
        val template = template(
            "BMX", ActivityKind.BMX,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, distanceM(500), speedKmh(10, 15)),
            step(WorkoutStepType.ACTIVE, calories(620), hrZone(2)),
            step(WorkoutStepType.ACTIVE, hrAboveDur(100), speedKmh(30, 40)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "BMX")
    }

    @Test
    fun testMountainBiking() {
        val template = template(
            "Mountain Biking", ActivityKind.MOUNTAIN_BIKE,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, distanceM(500), speedKmh(10, 15)),
            step(WorkoutStepType.ACTIVE, calories(620), hrZone(2)),
            step(WorkoutStepType.ACTIVE, hrAboveDur(100), speedKmh(30, 40)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Mountain Biking")
    }

    @Test
    fun testCoreTraining() {
        val template = template(
            "Core Training", ActivityKind.CORE_TRAINING,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, calories(45), hrZone(2)),
            step(WorkoutStepType.ACTIVE, hrBelowDur(150), hrRange(110, 140)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Core Training")
    }

    @Test
    fun testIndoorCycling() {
        val template = template(
            "Indoor Cycling", ActivityKind.INDOOR_CYCLING,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, calories(45), hrZone(2)),
            step(WorkoutStepType.ACTIVE, hrBelowDur(150), hrRange(110, 140)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Indoor Cycling")
    }

    @Test
    fun testIndoorFitness() {
        val template = template(
            "Indoor Fitness", ActivityKind.INDOOR_FITNESS,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, calories(45), hrZone(2)),
            step(WorkoutStepType.ACTIVE, hrBelowDur(150), hrRange(110, 140)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Indoor Fitness")
    }

    @Test
    fun testJumpRope() {
        val template = template(
            "Jump Rope", ActivityKind.JUMP_ROPING,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, reps(150), hrZone(2)),
            step(WorkoutStepType.ACTIVE, calories(620), hrRange(110, 140)),
            step(WorkoutStepType.ACTIVE, hrAboveDur(100), noAlerts),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Jump Rope")
    }

    @Test
    fun testOutdoorRunning() {
        val template = template(
            "Outdoor Running", ActivityKind.OUTDOOR_RUNNING,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, distanceM(500), pace(330, 390)),
            step(WorkoutStepType.ACTIVE, calories(620), cadence(150, 165)),
            step(WorkoutStepType.ACTIVE, hrAboveDur(100), hrZone(2)),
            step(WorkoutStepType.RECOVER, distanceM(15_200), pace(480, 570)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            repeat(
                3,
                step(WorkoutStepType.ACTIVE, distanceM(500), cadence(170, 190)),
                step(WorkoutStepType.RECOVER, time(45), noAlerts),
            ),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Outdoor Running")
    }

    @Test
    fun testOutdoorCycling() {
        val template = template(
            "Outdoor Cycling", ActivityKind.OUTDOOR_CYCLING,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, distanceM(500), speedKmh(10, 15)),
            step(WorkoutStepType.ACTIVE, calories(620), speedKmh(30, 40)),
            step(WorkoutStepType.ACTIVE, hrAboveDur(100), hrZone(2)),
            step(WorkoutStepType.ACTIVE, distanceM(15_200), noAlerts),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Outdoor Cycling")
    }

    @Test
    fun testRaceWalking() {
        val template = template(
            "Race Walking", ActivityKind.RACE_WALKING,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, distanceM(500), pace(330, 390)),
            step(WorkoutStepType.ACTIVE, calories(620), hrZone(2)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.RECOVER, hrAboveDur(100), hrRange(110, 140)),
            repeat(
                3,
                step(WorkoutStepType.ACTIVE, distanceM(15_200), pace(480, 570)),
                step(WorkoutStepType.RECOVER, time(45), noAlerts),
            ),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Race Walking")
    }

    @Test
    fun testRowingMachine() {
        val template = template(
            "Rowing Machine", ActivityKind.ROWING_MACHINE,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, strokes(25), strokeRate(20, 30)),
            step(WorkoutStepType.ACTIVE, calories(620), hrZone(2)),
            step(WorkoutStepType.ACTIVE, hrAboveDur(100), strokeRate(45, 60)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Rowing Machine")
    }

    @Test
    fun testStairClimbing() {
        val template = template(
            "Stair Climbing", ActivityKind.STAIR_CLIMBER,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, calories(45), hrZone(2)),
            step(WorkoutStepType.ACTIVE, hrBelowDur(150), hrRange(110, 140)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Stair Climbing")
    }

    @Test
    fun testTreadmill() {
        val template = template(
            "Treadmill", ActivityKind.TREADMILL,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(WorkoutStepType.ACTIVE, calories(45), pace(330, 390)),
            step(WorkoutStepType.ACTIVE, hrAboveDur(100), cadence(150, 165)),
            step(WorkoutStepType.REST, skipButton, noAlerts),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(template, "Treadmill")
    }

    @Test
    fun testStrengthTraining() {
        val squatRepsWeight = step(
            WorkoutStepType.ACTIVE, reps(8), noAlerts,
            exerciseId = "barbell_squats",
            weightType = WorkoutWeightType.MANUAL, weightValue = 22_500,
        )
        val rest = step(WorkoutStepType.REST, skipButton, noAlerts)

        val template = template(
            "Strength Training", ActivityKind.STRENGTH_TRAINING,
            step(WorkoutStepType.WARMUP, time(45), noAlerts),
            step(
                WorkoutStepType.ACTIVE, reps(8), noAlerts,
                exerciseId = "bench_press",
                weightType = WorkoutWeightType.MANUAL, weightValue = 22_500,
            ),
            step(
                WorkoutStepType.ACTIVE, reps(45), hrZone(2),
                exerciseId = "barbell_squats",
                weightType = WorkoutWeightType.MANUAL, weightValue = 87_500,
            ),
            step(
                WorkoutStepType.ACTIVE, time(1535), noAlerts,
                exerciseId = "eccentric_pull_up",
                weightType = WorkoutWeightType.BODY_WEIGHT,
            ),
            step(
                WorkoutStepType.ACTIVE, calories(45), hrRange(110, 140),
                exerciseId = "dynamic_plank",
                weightType = WorkoutWeightType.BODY_WEIGHT,
            ),
            step(
                WorkoutStepType.ACTIVE, hrAboveDur(100), noAlerts,
                exerciseId = "dumbbell_lunge_swing",
                weightType = WorkoutWeightType.RM, weightValue = 8,
            ),
            step(
                WorkoutStepType.ACTIVE, skipButton, noAlerts,
                exerciseId = "bench_press",
                weightType = WorkoutWeightType.RM, weightValue = 35,
            ),
            rest,
            repeat(5, squatRepsWeight, rest),
            step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
        )
        assertEncodesTo(
            template, "Strength Training",
            ignoredRepsIndexes = setOf(1, 2, 8),
            ignoredWeightTypeIndexes = setOf(4),
        )
    }

    //
    // Small DSL to simplify the templates above
    //

    private fun template(name: String, activityKind: ActivityKind, vararg steps: WorkoutStepNode): WorkoutTemplate =
        WorkoutTemplate(vendorId = "zeppos", name = name, activityKind = activityKind, steps = steps.toMutableList())

    private fun step(
        type: WorkoutStepType,
        duration: WorkoutDuration,
        target: WorkoutTarget? = null,
        exerciseId: String? = null,
        weightType: WorkoutWeightType? = null,
        weightValue: Int? = null,
    ): WorkoutStepNode = WorkoutStepNode.newStep(type).apply {
        this.duration = duration
        this.target = target
        this.exerciseId = exerciseId
        this.weightType = weightType
        this.weightValue = weightValue
        this.measurementSystem = WorkoutMeasurementSystem.METRIC
    }

    private fun repeat(count: Int, vararg children: WorkoutStepNode): WorkoutStepNode =
        WorkoutStepNode.newRepeat(count).apply { this.children = children.toMutableList() }

    private fun time(seconds: Long) = WorkoutDuration(WorkoutDurationType.TIME, seconds * 1000)
    private fun distanceM(meters: Long) = WorkoutDuration(WorkoutDurationType.DISTANCE, meters * 100)
    private fun calories(kcal: Long) = WorkoutDuration(WorkoutDurationType.CALORIES, kcal)
    private fun hrBelowDur(bpm: Long) = WorkoutDuration(WorkoutDurationType.HR_BELOW, bpm)
    private fun hrAboveDur(bpm: Long) = WorkoutDuration(WorkoutDurationType.HR_ABOVE, bpm)
    private fun strokes(n: Long) = WorkoutDuration(WorkoutDurationType.STROKES, n)
    private fun reps(n: Long) = WorkoutDuration(WorkoutDurationType.REPS, n)
    private val skipButton = WorkoutDuration(WorkoutDurationType.BUTTON_PRESS)

    private val noAlerts = WorkoutTarget.NONE
    private fun pace(lowSeconds: Long, highSeconds: Long) =
        WorkoutTarget(WorkoutTargetType.PACE, low = lowSeconds * 1000, high = highSeconds * 1000)

    private fun cadence(low: Long, high: Long) = WorkoutTarget(WorkoutTargetType.CADENCE, low = low, high = high)
    private fun speedKmh(lowKmh: Long, highKmh: Long) =
        WorkoutTarget(WorkoutTargetType.SPEED, low = kmhToMmPerS(lowKmh), high = kmhToMmPerS(highKmh))

    private fun strokeRate(low: Long, high: Long) = WorkoutTarget(WorkoutTargetType.STROKE_RATE, low = low, high = high)
    private fun hrZone(zone: Int) = WorkoutTarget(WorkoutTargetType.HR_ZONE, zone = zone)
    private fun hrRange(low: Long, high: Long) = WorkoutTarget(WorkoutTargetType.HR_RANGE, low = low, high = high)

    private fun kmhToMmPerS(kmh: Long): Long = (kmh * 1000 / 3.6).roundToLong()
}
