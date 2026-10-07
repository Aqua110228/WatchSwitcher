package nodomain.freeyourgadget.gadgetbridge.model.workouts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.util.concurrent.atomic.AtomicLong

/**
 * The intensity target of a step. The value is in [zone], [low] and [high], or [enumValue],
 * per [WorkoutTargetType.isZone], [WorkoutTargetType.isRange] and [WorkoutTargetType.isEnum].
 * The units are documented on [nodomain.freeyourgadget.gadgetbridge.entities.WorkoutTemplateStep].
 */
@Parcelize
data class WorkoutTarget(
    val type: WorkoutTargetType,
    val zone: Int? = null,
    val low: Long? = null,
    val high: Long? = null,
    /**
     * A [WorkoutEffort] name, for [WorkoutTargetType.EFFORT].
     */
    val enumValue: String? = null,
) : Parcelable {
    companion object {
        val NONE = WorkoutTarget(WorkoutTargetType.NONE)
    }
}

/**
 * The end condition (duration) of a step.
 */
@Parcelize
data class WorkoutDuration(
    val type: WorkoutDurationType,
    val value: Long? = null,
) : Parcelable

/**
 * One node of a [WorkoutTemplate]'s step tree: a step, a repeat group or a multisport leg.
 */
@Parcelize
data class WorkoutStepNode(
    /**
     * Identifies the node during an edit session. The database row id once the template is saved,
     * a negative number before that.
     */
    val localId: Long = nextLocalId(),
    val type: WorkoutNodeType,
    var stepType: WorkoutStepType? = null,
    var note: String? = null,

    /**
     * REPEAT only.
     */
    var repeatCount: Int? = null,

    /**
     * LEG only.
     */
    var legActivityKind: ActivityKind? = null,

    var duration: WorkoutDuration? = null,
    var target: WorkoutTarget? = null,
    var secondaryTarget: WorkoutTarget? = null,

    /**
     * A [nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExercise.id] in the
     * catalog of the template's vendor.
     */
    var exerciseId: String? = null,

    var weightType: WorkoutWeightType? = null,
    var weightValue: Int? = null,

    var swimStroke: WorkoutSwimStroke? = null,
    var swimDrill: WorkoutSwimDrill? = null,
    var swimEquipment: WorkoutEquipment? = null,

    var measurementSystem: WorkoutMeasurementSystem? = null,

    /**
     * Empty for a STEP. A REPEAT holds STEP children. A LEG holds STEP and REPEAT children.
     */
    var children: MutableList<WorkoutStepNode> = mutableListOf(),
) : Parcelable {
    companion object {
        private val counter = AtomicLong(-1)

        /**
         * A unique negative id, for a node that is not persisted yet.
         */
        fun nextLocalId(): Long = counter.getAndDecrement()

        fun newStep(stepType: WorkoutStepType) =
            WorkoutStepNode(type = WorkoutNodeType.STEP, stepType = stepType)

        fun newRepeat(repeatCount: Int) =
            WorkoutStepNode(type = WorkoutNodeType.REPEAT, repeatCount = repeatCount)

        fun newLeg(activityKind: ActivityKind) =
            WorkoutStepNode(type = WorkoutNodeType.LEG, legActivityKind = activityKind)
    }

    /**
     * The number of STEP nodes in this node, including itself.
     */
    fun stepCount(): Int = when (type) {
        WorkoutNodeType.STEP -> 1
        WorkoutNodeType.REPEAT, WorkoutNodeType.LEG -> children.sumOf { it.stepCount() }
    }

    /**
     * This node and its descendants, depth first.
     */
    fun flattenSelfAndDescendants(): List<WorkoutStepNode> =
        listOf(this) + children.flatMap { it.flattenSelfAndDescendants() }
}

/**
 * A user-created structured workout, for one device.
 */
data class WorkoutTemplate(
    val id: Long? = null,

    /**
     * See [nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec.vendorId].
     */
    var vendorId: String,

    var name: String = "",
    var note: String? = null,
    var activityKind: ActivityKind,

    /**
     * Pool swim only, in centimeters.
     */
    var poolLength: Int? = null,

    /**
     * Pool swim only.
     */
    var poolLengthUnit: WorkoutPoolLengthUnit? = null,

    /**
     * Multisport only.
     */
    var transitions: Boolean? = null,

    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),

    /**
     * The status of the last sync. Null if the template was never sent to the device.
     */
    val syncStatus: WorkoutSyncStateStatus? = null,

    /**
     * The content hash of the template as it was last sent.
     */
    val syncContentHash: Int? = null,

    /**
     * The id of the workout on the device.
     */
    val syncRemoteId: String? = null,

    /**
     * The reason of the last sync failure.
     */
    val syncError: String? = null,

    /**
     * Top-level nodes, in order.
     */
    var steps: MutableList<WorkoutStepNode> = mutableListOf(),
) {
    /**
     * The number of STEP nodes in the whole template.
     */
    fun totalStepCount(): Int = steps.sumOf { it.stepCount() }
}

