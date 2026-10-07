package nodomain.freeyourgadget.gadgetbridge.model.workouts

/**
 * The type of a node in a workout template's step tree.
 */
enum class WorkoutNodeType {
    /**
     * A single step.
     */
    STEP,

    /**
     * A group of steps that repeats a number of times.
     */
    REPEAT,

    /**
     * A sub-workout with its own sport, directly under a multisport template.
     */
    LEG,
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutNodeType? = enumByName(name)
    }
}
