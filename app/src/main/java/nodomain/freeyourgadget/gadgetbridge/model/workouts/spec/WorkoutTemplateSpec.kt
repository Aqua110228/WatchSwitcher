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
 * One duration choice of a step. [value] is null for a type with no value, such as a button press.
 */
data class DurationOption(val type: WorkoutDurationType, val value: ValueSpec? = null)

/**
 * One target choice of a step. [value] is null for [WorkoutTargetType.NONE].
 */
data class TargetOption(val type: WorkoutTargetType, val value: ValueSpec? = null)

/**
 * How many times a REPEAT group may repeat.
 */
data class RepeatSpec(val min: Int, val max: Int)

/**
 * The sports a leg of a multisport template can have.
 */
data class LegsSpec(val sports: List<ActivityKind>)

/**
 * The fields of a step beyond its duration and targets. A null or empty field is not shown.
 */
data class StepFieldsSpec(
    /**
     * The label of the exercise field. Null hides the field.
     */
    @StringRes val exerciseLabel: Int? = null,

    /**
     * The list of [WorkoutTemplateSpec.exerciseCatalog] the exercise picker shows. Null shows the
     * whole catalog.
     */
    val exerciseList: String? = null,

    /**
     * The available weight types. Empty hides the weight field.
     */
    val weightTypes: List<WorkoutWeightType> = emptyList(),

    /**
     * The value constraints of each weight type that has a value.
     */
    val weightSpecs: Map<WorkoutWeightType, ValueSpec> = emptyMap(),

    val showStroke: Boolean = false,
    val showDrill: Boolean = false,

    /**
     * The available equipment. Empty hides the equipment field.
     */
    val equipmentOptions: List<WorkoutEquipment> = emptyList(),

    /**
     * Whether the step has a metric/imperial unit field.
     */
    val showUnit: Boolean = false,
)

/**
 * The durations, targets and fields of one [WorkoutStepType].
 */
data class StepSpec(
    val durations: List<DurationOption>,
    val primaryTargets: List<TargetOption> = listOf(TargetOption(WorkoutTargetType.NONE)),
    /**
     * Empty when the step has no secondary target. Otherwise starts with [WorkoutTargetType.NONE].
     */
    val secondaryTargets: List<TargetOption> = emptyList(),
    val noteMaxLength: Int? = null,
    val fields: StepFieldsSpec = StepFieldsSpec(),
)

/**
 * What a device supports for one sport.
 */
data class SportSpec(
    val activityKind: ActivityKind,
    val stepTypes: List<WorkoutStepType>,
    /**
     * The label of [WorkoutStepType.ACTIVE].
     */
    @StringRes val activeLabel: Int = R.string.activeSeconds,
    val maxSteps: Int = Int.MAX_VALUE,
    /**
     * Null when the sport has no repeat groups.
     */
    val repeat: RepeatSpec? = null,
    /**
     * The valid leg sports of a multisport template. Null for every other sport.
     */
    val legs: LegsSpec? = null,
    val stepNoteMaxLength: Int? = null,
    /**
     * The constraints of a custom pool length. Null hides the pool length fields.
     */
    val poolLengthCustom: ValueSpec? = null,
    /**
     * Whether the template has a transitions checkbox.
     */
    val transitions: Boolean = false,
    val defaultStep: StepSpec,
    val stepOverrides: Map<WorkoutStepType, StepSpec> = emptyMap(),
) {
    fun stepSpec(stepType: WorkoutStepType): StepSpec = stepOverrides[stepType] ?: defaultStep
}

/**
 * What a device supports for workout templates.
 */
data class WorkoutTemplateSpec(
    /**
     * The vendor whose templates this device uses.
     */
    val vendorId: String,

    /**
     * The exercise catalog of the device. Null for a device with no exercise field.
     */
    val exerciseCatalog: WorkoutExerciseCatalog? = null,

    val maxTemplates: Int = Int.MAX_VALUE,
    val nameMaxLength: Int = 200,
    val noteMaxLength: Int = 200,
    val sports: Map<ActivityKind, SportSpec> = emptyMap(),
)
