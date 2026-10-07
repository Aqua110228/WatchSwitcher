package nodomain.freeyourgadget.gadgetbridge.model.workouts.spec

import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEquipment
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseCatalog

/**
 * Kotlin DSL for declaring a [WorkoutTemplateSpec].
 */
@DslMarker
annotation class WorkoutSpecMarker

@WorkoutSpecMarker
class StepFieldsBuilder {
    private var exerciseLabel: Int? = null
    private var exerciseList: String? = null
    private var weightTypes: List<WorkoutWeightType> = emptyList()
    private var weightSpecs: Map<WorkoutWeightType, ValueSpec> = emptyMap()
    private var showStroke: Boolean = false
    private var showDrill: Boolean = false
    private var equipmentOptions: List<WorkoutEquipment> = emptyList()
    private var showUnit: Boolean = false

    /**
     * An exercise picker field over [list] of the device's [WorkoutExerciseCatalog]. A null [list]
     * shows the whole catalog.
     */
    fun exercise(@StringRes label: Int, list: String? = null) {
        exerciseLabel = label
        exerciseList = list
    }

    /**
     * [specs] holds the value constraints of each weight type that has a value.
     */
    fun weight(vararg types: WorkoutWeightType, specs: Map<WorkoutWeightType, ValueSpec> = emptyMap()) {
        weightTypes = types.toList()
        weightSpecs = specs
    }

    fun stroke() {
        showStroke = true
    }

    fun drill() {
        showDrill = true
    }

    fun equipment(vararg options: WorkoutEquipment) {
        equipmentOptions = options.toList()
    }

    fun unit() {
        showUnit = true
    }

    fun build(): StepFieldsSpec = StepFieldsSpec(
        exerciseLabel, exerciseList, weightTypes, weightSpecs, showStroke, showDrill, equipmentOptions, showUnit
    )
}

@WorkoutSpecMarker
class StepSpecBuilder {
    private val durations = mutableListOf<DurationOption>()
    private val primaryTargets = mutableListOf(TargetOption(WorkoutTargetType.NONE))
    private val secondaryTargets = mutableListOf<TargetOption>()
    var noteMaxLength: Int? = null
    private var fieldsSpec = StepFieldsSpec()

    fun duration(type: WorkoutDurationType, value: ValueSpec? = null) {
        durations += DurationOption(type, value)
    }

    fun target(type: WorkoutTargetType, value: ValueSpec? = null) {
        primaryTargets += TargetOption(type, value)
    }

    fun secondaryTarget(type: WorkoutTargetType, value: ValueSpec? = null) {
        secondaryTargets += TargetOption(type, value)
    }

    /**
     * Removes the durations inherited from the sport's default step.
     */
    fun noDurations() {
        durations.clear()
    }

    /**
     * Removes the targets inherited from the sport's default step.
     */
    fun noTargets() {
        primaryTargets.clear()
        primaryTargets += TargetOption(WorkoutTargetType.NONE)
    }

    fun fields(block: StepFieldsBuilder.() -> Unit) {
        fieldsSpec = StepFieldsBuilder().apply(block).build()
    }

    /**
     * A copy of this builder.
     */
    fun copy(): StepSpecBuilder {
        val b = StepSpecBuilder()
        b.durations += durations
        b.primaryTargets.clear()
        b.primaryTargets += primaryTargets
        b.secondaryTargets += secondaryTargets
        b.noteMaxLength = noteMaxLength
        b.fieldsSpec = fieldsSpec
        return b
    }

    fun build(): StepSpec {
        val secondary = if (secondaryTargets.isEmpty()) {
            emptyList()
        } else {
            listOf(TargetOption(WorkoutTargetType.NONE)) + secondaryTargets
        }
        return StepSpec(durations.toList(), primaryTargets.toList(), secondary, noteMaxLength, fieldsSpec)
    }
}

@WorkoutSpecMarker
class SportSpecBuilder(private val activityKind: ActivityKind) {
    @StringRes
    var activeLabel: Int = R.string.activeSeconds
    var maxSteps: Int = Int.MAX_VALUE
    private var repeatSpec: RepeatSpec? = null
    private var legsSpec: LegsSpec? = null
    var stepNoteMaxLength: Int? = null
    var poolLengthCustom: ValueSpec? = null
    var transitions: Boolean = false
    private val stepTypesList = mutableListOf<WorkoutStepType>()
    private val defaultStep = StepSpecBuilder()
    private val overrides = mutableMapOf<WorkoutStepType, StepSpecBuilder>()

    fun stepTypes(vararg types: WorkoutStepType) {
        stepTypesList += types
    }

    fun repeat(min: Int, max: Int) {
        repeatSpec = RepeatSpec(min, max)
    }

    fun legs(vararg kinds: ActivityKind) {
        legsSpec = LegsSpec(kinds.toList())
    }

    fun duration(type: WorkoutDurationType, value: ValueSpec? = null) = defaultStep.duration(type, value)

    fun target(type: WorkoutTargetType, value: ValueSpec? = null) = defaultStep.target(type, value)

    fun secondaryTarget(type: WorkoutTargetType, value: ValueSpec? = null) = defaultStep.secondaryTarget(type, value)

    fun fields(block: StepFieldsBuilder.() -> Unit) = defaultStep.fields(block)

    /**
     * The overrides of [type], starting from a copy of the sport's default step.
     */
    fun stepType(type: WorkoutStepType, block: StepSpecBuilder.() -> Unit) {
        overrides.getOrPut(type) { defaultStep.copy() }.apply(block)
    }

    fun build(): SportSpec = SportSpec(
        activityKind = activityKind,
        stepTypes = stepTypesList.toList(),
        activeLabel = activeLabel,
        maxSteps = maxSteps,
        repeat = repeatSpec,
        legs = legsSpec,
        stepNoteMaxLength = stepNoteMaxLength,
        poolLengthCustom = poolLengthCustom,
        transitions = transitions,
        defaultStep = defaultStep.build(),
        stepOverrides = overrides.mapValues { it.value.build() },
    )
}

@WorkoutSpecMarker
class WorkoutTemplateSpecBuilder(
    private val vendorId: String,
    private val exerciseCatalog: WorkoutExerciseCatalog?,
) {
    var maxTemplates: Int = Int.MAX_VALUE
    var nameMaxLength: Int = 200
    var noteMaxLength: Int = 200
    private val sports = mutableMapOf<ActivityKind, SportSpec>()

    fun sport(activityKind: ActivityKind, block: SportSpecBuilder.() -> Unit) {
        sports[activityKind] = SportSpecBuilder(activityKind).apply(block).build()
    }

    fun build(): WorkoutTemplateSpec =
        WorkoutTemplateSpec(vendorId, exerciseCatalog, maxTemplates, nameMaxLength, noteMaxLength, sports.toMap())
}

/**
 * Declares a [WorkoutTemplateSpec]. [exerciseCatalog] is null for a device with no exercise field.
 */
fun workoutTemplates(
    vendorId: String,
    exerciseCatalog: WorkoutExerciseCatalog? = null,
    block: WorkoutTemplateSpecBuilder.() -> Unit,
): WorkoutTemplateSpec = WorkoutTemplateSpecBuilder(vendorId, exerciseCatalog).apply(block).build()
