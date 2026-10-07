package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ActivitySummariesChartFragment
import nodomain.freeyourgadget.gadgetbridge.activities.charts.DurationXLabelFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.charts.HeartRateZoneChartUtils
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts.ChartDataRepository
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts.WorkoutChartsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryGroup
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentWorkoutTabChartsBinding
import nodomain.freeyourgadget.gadgetbridge.entities.Device
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesResolver
import nodomain.freeyourgadget.gadgetbridge.model.workout.Workout
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutViewModel
import nodomain.freeyourgadget.gadgetbridge.util.GridTableBuilder
import org.apache.commons.lang3.tuple.Pair
import kotlin.collections.component1
import kotlin.collections.component2

class WorkoutTabChartsFragment : Fragment(), WorkoutTabScreenshotProvider {
    private lateinit var viewModel: WorkoutViewModel

    private val workoutValueFormatter = WorkoutValueFormatter()

    private lateinit var binding: FragmentWorkoutTabChartsBinding

    override val screenshotView: View get() = binding.root

    private var heartRateChartFragment: ActivitySummariesChartFragment? = null

    private var isFirstChartHeader = true

    private var latestWorkout: Workout? = null

    private val workoutId: Long by lazy {
        requireArguments().getLong("workoutId")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())
            .get(WorkoutViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWorkoutTabChartsBinding.inflate(inflater, container, false)

        heartRateChartFragment = ActivitySummariesChartFragment()
        childFragmentManager.beginTransaction()
            .replace(binding.chartsFragmentHolder.id, heartRateChartFragment!!)
            .commit()

        binding.dynamicCharts.removeAllViews()
        viewModel.getWorkout(workoutId).distinctUntilChanged().observe(viewLifecycleOwner) { workout ->
            workout ?: return@observe
            latestWorkout = workout
            workoutValueFormatter.setActivityKind(ActivityKind.fromCode(workout.summary.activityKind))
            renderWorkout(workout)
        }
        viewModel.showRawData.observe(viewLifecycleOwner) { showRawData ->
            workoutValueFormatter.setRawData(showRawData)
            latestWorkout?.let { renderWorkout(it) }
        }
        return binding.root
    }

    private fun renderWorkout(workout: Workout) {
        binding.dynamicCharts.removeAllViews()
        isFirstChartHeader = true
        val groupedEntries = ActivitySummaryGroup.buildGroupedList(workout.data)
        workout.charts.forEach { chart ->
            addChart(binding.dynamicCharts, true, chart, workout, groupedEntries)
        }
        updateHeartRateFallback(workout)
    }

    /**
     * A workout without its own heart rate chart (e.g. a manually logged activity, or a device
     * that doesn't record per-workout HR samples) can still show heart rate pulled from the
     * device's general health data for that time window, provided the device supports HR at all.
     */
    private fun updateHeartRateFallback(workout: Workout) {
        val hasOwnHeartRateChart = workout.charts.any { it.group == ActivitySummaryEntries.GROUP_HEART_RATE }
        val gbDevice = getGBDevice(workout.summary.device)
        if (hasOwnHeartRateChart || !gbDevice.deviceCoordinator.supportsHeartRateMeasurement(gbDevice)) {
            binding.heartRateChartWrapper.visibility = View.GONE
            return
        }

        binding.heartRateChartWrapper.visibility = View.VISIBLE
        heartRateChartFragment?.setDateAndGetData(
            workout.summary,
            gbDevice,
            workout.summary.startTime.time / 1000,
            workout.summary.endTime.time / 1000
        )
    }

    private fun getGBDevice(device: Device): GBDevice {
        return GBApplication.app().deviceManager.devices
            .first { it.address.equals(device.identifier, ignoreCase = true) }
    }

    /** The workout's custom label, falling back to its sport/activity kind name. */
    private fun workoutLabel(): String? {
        val summary = latestWorkout?.summary ?: return null
        return summary.name?.takeIf { it.isNotBlank() }
            ?: summary.activityKind.let { ActivityKind.fromCode(it).getLabel(requireContext()) }
    }

