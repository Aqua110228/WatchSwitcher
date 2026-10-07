package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import org.slf4j.LoggerFactory
import java.util.Calendar

/**
 * The loading state of one configured widget instance.
 */
sealed interface WidgetState {
    data object Loading : WidgetState
    data class Ready(val data: Any?) : WidgetState
    data class Error(val cause: Throwable) : WidgetState
}

/**
 * Drives the dashboard's widget grid: loads the configured [WidgetInstance]s from
 * [WidgetLayoutStore], and for the selected day loads each instance's data through its own
 * [nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget.loadData].
 *
 * Each instance loads concurrently on [Dispatchers.IO] and publishes to [state] as soon as it's
 * ready.
 */
class DashboardViewModel : ViewModel() {
    private val _instances = MutableStateFlow<List<WidgetInstance>>(emptyList())
    val instances: StateFlow<List<WidgetInstance>> = _instances.asStateFlow()

    private val _state = MutableStateFlow<Map<String, WidgetState>>(emptyMap())
    val state: StateFlow<Map<String, WidgetState>> = _state.asStateFlow()

    private var refreshJob: Job? = null

    /**
     * Re-reads the layout from [WidgetLayoutStore] and reloads every instance for [day].
     */
    fun refresh(day: Calendar, dashboardShowAllDevices: Boolean, dashboardDeviceList: Set<String>) {
        refreshJob?.cancel()

        LOG.debug("Reloading all widgets for {}", DateTimeUtils.formatIso8601(day))

        val layout = WidgetLayoutStore.load()
        _instances.value = layout
        _state.value = layout.associate { it.instanceId to WidgetState.Loading }

        val timeTo = (day.timeInMillis / 1000L).toInt()
        val timeFrom = DateTimeUtils.shiftDays(timeTo, -1)
        val query = DashboardQuery(timeFrom, timeTo)

        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val jobs = layout.map { instance ->
                async {
                    loadInstance(instance, query, dashboardShowAllDevices, dashboardDeviceList)
                }
            }
            jobs.awaitAll()
            LOG.debug("Reloading finished")
        }
    }

    private suspend fun loadInstance(
        instance: WidgetInstance,
        query: DashboardQuery,
        dashboardShowAllDevices: Boolean,
        dashboardDeviceList: Set<String>,
    ) {
        val widget = WidgetRegistry.byId(instance.typeId)
        if (widget == null) {
            LOG.warn("Unknown widget type {} for instance {}", instance.typeId, instance.instanceId)
            publish(
                instance.instanceId,
                WidgetState.Error(IllegalStateException("Unknown widget type ${instance.typeId}"))
            )
            return
        }

        try {
            val prefs = Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId))
            val config = WidgetConfig(instance, prefs, dashboardShowAllDevices, dashboardDeviceList)
            val devices = config.resolveDevices(widget)
            val scope = WidgetDataScope(query, devices)
            val data = widget.loadData(scope, config)
            publish(instance.instanceId, WidgetState.Ready(data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.error("Failed to load data for widget instance {}", instance.instanceId, e)
            publish(instance.instanceId, WidgetState.Error(e))
        }
    }

    private fun publish(instanceId: String, newState: WidgetState) {
        _state.update { it + (instanceId to newState) }
    }

    override fun onCleared() {
        refreshJob?.cancel()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DashboardViewModel::class.java)
    }
}
