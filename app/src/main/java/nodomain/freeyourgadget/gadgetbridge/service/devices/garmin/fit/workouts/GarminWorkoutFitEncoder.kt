package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEffort
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutPoolLengthUnit
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.FileType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordData
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.FitBaseUnit
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.GarminSport
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.WktStepDuration
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.WktStepTarget
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitExerciseTitle
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFileCreator
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFileId
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitWorkout
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitWorkoutSession
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitWorkoutStep
import org.slf4j.LoggerFactory

/**
 * Encodes a [WorkoutTemplate] into a Garmin FIT workout file.
 */
object GarminWorkoutFitEncoder {
    private val LOG = LoggerFactory.getLogger(GarminWorkoutFitEncoder::class.java)

    private const val LMT_FILE_ID = 0x00
    private const val LMT_FILE_CREATOR = 0x01
    private const val LMT_WORKOUT = 0x02
    private const val LMT_WORKOUT_STEP = 0x03
    private const val LMT_EXERCISE_TITLE = 0x04
    private const val LMT_WORKOUT_SESSION = 0x05

    /**
     * @param timestampSeconds the creation time of the file, in the FIT epoch
     */
    fun encode(template: WorkoutTemplate, timestampSeconds: Long): FitFile? {
        val garminSport = GarminSport.fromActivityKind(template.activityKind).orElse(null)
        if (garminSport == null) {
            LOG.error("No Garmin FIT sport mapping for {}", template.activityKind)
            return null
        }

        val steps = StepRecords(poolLength(template))
        if (template.activityKind == ActivityKind.MULTISPORT) {
            for (leg in template.steps) {
                steps.encodeLeg(leg)
            }
        } else {
            steps.encodeNodes(template.steps, template.activityKind)
        }

        val records = mutableListOf<RecordData>()
        records.add(fileId(timestampSeconds))
        records.add(fileCreator())
        records.add(workout(template, garminSport, steps))
        records.addAll(steps.sessionRecords)
        records.addAll(steps.stepRecords)
        records.addAll(steps.exerciseTitleRecords())

        return FitFile(records)
    }

    /**
     * The pool length of [template], in centimeters, or null when it has none.
     */
    private fun poolLength(template: WorkoutTemplate): Pair<Int, WorkoutPoolLengthUnit>? {
        val unit = template.poolLengthUnit ?: return null
        val lengthCm = if (unit == WorkoutPoolLengthUnit.CUSTOM) template.poolLength else unit.lengthCm
        return lengthCm?.let { it to unit }
    }

    private fun workout(template: WorkoutTemplate, garminSport: GarminSport, steps: StepRecords): FitWorkout {
        val builder = FitWorkout.Builder()
        builder.setMessageIndex(0)
        builder.setWktName(template.name)
        template.note?.takeIf { it.isNotEmpty() }?.let { builder.setWktDescription(it) }
        builder.setSport(garminSport.type)
        builder.setSubSport(garminSport.subtype)
        builder.setCapabilities(32L) // TODO what are these?
        builder.setNumValidSteps(steps.stepRecords.size)
        if (template.activityKind == ActivityKind.MULTISPORT) {
            builder.setNumSessions(steps.sessionRecords.size)
            builder.setTransitions(template.transitions)
        }
        if (template.activityKind == ActivityKind.POOL_SWIM) {
            steps.poolLength?.let { (lengthCm, unit) ->
                builder.setPoolLength(lengthCm / 100f)
                builder.setPoolLengthUnit(GarminWorkoutCodes.poolLengthUnitCode(unit))
            }
            val distanceCm = estimatedDistanceCm(template)
            builder.setDurationType(WktStepDuration.DISTANCE.num)
            builder.setDurationValue(distanceCm)
            builder.setDistance(distanceCm / 100.0)
            builder.setDistanceFlag(0)
            if (steps.hasRepeat) {
                builder.setTime(0.0)
                builder.setTimeFlag(1)
            }
        }
        return builder.build(LMT_WORKOUT)
    }

    /**
     * The estimated distance of a pool swim, in centimeters.
     */
    private fun estimatedDistanceCm(template: WorkoutTemplate): Long {
        fun stepDistanceCm(node: WorkoutStepNode): Long {
            if (node.stepType == WorkoutStepType.REST) return 0L
            val duration = node.duration ?: return 0L
            val value = duration.value ?: return 0L
            return when (duration.type) {
                WorkoutDurationType.DISTANCE -> value
                WorkoutDurationType.TIME -> value / 10 // ms -> s * 100 cm
                else -> 0L
            }
        }
        return template.steps.sumOf { node ->
            when (node.type) {
                WorkoutNodeType.STEP -> stepDistanceCm(node)
                WorkoutNodeType.REPEAT -> node.children.sumOf { stepDistanceCm(it) } * (node.repeatCount ?: 1)
                WorkoutNodeType.LEG -> 0L
            }
        }
    }

