package nodomain.freeyourgadget.gadgetbridge.model.workout

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map

class WorkoutViewModel : ViewModel() {
    private val _workouts = MutableLiveData<Map<Long, Workout>>(emptyMap())
    val workouts: LiveData<Map<Long, Workout>> get() = _workouts

    // Shared across all tabs, since each owns its own WorkoutValueFormatter instance; toggled by
    // a long-press on the workout header photo.
    private val _showRawData = MutableLiveData(false)
    val showRawData: LiveData<Boolean> get() = _showRawData

    fun setWorkout(workout: Workout, id: Long) {
        val current = _workouts.value?.toMutableMap() ?: mutableMapOf()
        if (current[id] != workout) {
            current[id] = workout
            _workouts.value = current
        }
    }

    fun getWorkout(id: Long): LiveData<Workout?> {
        return workouts.map { it[id] }
    }

    /**
     * Editors (rename, header photo, ...) mutate [Workout.summary] in place on the same
     * greenDAO entity instance rather than replacing it, so the [Workout] passed back to
     * [setWorkout] is reference-equal (and thus data-class-equal) to what's already stored,
     * and its dedup check silently drops the update. Call this after such an in-place edit to
     * force every tab observing this workout to re-render with the new field values.
     */
    fun refreshWorkout(id: Long) {
        val current = _workouts.value?.toMutableMap() ?: return
        val workout = current[id] ?: return
        current.remove(id)
        _workouts.value = current
        current[id] = workout
        _workouts.value = current
    }

    fun toggleRawData() {
        _showRawData.value = !(_showRawData.value ?: false)
    }

}