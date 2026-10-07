package nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.InputStreamReader

/**
 * A [WorkoutExerciseCatalog] read from a JSON file. The file is parsed once, on first use.
 *
 * A subclass supplies [parseExercise], which reads its own fields from the same JSON object.
 *
 * File format:
 * ```json
 * {
 *   "filters": [
 *     { "id": "muscle", "name": "Muscle group",
 *       "values": [ { "id": "upper_body", "name": "Upper body" } ] }
 *   ],
 *   "exercises": [
 *     { "id": "barbell_bench_press", "name": "Barbell Bench Press",
 *       "lists": ["strength"], "filters": { "muscle": ["upper_body"] } }
 *   ]
 * }
 * ```
 * Any other key is vendor-specific.
 */
abstract class JsonWorkoutExerciseCatalog(private val source: () -> InputStream) : WorkoutExerciseCatalog {
    private val data: CatalogData by lazy { parse() }

    override fun filters(list: String?): List<WorkoutExerciseFilter> = data.filters

    override fun exercises(list: String?): List<WorkoutExercise> =
        if (list == null) data.exercises else data.exercises.filter { list in it.lists }

    override fun byId(id: String?): WorkoutExercise? = id?.let { data.byId[it] }

    override fun preload() {
        data
    }

    /**
     * @param json the whole entry, with the vendor fields
     */
    protected abstract fun parseExercise(
        id: String,
        name: String,
        lists: Set<String>,
        filters: Map<String, Set<String>>,
        json: JsonObject,
    ): WorkoutExercise

    private fun parse(): CatalogData = try {
        source().use { stream ->
            val root = JsonParser.parseReader(InputStreamReader(stream, Charsets.UTF_8)).asJsonObject
            CatalogData(parseFilters(root), parseExercises(root))
        }
    } catch (e: Exception) {
        LOG.error("Failed to parse exercise catalog", e)
        CatalogData(emptyList(), emptyList())
    }

    private fun parseFilters(root: JsonObject): List<WorkoutExerciseFilter> =
        root.getAsJsonArray("filters").orEmpty().map { element ->
            val filter = element.asJsonObject
            WorkoutExerciseFilter(
                id = filter.get("id").asString,
                name = filter.get("name").asString,
                values = filter.getAsJsonArray("values").orEmpty().map { value ->
                    val obj = value.asJsonObject
                    WorkoutExerciseFilterValue(obj.get("id").asString, obj.get("name").asString)
                },
            )
        }

    private fun parseExercises(root: JsonObject): List<WorkoutExercise> =
        root.getAsJsonArray("exercises").orEmpty().map { element ->
            val exercise = element.asJsonObject
            val filters = exercise.getAsJsonObject("filters")?.entrySet()
                ?.associate { (key, values) -> key to values.asJsonArray.map { it.asString }.toSet() }
                .orEmpty()
            parseExercise(
                id = exercise.get("id").asString,
                name = exercise.get("name").asString,
                lists = exercise.getAsJsonArray("lists").orEmpty().map { it.asString }.toSet(),
                filters = filters,
                json = exercise,
            )
        }

    private class CatalogData(
        val filters: List<WorkoutExerciseFilter>,
        val exercises: List<WorkoutExercise>,
    ) {
        val byId: Map<String, WorkoutExercise> = exercises.associateBy { it.id }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(JsonWorkoutExerciseCatalog::class.java)
    }
}

fun assetSource(path: String): () -> InputStream = { GBApplication.getContext().assets.open(path) }

private fun JsonArray?.orEmpty(): Iterable<JsonElement> = this ?: emptyList()