    /**
     * The `workout_step`, `workout_session` and `exercise_title` records of one file, in the order
     * they are encoded.
     */
    private class StepRecords(val poolLength: Pair<Int, WorkoutPoolLengthUnit>?) {
        val stepRecords = mutableListOf<RecordData>()
        val sessionRecords = mutableListOf<RecordData>()
        var hasRepeat = false
            private set

        private val exerciseTitleOrder = LinkedHashMap<GarminExercise, Int>()
        private var messageIndex = 0

        fun encodeLeg(leg: WorkoutStepNode) {
            val legKind = leg.legActivityKind
            if (leg.type != WorkoutNodeType.LEG || legKind == null) {
                LOG.warn("Skipping non-leg node {} of a multisport template", leg.type)
                return
            }
            val legSport = GarminSport.fromActivityKind(legKind).orElse(null)
            if (legSport == null) {
                LOG.warn("No Garmin FIT sport mapping for leg {}, skipping it", legKind)
                return
            }
            val firstStepIndex = messageIndex
            encodeNodes(leg.children, legKind)
            val builder = FitWorkoutSession.Builder()
            builder.setMessageIndex(sessionRecords.size)
            builder.setSport(legSport.type)
            builder.setSubSport(legSport.subtype)
            builder.setNumValidSteps(messageIndex - firstStepIndex)
            builder.setFirstStepIndex(firstStepIndex)
            if (legKind == ActivityKind.POOL_SWIM && poolLength != null) {
                builder.setPoolLength(poolLength.first / 100f)
                builder.setPoolLengthUnit(GarminWorkoutCodes.poolLengthUnitCode(poolLength.second))
            }
            sessionRecords.add(builder.build(LMT_WORKOUT_SESSION))
        }

        fun encodeNodes(nodes: List<WorkoutStepNode>, activityKind: ActivityKind) {
            for (node in nodes) {
                when (node.type) {
                    WorkoutNodeType.STEP -> encodeStep(node, activityKind)
                    WorkoutNodeType.REPEAT -> encodeRepeat(node, activityKind)
                    WorkoutNodeType.LEG -> throw IllegalStateException("A leg is only valid at the top level of a multisport template")
                }
            }
        }

        fun exerciseTitleRecords(): List<RecordData> = exerciseTitleOrder.map { (exercise, index) ->
            val builder = FitExerciseTitle.Builder()
            builder.setMessageIndex(index)
            builder.setExerciseCategory(exercise.category.num)
            exercise.code?.let { builder.setExerciseName(it) }
            builder.build(LMT_EXERCISE_TITLE)
        }

        private fun encodeRepeat(node: WorkoutStepNode, activityKind: ActivityKind) {
            hasRepeat = true
            val startIndex = messageIndex
            node.children.forEach { encodeStep(it, activityKind) }
            val builder = FitWorkoutStep.Builder()
            builder.setMessageIndex(messageIndex)
            builder.setDurationType(WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT)
            builder.setDurationValue(startIndex.toLong())
            builder.setTargetValue((node.repeatCount ?: 1).toLong())
            builder.setSkipLastRecover(activityKind == ActivityKind.POOL_SWIM)
            stepRecords.add(builder.build(LMT_WORKOUT_STEP))
            messageIndex++
        }

        private fun encodeStep(node: WorkoutStepNode, activityKind: ActivityKind) {
            val builder = FitWorkoutStep.Builder()
            builder.setMessageIndex(messageIndex)
            // Garmin Connect writes the weight unit on every step?
            builder.setWeightDisplayUnit(FitBaseUnit.KILOGRAM)

            val stepType = node.stepType ?: WorkoutStepType.ACTIVE
            builder.setIntensity(GarminWorkoutCodes.intensityOf(stepType))

            node.note?.takeIf { it.isNotEmpty() }?.let { builder.setNotes(it) }

            encodeDuration(builder, node.duration)
            if (activityKind == ActivityKind.POOL_SWIM) {
                encodeSwimTargets(builder, node, stepType)
                GarminWorkoutCodes.equipmentCode(node.swimEquipment)?.let { builder.setEquipment(it) }
                GarminWorkoutCodes.swimDrillCode(node.swimDrill)?.let { builder.setSwimDrillType(it) }
            } else {
                encodeTarget(builder, node.target, primary = true, activityKind)
                encodeTarget(builder, node.secondaryTarget, primary = false, activityKind)
            }

            // Only an ACTIVE step has an exercise and a weight
            if (stepType == WorkoutStepType.ACTIVE) {
                encodeExerciseAndWeight(builder, node)
            }

            stepRecords.add(builder.build(LMT_WORKOUT_STEP))
            messageIndex++
        }

