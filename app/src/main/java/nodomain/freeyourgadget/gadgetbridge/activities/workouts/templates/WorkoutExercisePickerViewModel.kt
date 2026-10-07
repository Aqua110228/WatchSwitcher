package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExercise
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseFilter

/**
 * The exercises the picker shows, filtered by the search text and the checked filter values.
 * [load] reads the catalog on [Dispatchers.IO].
 */
class WorkoutExercisePickerViewModel : ViewModel() {
    private var allExercises: List<WorkoutExercise> = emptyList()
    private var query: String = ""

    /**
     * The checked value ids of each filter, by filter id. A filter with nothing checked is absent.
     */
    var checked: Map<String, Set<String>> = emptyMap()
        private set

    private val _filters = MutableStateFlow<List<WorkoutExerciseFilter>>(emptyList())

    /**
     * The filters and filter values that at least one exercise matches.
     */
    val filters: StateFlow<List<WorkoutExerciseFilter>> = _filters.asStateFlow()

    /**
     * Null while the catalog is still being read.
     */
    private val _exercises = MutableStateFlow<List<WorkoutExercise>?>(null)
    val exercises: StateFlow<List<WorkoutExercise>?> = _exercises.asStateFlow()

    fun load(catalog: WorkoutExerciseCatalog, list: String?) {
        if (allExercises.isNotEmpty()) return
        viewModelScope.launch {
            val (loaded, offered) = withContext(Dispatchers.IO) {
                val exercises = catalog.exercises(list)
                exercises to exerciseFilters(catalog.filters(list), exercises)
            }
            allExercises = loaded
            _filters.value = offered
            refresh()
        }
    }

    fun setQuery(newQuery: String) {
        query = newQuery
        refresh()
    }

    fun setChecked(newChecked: Map<String, Set<String>>) {
        checked = newChecked.filterValues { it.isNotEmpty() }
        refresh()
    }

    /**
     * The number of exercises that match [candidate] and the current search text.
     */
    fun count(candidate: Map<String, Set<String>>): Int = allExercises.count { isShown(it, candidate) }

    private fun refresh() {
        _exercises.value = allExercises.filter { isShown(it, checked) }
    }

    private fun isShown(exercise: WorkoutExercise, checked: Map<String, Set<String>>): Boolean {
        val trimmed = query.trim()
        return exercise.matches(checked) &&
            (trimmed.isEmpty() || exercise.name.contains(trimmed, ignoreCase = true))
    }

    private fun exerciseFilters(
        filters: List<WorkoutExerciseFilter>,
        exercises: List<WorkoutExercise>,
    ): List<WorkoutExerciseFilter> = filters.mapNotNull { filter ->
        val used = exercises.flatMapTo(mutableSetOf()) { it.filters[filter.id].orEmpty() }
        val values = filter.values.filter { it.id in used }
        if (values.isEmpty()) null else filter.copy(values = values)
    }
}
