package nodomain.freeyourgadget.gadgetbridge.model.workouts.spec

import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseCatalog

/**
 * One validation error. [nodeLocalId] identifies the node, or is null for a template-level issue.
 */
data class WorkoutValidationIssue(val nodeLocalId: Long?, @StringRes val message: Int)

/**
 * The validation errors of one STEP node, per field.
 */
data class WorkoutStepFieldIssues(
    @StringRes val stepType: Int? = null,
    @StringRes val duration: Int? = null,
    @StringRes val target: Int? = null,
    @StringRes val secondaryTarget: Int? = null,
    @StringRes val exercise: Int? = null,
) {
    fun isEmpty(): Boolean =
        stepType == null && duration == null && target == null && secondaryTarget == null && exercise == null
}

/**
 * Validates a [WorkoutTemplate] against a [WorkoutTemplateSpec].
 */
object WorkoutTemplateValidator {
    fun validate(template: WorkoutTemplate, spec: WorkoutTemplateSpec): List<WorkoutValidationIssue> {
        val issues = mutableListOf<WorkoutValidationIssue>()
        val sportSpec = spec.sports[template.activityKind]
        if (sportSpec == null) {
            issues += WorkoutValidationIssue(null, R.string.workout_error_sport_not_supported)
            return issues
        }

        if (template.name.isBlank()) {
            issues += WorkoutValidationIssue(null, R.string.workout_error_name_required)
        } else if (template.name.length > spec.nameMaxLength) {
            issues += WorkoutValidationIssue(null, R.string.workout_error_name_too_long)
        }
        template.note?.let {
            if (it.length > spec.noteMaxLength) issues += WorkoutValidationIssue(
                null,
                R.string.workout_error_note_too_long
            )
        }
        val stepCount = template.totalStepCount()
        if (stepCount == 0) {
            issues += WorkoutValidationIssue(null, R.string.workout_error_no_steps)
        } else if (stepCount > sportSpec.maxSteps) {
            issues += WorkoutValidationIssue(null, R.string.workout_error_too_many_steps)
        }

        template.steps.forEach { validateNode(it, spec, sportSpec, issues) }
        return issues
    }

    /**
     * Validates [nodes] as the steps of [activityKind], without the template-level checks.
     */
    fun validateNodes(nodes: List<WorkoutStepNode>, spec: WorkoutTemplateSpec, activityKind: ActivityKind): List<WorkoutValidationIssue> {
        val sportSpec = spec.sports[activityKind]
            ?: return listOf(WorkoutValidationIssue(null, R.string.workout_error_sport_not_supported))
        val issues = mutableListOf<WorkoutValidationIssue>()
        nodes.forEach { validateNode(it, spec, sportSpec, issues) }
        return issues
    }

    /**
     * Whether [template] has no issues against [spec].
     */
    fun isSupported(template: WorkoutTemplate, spec: WorkoutTemplateSpec): Boolean = validate(template, spec).isEmpty()

    /**
     * Validates one STEP node on its own, with the issues keyed by field.
     */
    fun validateStepFields(
        node: WorkoutStepNode,
        sportSpec: SportSpec,
        catalog: WorkoutExerciseCatalog?,
    ): WorkoutStepFieldIssues {
        val stepType = node.stepType
        if (stepType == null || stepType !in sportSpec.stepTypes) {
            return WorkoutStepFieldIssues(stepType = R.string.workout_error_step_type_not_supported)
        }
        val stepSpec = sportSpec.stepSpec(stepType)
        return WorkoutStepFieldIssues(
            duration = durationIssue(stepSpec, node.duration),
            target = targetIssue(
                stepSpec.primaryTargets,
                node.target ?: WorkoutTarget.NONE,
                R.string.workout_error_target_not_supported
            ),
            secondaryTarget = secondaryTargetIssue(stepSpec, node),
            exercise = exerciseIssue(stepSpec, node, catalog),
        )
    }

    private fun validateNode(
        node: WorkoutStepNode,
        spec: WorkoutTemplateSpec,
        sportSpec: SportSpec,
        issues: MutableList<WorkoutValidationIssue>,
    ) {
        when (node.type) {
            WorkoutNodeType.STEP -> validateStep(node, spec, sportSpec, issues)
            WorkoutNodeType.REPEAT -> {
                val repeatSpec = sportSpec.repeat
                if (repeatSpec == null) {
                    issues += WorkoutValidationIssue(node.localId, R.string.workout_error_repeat_not_supported)
                } else {
                    val count = node.repeatCount
                    if (count == null || count < repeatSpec.min || count > repeatSpec.max) {
                        issues += WorkoutValidationIssue(node.localId, R.string.workout_error_repeat_count_out_of_range)
                    }
                }
                if (node.children.isEmpty()) issues += WorkoutValidationIssue(
                    node.localId,
                    R.string.workout_error_empty_group
                )
                node.children.forEach { child ->
                    if (child.type != WorkoutNodeType.STEP) {
                        issues += WorkoutValidationIssue(
                            child.localId,
                            R.string.workout_error_nested_repeat_not_supported
                        )
                    }
                    validateNode(child, spec, sportSpec, issues)
                }
            }

            WorkoutNodeType.LEG -> {
                val legsSpec = sportSpec.legs
                val legKind = node.legActivityKind
                if (legsSpec == null || legKind == null || legKind !in legsSpec.sports) {
                    issues += WorkoutValidationIssue(node.localId, R.string.workout_error_leg_not_supported)
                }
                if (node.children.isEmpty()) issues += WorkoutValidationIssue(
                    node.localId,
                    R.string.workout_error_empty_group
                )
                val legSportSpec = legKind?.let { spec.sports[it] } ?: sportSpec
                node.children.forEach { validateNode(it, spec, legSportSpec, issues) }
            }
        }
    }