    @Suppress("KotlinConstantConditions")
    private fun addChart(
        chartsLayout: LinearLayout,
        includeHeader: Boolean,
        chart: WorkoutChart,
        workout: Workout,
        groupedEntries: Map<String, List<Pair<String, ActivitySummaryEntry>>>
    ) {
        if (includeHeader) {
            addSectionHeader(chartsLayout, requireContext(), chart.title, showDivider = !isFirstChartHeader)
            isFirstChartHeader = false
        }

        // Basic avg/max info for this chart's metric, shown above it.
        CHART_STAT_KEYS[chart.group]?.let { statKeys ->
            val statEntries = groupedEntries[chart.group].orEmpty().filter { (key, _) -> key in statKeys }
            val stats = statEntries.mapNotNull { (key, entry) ->
                (entry as? ActivitySummarySimpleEntry)?.takeIf { it.value != null }?.let {
                    StatTileData(workoutValueFormatter.formatTileValue(it.value, it.unit), workoutValueFormatter.getStringResourceByName(key))
                }
            }
            if (stats.isNotEmpty()) {
                // dynamicCharts already has 16dp of its own container padding; the chart title
                // header adds another 16dp on top of that, so match that same inset here.
                addStatTileGrid(chartsLayout, requireContext(), stats, horizontalMarginDp = 16)
            }
        }

        val chartsFragmentHolder = FrameLayout(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * resources.displayMetrics.density).toInt()
            ).apply {
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
        }

        val chartTextColor = GBApplication.getSecondaryTextColor(context)
        val lineChart: BarLineChartBase<*> = when (chart.chartData) {
            is ScatterData -> ScatterChart(requireContext())
            else -> LineChart(requireContext())
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            legend.textColor = GBApplication.getTextColor(context)
            isScaleXEnabled = false
            isScaleYEnabled = false
            isHighlightPerDragEnabled = false
            isHighlightPerTapEnabled = false
            isDragEnabled = false
        }
        lineChart.xAxis.apply {
            setDrawLabels(true)
            setDrawGridLines(false)
            setDrawLimitLinesBehindData(true)
            isEnabled = true
            textColor = chartTextColor
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = DurationXLabelFormatter()
        }
        lineChart.axisLeft.apply {
            setDrawGridLines(false)
            setDrawTopYLabelEntry(true)
            textColor = chartTextColor
            isEnabled = true
            if (chart.chartYLabelFormatter != null) {
                valueFormatter = chart.chartYLabelFormatter;
            }
        }
        lineChart.axisRight.apply {
            isEnabled = false
        }
        chart.zoneThresholds?.let { zones ->
            lineChart.axisLeft.setDrawLimitLinesBehindData(true)
            for ((zoneIdx, hr) in listOf(2 to zones.zone2, 3 to zones.zone3, 4 to zones.zone4, 5 to zones.zone5)) {
                if (hr <= 0) continue
                lineChart.axisLeft.addLimitLine(LimitLine(hr.toFloat()).apply {
                    lineColor = HeartRateZonesResolver.colorForZone(requireContext(), zoneIdx)
                    lineWidth = 0.7f
                    enableDashedLine(6f, 6f, 0f)
                })
            }
        }
        chart.lineChart(lineChart);
        when {
            lineChart is LineChart && chart.chartData is LineData -> {
                lineChart.data = chart.chartData
            }
            lineChart is ScatterChart && chart.chartData is ScatterData -> {
                lineChart.data = chart.chartData
            }
        }
        lineChart.description.isEnabled = false;
        lineChart.onChartGestureListener = object : OnChartGestureListener {
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartSingleTapped(me: MotionEvent?) {
                ChartDataRepository.chartData = workout.charts
                val intent = Intent(requireContext(), WorkoutChartsActivity::class.java).apply {
                    putExtra(WorkoutChartsActivity.INIT_CHART_ID, chart.id)
                    workoutLabel()?.let {
                        putExtra(WorkoutChartsActivity.EXTRA_TITLE, "${getString(R.string.charts)} · $it")
                    }
                }
                startActivity(intent)
            }
            override fun onChartDoubleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
        }
        lineChart.invalidate()
        chartsFragmentHolder.addView(lineChart)

