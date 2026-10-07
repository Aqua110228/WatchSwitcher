package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import com.google.gson.JsonObject
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.JsonWorkoutExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExercise
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.assetSource
import java.io.InputStream

/**
 * One Zepp OS exercise, identified on the JSON files by [actionType].
 */
class ZeppOsExercise(
    id: String,
    name: String,
    lists: Set<String>,
    filters: Map<String, Set<String>>,

    val actionType: Int,

    /**
     * The name the watch expects, which can differ from the displayed [name].
     */
    val actionName: String,

    val mainPositions: List<Int>,
    val subPositions: List<Int>,
) : WorkoutExercise(id, name, lists, filters)

/**
 * The lists of [ZeppOsExerciseCatalog].
 */
object ZeppOsExerciseLists {
    const val STRENGTH = "strength"
}

/**
 * The Zepp OS exercise catalog, read from `assets/workouts/exercises/zeppos.json`.
 */
class ZeppOsExerciseCatalog(source: () -> InputStream) : JsonWorkoutExerciseCatalog(source) {
    override fun parseExercise(
        id: String,
        name: String,
        lists: Set<String>,
        filters: Map<String, Set<String>>,
        json: JsonObject,
    ): WorkoutExercise = ZeppOsExercise(
        id,
        name,
        lists,
        filters,
        json.get("actionType").asInt,
        json.get("actionName").asString,
        json.getAsJsonArray("mainPositions").map { it.asInt },
        json.getAsJsonArray("subPositions").map { it.asInt },
    )

    fun exerciseFor(id: String?): ZeppOsExercise? = byId(id) as? ZeppOsExercise

    companion object {
        private const val ASSET = "workouts/exercises/zeppos.json"

        @JvmField
        val INSTANCE: ZeppOsExerciseCatalog = ZeppOsExerciseCatalog(assetSource(ASSET))
    }
}