    private fun validateStep(
        node: WorkoutStepNode,
        spec: WorkoutTemplateSpec,
        sportSpec: SportSpec,
        issues: MutableList<WorkoutValidationIssue>,
    ) {
        val stepType = node.stepType
        if (stepType == null || stepType !in sportSpec.stepTypes) {
            issues += WorkoutValidationIssue(node.localId, R.string.workout_error_step_type_not_supported)
            return
        }
        val stepSpec = sportSpec.stepSpec(stepType)

        durationIssue(stepSpec, node.duration)?.let {
            issues += WorkoutValidationIssue(node.localId, it)
        }
        targetIssue(
            stepSpec.primaryTargets,
            node.target ?: WorkoutTarget.NONE,
            R.string.workout_error_target_not_supported
        )?.let {
            issues += WorkoutValidationIssue(node.localId, it)
        }

        secondaryTargetIssue(stepSpec, node)?.let {
            issues += WorkoutValidationIssue(node.localId, it)
        }

        exerciseIssue(stepSpec, node, spec.exerciseCatalog)?.let {
            issues += WorkoutValidationIssue(node.localId, it)
        }
    }

    /**
     * The exercise must exist in [catalog], and must be in the list of the step's exercise field.
     */
    @StringRes
    private fun exerciseIssue(stepSpec: StepSpec, node: WorkoutStepNode, catalog: WorkoutExerciseCatalog?): Int? {
        val exerciseId = node.exerciseId ?: return null
        if (stepSpec.fields.exerciseLabel == null) return R.string.workout_error_exercise_not_supported
        val exercise = catalog?.byId(exerciseId) ?: return R.string.workout_error_exercise_not_supported
        val list = stepSpec.fields.exerciseList ?: return null
        return if (list in exercise.lists) null else R.string.workout_error_exercise_not_supported
    }

    private fun durationIssue(stepSpec: StepSpec, duration: WorkoutDuration?): Int? {
        val option = duration?.let { d -> stepSpec.durations.find { it.type == d.type } }
        return when {
            duration == null || option == null -> R.string.workout_error_duration_not_supported
            !valueInRange(option.value, duration.value) -> R.string.workout_error_value_out_of_range
            else -> null
        }
    }

    @StringRes
    private fun targetIssue(options: List<TargetOption>, target: WorkoutTarget, notSupportedMessage: Int): Int? {
        val option = options.find { it.type == target.type } ?: return notSupportedMessage
        return targetValueIssue(option.value, target)
    }

    /**
     * The secondary target must be declared, and must not apply to the same metric as the primary
     * target.
     */
    @StringRes
    private fun secondaryTargetIssue(stepSpec: StepSpec, node: WorkoutStepNode): Int? {
        val secondary = node.secondaryTarget?.takeIf { it.type != WorkoutTargetType.NONE } ?: return null
        val primaryType = node.target?.type ?: WorkoutTargetType.NONE
        if (secondary.type.conflictsWith(primaryType)) return R.string.workout_error_secondary_target_conflict
        return targetIssue(stepSpec.secondaryTargets, secondary, R.string.workout_error_secondary_target_not_supported)
    }

    /**
     * A duration value is a single value, never a range.
     */
    private fun valueInRange(spec: ValueSpec?, value: Long?): Boolean = when (spec) {
        null -> true
        is ValueSpec.Range -> value != null && value in spec.min..spec.max
        is ValueSpec.Time -> value != null && value in spec.min..spec.max
        is ValueSpec.Zone -> value != null && value in spec.min.toLong()..spec.max.toLong()
        is ValueSpec.Pace -> value != null && value in spec.min..spec.max
        is ValueSpec.EnumValues<*> -> value != null
        is ValueSpec.PairRange -> false
    }

    private fun targetValueIssue(spec: ValueSpec?, target: WorkoutTarget): Int? {
        val ok = when (spec) {
            null -> true
            is ValueSpec.Zone -> target.zone != null && target.zone in spec.min..spec.max
            is ValueSpec.Range -> target.low != null && target.low in spec.min..spec.max
            is ValueSpec.Time -> target.low != null && target.low in spec.min..spec.max
            is ValueSpec.Pace -> target.low != null && target.low in spec.min..spec.max
            is ValueSpec.EnumValues<*> -> target.enumValue != null && spec.values.any { (it as Enum<*>).name == target.enumValue }
            is ValueSpec.PairRange -> {
                val low = target.low
                val high = target.high
                low != null && high != null && low <= high && boundInRange(spec.inner, low) && boundInRange(
                    spec.inner,
                    high
                )
            }
        }
        return if (ok) null else R.string.workout_error_value_out_of_range
    }

    private fun boundInRange(spec: ValueSpec, value: Long): Boolean = when (spec) {
        is ValueSpec.Range -> value in spec.min..spec.max
        is ValueSpec.Time -> value in spec.min..spec.max
        is ValueSpec.Pace -> value in spec.min..spec.max
        else -> true
    }
}