        private fun encodeExerciseAndWeight(builder: FitWorkoutStep.Builder, node: WorkoutStepNode) {
            node.exerciseId?.let { exerciseId ->
                val exercise = GarminExerciseCatalog.INSTANCE.exerciseFor(exerciseId)
                if (exercise != null) {
                    exerciseTitleOrder.getOrPut(exercise) { exerciseTitleOrder.size }
                    builder.setExerciseCategory(exercise.category.num)
                    exercise.code?.let { builder.setExerciseName(it) }
                } else {
                    LOG.warn("No Garmin exercise {}, omitting it from the encoded step", exerciseId)
                }
            }

            when (node.weightType) {
                // Captured FIT files do not distinguish BODY_WEIGHT from a manual 0 kg entry
                WorkoutWeightType.MANUAL, WorkoutWeightType.BODY_WEIGHT -> {
                    val grams = node.weightValue ?: 0
                    builder.setExerciseWeight(grams / 1000f)
                }

                WorkoutWeightType.PERCENT_1RM -> {
                    builder.setExerciseWeightPercent(node.weightValue ?: 0)
                    builder.setExerciseWeightType(5) // constant seen on every %-of-1RM capture
                }

                WorkoutWeightType.RM, WorkoutWeightType.NONE, null -> {}
            }
        }
    }

    private fun encodeDuration(builder: FitWorkoutStep.Builder, duration: WorkoutDuration?) {
        val type = duration?.type ?: WorkoutDurationType.BUTTON_PRESS
        builder.setDurationType(GarminWorkoutCodes.durationTypeCode(type))
        val value = duration?.value ?: return
        when (type) {
            WorkoutDurationType.TIME, WorkoutDurationType.DISTANCE,
            WorkoutDurationType.CALORIES, WorkoutDurationType.REPS,
            WorkoutDurationType.SEND_OFF_TIME,
                -> builder.setDurationValue(value)

            WorkoutDurationType.HR_BELOW, WorkoutDurationType.HR_ABOVE ->
                builder.setDurationValue(value + GarminWorkoutCodes.HR_OFFSET)

            WorkoutDurationType.POWER_BELOW, WorkoutDurationType.POWER_ABOVE ->
                builder.setDurationValue(value + GarminWorkoutCodes.POWER_OFFSET)

            WorkoutDurationType.CSS_SEND_OFF_TIME ->
                builder.setDurationValue(value + GarminWorkoutCodes.CSS_OFFSET)

            WorkoutDurationType.BUTTON_PRESS, WorkoutDurationType.LAPS, WorkoutDurationType.STROKES -> {}
        }
    }

    /**
     * The primary target of a pool swim step is its stroke. The target is the secondary target.
     * A rest step has an open target and an open secondary target.
     */
    private fun encodeSwimTargets(builder: FitWorkoutStep.Builder, node: WorkoutStepNode, stepType: WorkoutStepType) {
        if (stepType == WorkoutStepType.REST) {
            builder.setTargetType(WktStepTarget.OPEN)
            builder.setSecondaryTargetType(WktStepTarget.OPEN)
            return
        }

        builder.setTargetType(WktStepTarget.SWIM_STROKE)
        builder.setTargetValue(GarminWorkoutCodes.swimStrokeCode(node.swimStroke))

        val target = node.target ?: WorkoutTarget.NONE
        when (target.type) {
            WorkoutTargetType.EFFORT -> {
                val effort = WorkoutEffort.fromName(target.enumValue)
                if (effort != null) {
                    builder.setSecondaryTargetType(WktStepTarget.SWIM_EFFORT)
                    builder.setSecondaryTargetValue(GarminWorkoutCodes.effortCode(effort))
                    return
                }
                LOG.warn("Unknown swim effort {}, encoding no target", target.enumValue)
            }

            WorkoutTargetType.PACE -> {
                val speed = GarminWorkoutCodes.swimPaceMsPer100mToSpeedMmPerS(target.low)
                builder.setSecondaryTargetType(WktStepTarget.SPEED)
                builder.setSecondaryCustomTargetValueLow(speed)
                builder.setSecondaryCustomTargetValueHigh(speed)
                return
            }

            WorkoutTargetType.CSS_OFFSET -> {
                builder.setSecondaryTargetType(WktStepTarget.SWIM_CSS_OFFSET)
                builder.setSecondaryTargetValue((target.low ?: 0L) + GarminWorkoutCodes.CSS_OFFSET)
                return
            }

            WorkoutTargetType.NONE -> {}

            else -> LOG.warn("Target type {} is not supported on a Garmin pool swim step", target.type)
        }

        builder.setSecondaryTargetType(WktStepTarget.SWIM_STROKE)
        builder.setSecondaryTargetValue(0L)
    }

