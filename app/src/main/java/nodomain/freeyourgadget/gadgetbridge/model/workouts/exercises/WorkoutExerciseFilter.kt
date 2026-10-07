package nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises

/**
 * One value of a [WorkoutExerciseFilter].
 */
data class WorkoutExerciseFilterValue(val id: String, val name: String)

/**
 * One filter of the exercise picker.
 */
data class WorkoutExerciseFilter(
    val id: String,
    val name: String,
    val values: List<WorkoutExerciseFilterValue>,
)
