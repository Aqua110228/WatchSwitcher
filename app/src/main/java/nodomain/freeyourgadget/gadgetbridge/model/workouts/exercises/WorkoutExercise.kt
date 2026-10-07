package nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises

/**
 * One exercise of a [WorkoutExerciseCatalog].
 */
open class WorkoutExercise(
    /**
     * Unique within the catalog. Stored in a step's exerciseId.
     */
    val id: String,

    /**
     * English only.
     */
    val name: String,

    /**
     * The lists this entry is in, see [nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.StepFieldsSpec.exerciseList].
     */
    val lists: Set<String>,

    /**
     * The [WorkoutExerciseFilterValue.id]s this entry matches, by [WorkoutExerciseFilter.id].
     */
    val filters: Map<String, Set<String>>,
) {
    /**
     * Whether this entry matches every value in [checked], a map of filter id to checked value ids.
     */
    fun matches(checked: Map<String, Set<String>>): Boolean = checked.all { (filterId, values) ->
        filters[filterId]?.containsAll(values) == true
    }

    override fun toString(): String = "$id ($name)"
}