        chartsLayout.addView(chartsFragmentHolder)

        // Heart rate zones go directly under the heart rate chart.
        if (chart.group == ActivitySummaryEntries.GROUP_HEART_RATE) {
            val zoneEntries = groupedEntries[ActivitySummaryEntries.GROUP_HEART_RATE_ZONES].orEmpty()
            val zoneSeconds = chart.secondsInZone
            val zoneThresholds = chart.zoneThresholds
            if (zoneEntries.isNotEmpty()) {
                addSectionHeader(chartsLayout, requireContext(), getString(R.string.workout_time_in_zones), showDivider = !isFirstChartHeader)
                isFirstChartHeader = false
                addStatRow(chartsLayout, zoneEntries)
            } else if (zoneSeconds != null && zoneThresholds != null && (1..5).sumOf { zoneSeconds[it] } > 0) {
                // The device reported no time in zones, so show the split computed from the HR samples.
                addSectionHeader(chartsLayout, requireContext(), getString(R.string.workout_time_in_zones), showDivider = !isFirstChartHeader)
                isFirstChartHeader = false
                HeartRateZoneChartUtils.populateZoneSummary(requireContext(), chartsLayout, zoneSeconds, zoneThresholds)
            }
        }
    }

    private fun addStatRow(chartsLayout: LinearLayout, entries: List<Pair<String, ActivitySummaryEntry>>) {
        val gridTableBuilder = GridTableBuilder(requireContext(), workoutValueFormatter)
        for ((key, entry) in entries) {
            gridTableBuilder.addEntry(workoutValueFormatter.getStringResourceByName(key), entry)
        }
        val grid = gridTableBuilder.build().apply {
            // GridTableBuilder paints its own background behind the cells and relies on small
            // cell margins to reveal it as divider lines between rows - only used here for the
            // heart rate zone progress bars, which don't need that grid look, so clear it.
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (16 * resources.displayMetrics.density).toInt()
        }
        chartsLayout.addView(grid, params)
    }

    companion object {
        /** Which avg/min/max entries to surface above each chart, keyed by [WorkoutChart.group]. */
        private val CHART_STAT_KEYS: Map<String, List<String>> = mapOf(
            ActivitySummaryEntries.GROUP_HEART_RATE to listOf(
                ActivitySummaryEntries.HR_AVG,
                ActivitySummaryEntries.HR_MAX,
            ),
            ActivitySummaryEntries.GROUP_SPEED to listOf(
                // Only one of pace/speed will actually be present for a given workout.
                ActivitySummaryEntries.PACE_AVG_SECONDS_KM,
                ActivitySummaryEntries.PACE_MAX,
                ActivitySummaryEntries.SPEED_AVG,
                ActivitySummaryEntries.SPEED_MAX,
            ),
            ActivitySummaryEntries.GROUP_ELEVATION to listOf(
                ActivitySummaryEntries.ALTITUDE_MIN,
                ActivitySummaryEntries.ALTITUDE_MAX,
            ),
            ActivitySummaryEntries.GROUP_POWER to listOf(
                ActivitySummaryEntries.AVG_POWER,
                ActivitySummaryEntries.MAX_POWER,
            ),
            ActivitySummaryEntries.GROUP_CADENCE to listOf(
                ActivitySummaryEntries.CADENCE_AVG,
                ActivitySummaryEntries.CADENCE_MAX,
            ),
            ActivitySummaryEntries.GROUP_RESPIRATORY_RATE to listOf(
                ActivitySummaryEntries.RESPIRATION_AVG,
                ActivitySummaryEntries.RESPIRATION_MAX,
            ),
            ActivitySummaryEntries.GROUP_TEMPERATURE to listOf(
                ActivitySummaryEntries.TEMPERATURE_MIN,
                ActivitySummaryEntries.TEMPERATURE_MAX,
            ),
        )
    }
}
