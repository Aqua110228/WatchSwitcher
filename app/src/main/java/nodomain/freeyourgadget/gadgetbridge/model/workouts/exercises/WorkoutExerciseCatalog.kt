package nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises

/**
 * A device's exercise catalog.
 */
interface WorkoutExerciseCatalog {
    /**
     * The filters of one list, in display order. A null [list] returns the filters of the whole
     * catalog.
     */
    fun filters(list: String? = null): List<WorkoutExerciseFilter>

    /**
     * The entries of one list, in catalog order. A null [list] returns the whole catalog.
     */
    fun exercises(list: String? = null): List<WorkoutExercise>

    fun byId(id: String?): WorkoutExercise?

    /**
     * Reads the catalog, if it is not read yet. Should not be called from the main thread.
     */
    fun preload()
}
