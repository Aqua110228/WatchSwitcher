package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed interface ChartUiState<out T> {
    data object Loading : ChartUiState<Nothing>
    data class Ready<T>(val data: T) : ChartUiState<T>
    data class Error(val cause: Throwable) : ChartUiState<Nothing>
}

class ChartViewModel<T> : ViewModel() {
    private val _state = MutableStateFlow<ChartUiState<T>>(ChartUiState.Loading)
    val state: StateFlow<ChartUiState<T>> = _state.asStateFlow()

    private var refreshJob: Job? = null

    /**
     * Cancels any other in-flight load, then runs [load] on [Dispatchers.IO] and publishes its result.
     */
    fun refresh(load: suspend (ChartDataScope) -> T) {
        refreshJob?.cancel()
        _state.value = ChartUiState.Loading
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = load(ChartDataScope())
                _state.value = ChartUiState.Ready(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load chart data", e)
                _state.value = ChartUiState.Error(e)
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ChartViewModel::class.java)
    }
}