    private fun encodeTarget(
        builder: FitWorkoutStep.Builder,
        target: WorkoutTarget?,
        primary: Boolean,
        activityKind: ActivityKind,
    ) {
        val t = target ?: WorkoutTarget.NONE
        // An unset secondary target is not written
        if (!primary && t.type == WorkoutTargetType.NONE) return

        val typeCode: WktStepTarget
        var value: Long? = null
        var low: Long? = null
        var high: Long? = null

        when (t.type) {
            WorkoutTargetType.NONE -> {
                typeCode = WktStepTarget.OPEN
                value = 0L
            }

            WorkoutTargetType.PACE -> {
                typeCode = WktStepTarget.SPEED
                val (lo, hi) = GarminWorkoutCodes.paceMsPerKmToSpeedMmPerS(t.low, t.high)
                low = lo
                high = hi
            }

            WorkoutTargetType.SPEED -> {
                typeCode = WktStepTarget.SPEED
                low = t.low
                high = t.high
            }

            WorkoutTargetType.CADENCE -> {
                typeCode = WktStepTarget.CADENCE
                val halve = GarminWorkoutCodes.isHalvedCadence(activityKind)
                low = if (halve) (t.low ?: 0L) / 2 else t.low
                high = if (halve) (t.high ?: 0L) / 2 else t.high
            }

            WorkoutTargetType.HR_ZONE -> {
                typeCode = WktStepTarget.HEART_RATE
                value = (t.zone ?: 0).toLong()
            }

            WorkoutTargetType.HR_RANGE -> {
                typeCode = WktStepTarget.HEART_RATE
                low = (t.low ?: 0L) + GarminWorkoutCodes.HR_OFFSET
                high = (t.high ?: 0L) + GarminWorkoutCodes.HR_OFFSET
            }

            WorkoutTargetType.POWER_ZONE -> {
                typeCode = WktStepTarget.POWER_3S
                value = (t.zone ?: 0).toLong()
            }

            WorkoutTargetType.POWER_RANGE -> {
                typeCode = WktStepTarget.POWER_3S
                low = (t.low ?: 0L) + GarminWorkoutCodes.POWER_OFFSET
                high = (t.high ?: 0L) + GarminWorkoutCodes.POWER_OFFSET
            }

            WorkoutTargetType.POWER_FTP_RANGE -> {
                typeCode = WktStepTarget.POWER_3S
                low = t.low
                high = t.high
            }

            else -> {
                // EFFORT and CSS_OFFSET are pool swim targets - see encodeSwimTargets
                LOG.warn("Target type {} is not supported by the Garmin FIT workout encoder", t.type)
                return
            }
        }

        if (primary) {
            builder.setTargetType(typeCode)
            value?.let { builder.setTargetValue(it) }
            low?.let { builder.setCustomTargetValueLow(it) }
            high?.let { builder.setCustomTargetValueHigh(it) }
        } else {
            builder.setSecondaryTargetType(typeCode)
            value?.let { builder.setSecondaryTargetValue(it) }
            low?.let { builder.setSecondaryCustomTargetValueLow(it) }
            high?.let { builder.setSecondaryCustomTargetValueHigh(it) }
        }
    }

    private fun fileId(timestampSeconds: Long): FitFileId =
        FitFileId.Builder()
            .setType(FileType.FILETYPE.WORKOUTS)
            .setManufacturer(1)
            .setProduct(65534)
            .setSerialNumber(1L)
            .setNumber(1)
            .setTimeCreated(timestampSeconds)
            .build(LMT_FILE_ID)

    private fun fileCreator(): FitFileCreator =
        FitFileCreator.Builder()
            .setSoftwareVersion(1)
            .build(LMT_FILE_CREATOR)
}
