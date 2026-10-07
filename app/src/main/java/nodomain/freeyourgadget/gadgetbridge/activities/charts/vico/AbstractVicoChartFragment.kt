package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBFragment
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartDataRange
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartsHost
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import org.slf4j.LoggerFactory
import java.util.Date

/**
 * Base class for chart fragments backed by vico, hosted in a [ComposeView].
 * Mirrors AbstractChartFragment.
 */
abstract class AbstractVicoChartFragment<T> : AbstractGBFragment() {
    protected val viewModel: ChartViewModel<T> by viewModels()

    private val loadingHandler = Handler(Looper.getMainLooper())
    private var chartDirty = true

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val host = chartsHostOrNull() ?: return
            when (intent.action) {
                ChartsHost.REFRESH -> {
                    updateDateInfo(host)
                    refresh()
                }

                ChartsHost.DATE_NEXT_DAY -> handleDate(host, host.startDate, host.endDate, +1)
                ChartsHost.DATE_PREV_DAY -> handleDate(host, host.startDate, host.endDate, -1)
                ChartsHost.DATE_NEXT_WEEK -> handleDate(host, host.startDate, host.endDate, +7)
                ChartsHost.DATE_PREV_WEEK -> handleDate(host, host.startDate, host.endDate, -7)
                ChartsHost.DATE_NEXT_MONTH -> {
                    val time1 = DateTimeUtils.shiftMonths((host.startDate.time / 1000).toInt(), 1)
                    val time2 = DateTimeUtils.shiftMonths((host.endDate.time / 1000).toInt(), 1)
                    val date1 = DateTimeUtils.shiftByDays(Date(time1 * 1000L), 30)
                    val date2 = DateTimeUtils.shiftByDays(Date(time2 * 1000L), 30)
                    handleDate(host, date1, date2, -30)
                }

                ChartsHost.DATE_PREV_MONTH -> {
                    val time1 = DateTimeUtils.shiftMonths((host.startDate.time / 1000).toInt(), -1)
                    val time2 = DateTimeUtils.shiftMonths((host.endDate.time / 1000).toInt(), -1)
                    val date1 = DateTimeUtils.shiftByDays(Date(time1 * 1000L), -30)
                    val date2 = DateTimeUtils.shiftByDays(Date(time2 * 1000L), -30)
                    handleDate(host, date1, date2, 30)
                }
            }
        }
    }

    /**
     * Whether this chart shows data for a single day or a date range. Affects the date bar text.
     */
    protected open fun isSingleDay(): Boolean = true

    /**
     * Returns the time range of the available data for this chart, or null if it is not known.
     */
    open fun getAvailableDataRange(device: GBDevice, db: DBHandler): ChartDataRange? = null

    /**
     * Loads this chart's data. Runs on a background dispatcher, via [scope].
     */
    protected abstract suspend fun loadChart(scope: ChartDataScope, host: ChartsHost): T

    /**
     * Whether a successfully loaded [data] should be shown as "no data" instead of [RenderChart].
     */
    protected open fun isEmpty(data: T): Boolean = false

    /**
     * Renders a successfully loaded, non-empty [data]. Called on the UI thread.
     */
    @Composable
    protected abstract fun RenderChart(data: T)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter().apply {
            addAction(ChartsHost.REFRESH)
            addAction(ChartsHost.DATE_NEXT_DAY)
            addAction(ChartsHost.DATE_PREV_DAY)
            addAction(ChartsHost.DATE_NEXT_WEEK)
            addAction(ChartsHost.DATE_PREV_WEEK)
            addAction(ChartsHost.DATE_NEXT_MONTH)
            addAction(ChartsHost.DATE_PREV_MONTH)
        }
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(receiver, filter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).also(::bindChartComposeView)

    /**
     * Renders this fragment's chart state into [composeView]: the default [onCreateView] calls
     * this on the [ComposeView] it creates; a fragment with its own layout should call this (from
     * `onCreateView`, after inflating) on the `ComposeView` it found in that layout.
     */
    protected fun bindChartComposeView(composeView: ComposeView) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state) { onStateChanged(state) }
            ChartFrame(state)
        }
    }

    @Composable
    private fun ChartFrame(state: ChartUiState<T>) {
        when (state) {
            is ChartUiState.Loading -> Unit // the host's has a progress bar
            is ChartUiState.Error -> ChartMessage(chartNoDataText())
            is ChartUiState.Ready -> if (isEmpty(state.data)) {
                ChartMessage(chartNoDataText())
            } else {
                RenderChart(state.data)
            }
        }
    }

    private fun chartNoDataText(): String = getString(R.string.chart_no_data_synchronize)

    override fun onResume() {
        super.onResume()
        val host = chartsHostOrNull()
        if (host != null) {
            host.dateBar.visibility = View.VISIBLE
            updateDateInfo(host)
            if (chartDirty) {
                refresh()
            }
        }
    }

    override fun onDestroyView() {
        loadingHandler.removeCallbacksAndMessages(null)
        chartsHostOrNull()?.setLoading(false)
        super.onDestroyView()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(receiver)
        super.onDestroy()
    }

    protected fun chartsHost(): ChartsHost = requireActivity() as ChartsHost

    private fun chartsHostOrNull(): ChartsHost? = activity as? ChartsHost

    private fun onStateChanged(state: ChartUiState<T>) {
        val host = chartsHostOrNull() ?: return
        if (state is ChartUiState.Loading) {
            // Delay the loading slightly to prevent quick flashes on fast loading
            loadingHandler.postDelayed({ host.setLoading(true) }, LOADING_SPINNER_DELAY_MS)
        } else {
            loadingHandler.removeCallbacksAndMessages(null)
            host.setLoading(false)
        }
    }

    private fun handleDate(host: ChartsHost, startDate: Date, endDate: Date, offsetDays: Int) {
        if (isResumed) {
            if (!shiftDates(host, startDate, endDate, offsetDays)) {
                return
            }
            updateDateInfo(host)
        }
        refreshIfVisible()
    }

    private fun shiftDates(host: ChartsHost, startDate: Date, endDate: Date, offsetDays: Int): Boolean {
        var newStart = DateTimeUtils.shiftByDays(startDate, offsetDays)
        var newEnd = DateTimeUtils.shiftByDays(endDate, offsetDays)
        val now = Date()
        if (newEnd.after(now)) {
            // allow to jump to the end (now) if week/month reach after now
            newEnd = now
            newStart = DateTimeUtils.shiftByDays(now, -1)
        }
        return setDateRange(host, newStart, newEnd)
    }

    /**
     * Returns true if the shift was applied, and false if it was ignored (e.g. [to] is in the future).
     */
    private fun setDateRange(host: ChartsHost, from: Date, to: Date): Boolean {
        require(from <= to) { "Invalid date range: $from..$to" }
        val now = Date()
        if (to.after(now) || to.time / 10_000 == host.endDate.time / 10_000) {
            return false
        }
        host.startDate = from
        host.endDate = to
        return true
    }

    private fun refreshIfVisible() {
        if (isResumed) {
            chartsHostOrNull()?.let(::updateDateInfo)
            refresh()
        } else {
            chartDirty = true
        }
    }

    private fun refresh() {
        val host = chartsHostOrNull() ?: return
        if (host.device == null) {
            return
        }
        LOG.info("Refreshing data for {}", getTitle())
        chartDirty = false
        viewModel.refresh { scope -> loadChart(scope, host) }
    }

    private fun updateDateInfo(host: ChartsHost) {
        val dateFlags = DateUtils.FORMAT_SHOW_WEEKDAY
        val from = host.startDate
        val to = host.endDate
        host.setDateInfo(
            if (isSingleDay() || from == to) {
                DateTimeUtils.formatDate(to, dateFlags)
            } else {
                DateTimeUtils.formatDateRange(from, to, dateFlags)
            }
        )
    }

    companion object {
        private const val LOADING_SPINNER_DELAY_MS = 300L
        private val LOG = LoggerFactory.getLogger(AbstractVicoChartFragment::class.java)
    }
}
