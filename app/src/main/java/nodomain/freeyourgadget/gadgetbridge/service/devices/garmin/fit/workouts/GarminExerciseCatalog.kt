package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts

import com.google.gson.JsonObject
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.JsonWorkoutExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExercise
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseFilter
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseFilterValue
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.assetSource
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.ExerciseCategory
import java.io.InputStream

/**
 * One Garmin exercise, identified on the FIT file by a `(exercise_category, exercise_name)` pair.
 */
class GarminExercise(
    id: String,
    name: String,
    lists: Set<String>,
    filters: Map<String, Set<String>>,

    val category: ExerciseCategory,

    /**
     * The `exercise_name` value within [category]. Null for the generic exercise of the category,
     * which has no `exercise_name`.
     */
    val code: Int?,
) : WorkoutExercise(id, name, lists, filters)

/**
 * The lists of [GarminExerciseCatalog].
 */
object GarminExerciseLists {
    const val STRENGTH = "strength"
    const val YOGA = "yoga"
    const val PILATES = "pilates"
    const val MOBILITY = "mobility"
}

/**
 * The Garmin exercise catalog, read from `assets/workouts/exercises/garmin.json`.
 *
 * The file declares the muscle group and equipment filters. The category filter is built from
 * [ExerciseCategory], and every exercise matches its own category. The yoga list has no filters.
 */
class GarminExerciseCatalog(source: () -> InputStream) : JsonWorkoutExerciseCatalog(source) {
    private val categoryFilter: WorkoutExerciseFilter by lazy {
        val context = GBApplication.getContext()
        WorkoutExerciseFilter(
            id = CATEGORY_FILTER,
            name = context.getString(R.string.category),
            values = ExerciseCategory.entries
                .filter { it != ExerciseCategory.CATEGORY_UNKNOWN }
                .map { WorkoutExerciseFilterValue(it.name, context.getString(it.label)) }
                .sortedBy { it.name.lowercase() },
        )
    }

    override fun filters(list: String?): List<WorkoutExerciseFilter> =
        if (list == GarminExerciseLists.YOGA) emptyList() else listOf(categoryFilter) + super.filters(list)

    override fun parseExercise(
        id: String,
        name: String,
        lists: Set<String>,
        filters: Map<String, Set<String>>,
        json: JsonObject,
    ): WorkoutExercise {
        val category = ExerciseCategory.valueOf(json.get("category").asString)
        return GarminExercise(
            id,
            name,
            lists,
            filters + (CATEGORY_FILTER to setOf(category.name)),
            category,
            json.get("code")?.takeUnless { it.isJsonNull }?.asInt,
        )
    }

    fun exerciseFor(id: String?): GarminExercise? = byId(id) as? GarminExercise

    companion object {
        private const val ASSET = "workouts/exercises/garmin.json"
        private const val CATEGORY_FILTER = "category"

        @JvmField
        val INSTANCE: GarminExerciseCatalog = GarminExerciseCatalog(assetSource(ASSET))
    }
}
