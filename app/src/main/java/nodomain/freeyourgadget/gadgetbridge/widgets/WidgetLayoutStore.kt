package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized

/**
 * The JSON-serializable shape of one configured widget.
 */
@GsonSerialized
private data class LayoutEntry(val id: String, val type: String, val cols: Int)

/**
 * Persists the ordered list of configured dashboard widgets ([WidgetInstance]s) as JSON in one
 * preference, and each instance's own settings in its own SharedPreferences file (see
 * [GBApplication.getWidgetSharedPrefs]).
 */
object WidgetLayoutStore {
    private val LOG: Logger = LoggerFactory.getLogger(WidgetLayoutStore::class.java)

    private const val PREF_LAYOUT = "pref_dashboard_layout"
    private const val PREF_LEGACY_ORDER = "pref_dashboard_widgets_order"

    /**
     * The default widgets, in order.
     */
    private val DEFAULT_ORDER = listOf(
        "today",
        "goals",
        "steps",
        "distance",
        "activetime",
        "sleep",
        "bodyenergy",
        "stress_segmented",
        "hrv",
        "bloodpressure",
        "vo2max",
        "calories_active",
        "calories_segmented",
    )

    private val gson = Gson()
    private val listType = object : TypeToken<List<LayoutEntry>>() {}.type

    fun load(): List<WidgetInstance> {
        migrateIfNeeded()

        val json = GBApplication.getPrefs().getString(PREF_LAYOUT, "")
        if (json.isNullOrBlank()) return emptyList()

        return try {
            val entries: List<LayoutEntry>? = gson.fromJson(json, listType)
            entries.orEmpty().map { WidgetInstance(it.id, it.type, it.cols) }
        } catch (e: Exception) {
            LOG.error("Failed to parse widget layout, treating as empty", e)
            emptyList()
        }
    }

    fun save(instances: List<WidgetInstance>) {
        val entries = instances.map { LayoutEntry(it.instanceId, it.typeId, it.columns) }
        sharedPreferences().edit {
            putString(PREF_LAYOUT, gson.toJson(entries))
        }
    }

    /**
     * Adds a new instance of [typeId] at the end of the layout and returns it.
     */
    fun add(typeId: String): WidgetInstance {
        val widget = WidgetRegistry.byId(typeId)
        val instance = WidgetInstance(
            instanceId = newInstanceId(),
            typeId = typeId,
            columns = widget?.defaultColumns ?: 1,
        )
        save(load() + instance)
        return instance
    }

    /**
     * Duplicates [instanceId] (settings included) right after itself. Returns the copy, if the original was found.
     */
    fun duplicate(instanceId: String): WidgetInstance? {
        val list = load()
        val index = list.indexOfFirst { it.instanceId == instanceId }
        if (index < 0) return null

        val copy = list[index].copy(instanceId = newInstanceId())

        val source = GBApplication.getWidgetSharedPrefs(instanceId)
        val dest = GBApplication.getWidgetSharedPrefs(copy.instanceId)
        if (source != null && dest != null) {
            copyAllPrefs(source, dest) { it }
        }

        val newList = list.toMutableList()
        newList.add(index + 1, copy)
        save(newList)
        return copy
    }

    /**
     * Removes [instanceId] from the layout and clears its settings file.
     */
    fun remove(instanceId: String) {
        save(load().filterNot { it.instanceId == instanceId })
        GBApplication.deleteWidgetSharedPrefs(instanceId)
    }

    /**
     * Moves the instance at [from] to [to] within the layout.
     */
    fun move(from: Int, to: Int) {
        val list = load().toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        save(list)
    }

    /**
     * Updates [instanceId]'s column span in the layout. Column span is a layout-level property,
     * not a shared preference.
     */
    fun setColumns(instanceId: String, columns: Int) {
        val list = load()
        val index = list.indexOfFirst { it.instanceId == instanceId }
        if (index < 0) return
        val updated = list.toMutableList()
        updated[index] = updated[index].copy(columns = columns)
        save(updated)
    }

    private fun newInstanceId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun sharedPreferences(): SharedPreferences = GBApplication.getPrefs().preferences

    /**
     * Copies every entry of [source] into [dest], transforming each key with [keyTransform] and
     * skipping entries the transform rejects (returns null for). Preserves each value's runtime
     * type (String/boolean/int/long/float/String set), the same types SharedPreferences supports.
     */
    private fun copyAllPrefs(source: SharedPreferences, dest: SharedPreferences, keyTransform: (String) -> String?) {
        dest.edit {
            for ((key, value) in source.all) {
                val destKey = keyTransform(key) ?: continue
                when (value) {
                    is Boolean -> putBoolean(destKey, value)
                    is String -> putString(destKey, value)
                    is Int -> putInt(destKey, value)
                    is Long -> putLong(destKey, value)
                    is Float -> putFloat(destKey, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(destKey, value as Set<String>)
                    }
                }
            }
        }
    }

    //
    // One-time migration from the pre-registry dashboard preferences
    //

    private fun migrateIfNeeded() {
        val prefs = GBApplication.getPrefs()
        if (prefs.contains(PREF_LAYOUT)) return

        LOG.info("Migrating legacy dashboard widget preferences to the widget layout store")

        val order = prefs.getString(PREF_LEGACY_ORDER, DEFAULT_ORDER.joinToString(","))
            .split(",")
            .filter { it.isNotBlank() }

        val entries = order.map { typeId ->
            val columns = when (typeId) {
                "today" -> if (prefs.getBoolean("dashboard_widget_today_2columns", true)) 2 else 1
                "goals" -> if (prefs.getBoolean("dashboard_widget_goals_2columns", true)) 2 else 1
                else -> 1
            }
            LayoutEntry(id = typeId, type = typeId, cols = columns)
        }

        sharedPreferences().edit {
            putString(PREF_LAYOUT, gson.toJson(entries))
        }

        // The old per-widget options were global preferences, e.g. "dashboard_widget_today_24h".
        // Copy each family into its instance's own settings file, dropping the widget prefix so
        // the key matches what TodayWidget/GoalsWidget declare via `settings()`.
        val defaultPrefs = sharedPreferences()
        if (order.contains("today")) {
            GBApplication.getWidgetSharedPrefs("today")?.let { dest ->
                copyAllPrefs(defaultPrefs, dest) { key ->
                    key.removePrefix("dashboard_widget_today_").takeIf { it != key }
                }
            }
        }
        if (order.contains("goals")) {
            GBApplication.getWidgetSharedPrefs("goals")?.let { dest ->
                copyAllPrefs(defaultPrefs, dest) { key ->
                    key.removePrefix("dashboard_widget_goals_").takeIf { it != key }
                }
            }
        }
    }
}
