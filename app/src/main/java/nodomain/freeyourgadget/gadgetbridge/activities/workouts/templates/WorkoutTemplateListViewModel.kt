package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.database.repository.WorkoutTemplateRepository
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutSyncStatus
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateValidator

data class WorkoutTemplateListItem(
    val template: WorkoutTemplate,
    val syncStatus: WorkoutSyncStatus,

    /**
     * Whether the [WorkoutTemplateSpec] of the device accepts the template. An unsupported
     * template cannot be sent to the device.
     */
    val supported: Boolean,

    /**
     * The reason of the last sync failure, for [WorkoutSyncStatus.FAILED].
     */
    val syncError: String? = null,
)

/**
 * The templates of the device.
 */
class WorkoutTemplateListViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<WorkoutTemplateListItem>>(emptyList())
    val items: StateFlow<List<WorkoutTemplateListItem>> = _items.asStateFlow()

    fun refresh(device: GBDevice, spec: WorkoutTemplateSpec) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                WorkoutTemplateRepository.list(device)
                    .map { template ->
                        val status = WorkoutTemplateRepository.syncStatus(template, device)
                        val error = if (status == WorkoutSyncStatus.FAILED) template.syncError else null
                        WorkoutTemplateListItem(
                            template,
                            status,
                            WorkoutTemplateValidator.isSupported(template, spec),
                            error,
                        )
                    }
            }
            _items.value = loaded
        }
    }

    fun delete(templateId: Long, device: GBDevice, spec: WorkoutTemplateSpec) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { WorkoutTemplateRepository.delete(templateId) }
            refresh(device, spec)
        }
    }

    fun duplicate(templateId: Long, newName: String, device: GBDevice, spec: WorkoutTemplateSpec) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { WorkoutTemplateRepository.duplicate(templateId, newName) }
            refresh(device, spec)
        }
    }
}
