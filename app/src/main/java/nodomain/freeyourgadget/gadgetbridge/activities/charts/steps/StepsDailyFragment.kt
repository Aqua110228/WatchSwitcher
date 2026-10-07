package nodomain.freeyourgadget.gadgetbridge.activities.charts.steps

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartDataRange
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartsHost
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StepStreaksDashboard
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.AbstractVicoChartFragment
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartDataScope
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartTheme
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.ChartUiState
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vico.GbLineChart
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addStatTileGrid
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentStepsBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A day's cumulative steps / the daily goal.
 */
class StepsDailyFragment : AbstractVicoChartFragment<StepsDailyFragment.ScreenData>() {
    data class ScreenData(
        val totals: StepsDailyChartData.DailyTotals,
        val spec: ChartSpec,
    )

    private lateinit var binding: FragmentStepsBinding

    private var stepsGoal: Int = ActivityUser.defaultUserStepsGoal
    private var stepsColor: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentStepsBinding.inflate(layoutInflater, container, false)

        binding.root.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            chartsHost().enableSwipeRefresh(scrollY == 0)
        }

        bindChartComposeView(binding.stepsDailyChart)

        stepsColor = ContextCompat.getColor(requireContext(), R.color.steps_color)
        stepsGoal = GBApplication.getPrefs()
            .getInt(ActivityUser.PREF_USER_STEPS_GOAL, ActivityUser.defaultUserStepsGoal)

        binding.stepsStreaksButton.setOnClickListener {
            StepStreaksDashboard.newInstance(stepsGoal, chartsHost().device)
                .show(requireActivity().supportFragmentManager, "steps_streaks_dashboard")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state is ChartUiState.Ready) {
                        showTotals(state.data.totals)
                    }
                }
            }
        }

        return binding.root
    }

    override fun getTitle(): CharSequence = getString(R.string.steps)

    override fun isSingleDay(): Boolean = true

    override suspend fun loadChart(scope: ChartDataScope, host: ChartsHost): ScreenData {
        val day = Calendar.getInstance()
        day.time = host.endDate
        day.set(Calendar.HOUR_OF_DAY, 0)
        day.set(Calendar.MINUTE, 0)
        day.set(Calendar.SECOND, 0)
        val tsStart = (day.timeInMillis / 1000).toInt()
        val tsEnd = tsStart + 24 * 60 * 60 - 1

        val samples = scope.activitySamples(host.device, tsStart, tsEnd)
        val totals = StepsDailyChartData.buildDailyTotals(samples)
        val spec = StepsDailyChartData.buildChartSpec(samples, tsStart, stepsColor, stepsGoal)
        return ScreenData(totals, spec)
    }

    override fun isEmpty(data: ScreenData): Boolean = data.spec.isEmpty

    @Composable
    override fun RenderChart(data: ScreenData) {
        val context = LocalContext.current
        val theme = remember(context) { ChartTheme.from(context) }
        GbLineChart(spec = data.spec, theme = theme, modifier = Modifier)
    }

    private fun showTotals(totals: StepsDailyChartData.DailyTotals) {
        binding.stepsDateView.text = SimpleDateFormat("E, MMM dd", Locale.getDefault()).format(chartsHost().endDate)

        val width = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            GAUGE_WIDTH_DP,
            resources.displayMetrics,
        ).toInt()
        binding.stepsGauge.setImageBitmap(
            GaugeDrawer.drawCircleGauge(
                width,
                width / GAUGE_BAR_WIDTH_DIVISOR,
                stepsColor,
                totals.steps.toInt(),
                stepsGoal,
                requireContext(),
            )
        )

        binding.stepsStatsContainer.removeAllViews()
        val stats = listOf(
            StatTileData(NumberFormat.getInstance().format(totals.steps), getString(R.string.steps)),
            StatTileData(WorkoutValueFormatter().formatValue(totals.distanceKm, "km"), getString(R.string.distance)),
        )
        addStatTileGrid(binding.stepsStatsContainer, requireContext(), stats, 0)
    }

    override fun getAvailableDataRange(device: GBDevice, db: DBHandler): ChartDataRange? =
        ChartDataRange.ofActivitySamples(device.deviceCoordinator.getSampleProvider(device, db.daoSession))

    companion object {
        private const val GAUGE_WIDTH_DP = 300f
        private const val GAUGE_BAR_WIDTH_DIVISOR = 15
    }
}
