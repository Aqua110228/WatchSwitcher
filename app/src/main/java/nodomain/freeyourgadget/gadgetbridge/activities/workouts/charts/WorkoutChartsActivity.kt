package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.children
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.LineScatterCandleRadarDataSet
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.github.mikephil.charting.formatter.DefaultAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.DurationXLabelFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.charts.HeartRateZoneChartUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.marker.ValueMarker
import nodomain.freeyourgadget.gadgetbridge.databinding.WorkoutChartsBinding
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesResolver
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart

class WorkoutChartsActivity : AbstractGBActivity(), MenuProvider {

    private var context: Context = GBApplication.getContext()
    private lateinit var binding: WorkoutChartsBinding
    private var chartData: List<WorkoutChart>? = null
    val selectedCharts = mutableListOf<Any>()

    // Selectable overlays drawn on top of the compared charts.
    private var showHrZones = false

    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WorkoutChartsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        addMenuProvider(this)
        intent.getStringExtra(EXTRA_TITLE)?.let { supportActionBar?.title = it }
        chartData = ChartDataRepository.chartData

        if (chartData == null) {
            Toast.makeText(this, "No charts data found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val chartTextColor = GBApplication.getSecondaryTextColor(context)
        binding.workoutDataChart.xAxis.apply {
            setDrawLabels(true)
            setDrawGridLines(false)
            setDrawLimitLinesBehindData(true)
            isEnabled = true
            textColor = chartTextColor
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = DurationXLabelFormatter()
        }
        binding.workoutDataChart.axisLeft.apply {
            setDrawGridLines(false)
            setDrawTopYLabelEntry(true)
            textColor = chartTextColor
            isEnabled = true
        }
        binding.workoutDataChart.axisRight.apply {
            setDrawGridLines(false)
            setDrawTopYLabelEntry(true)
            textColor = chartTextColor
            isEnabled = true
        }
        binding.workoutDataChart.description.isEnabled = false
        binding.workoutDataChart.legend.textColor = GBApplication.getTextColor(context)

        val initChartId = intent.getStringExtra(INIT_CHART_ID) ?: "none"
        selectedCharts.add(0, initChartId)
        setupChipGroup(binding.workoutDataChartChipGroup, initChartId)
        setupOverlayChips(binding.workoutDataChartOverlayChipGroup)
        refreshChart()
    }

    override fun onDestroy() {
        ChartDataRepository.clear()
        super.onDestroy()
    }

    fun setupChipGroup(chipGroup: ChipGroup, initChartId: String) {
        for (chart in chartData!!) {
            val chip = (layoutInflater.inflate(R.layout.layout_chart_chip, chipGroup, false) as Chip).apply {
                text = chart.title
                isCheckable = true
                isClickable = true
                tag = chart.id
                isChecked = chart.id == initChartId
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                val tag = chip.tag
                val checkedCount = binding.workoutDataChartChipGroup.children
                    .filterIsInstance<Chip>()
                    .count { it.isChecked }
                if (isChecked) {
                    if (checkedCount > 2) {
                        chip.isChecked = false
                        Toast.makeText(this, context.getString(R.string.charts_two_items_only), Toast.LENGTH_SHORT).show()
                    } else {
                        selectedCharts.add(tag)
                        refreshChart()
                    }
                } else {
                    if (checkedCount == 0) {
                        chip.isChecked = true
                        Toast.makeText(this, context.getString(R.string.charts_at_least_one_item), Toast.LENGTH_SHORT).show()
                    } else {
                        selectedCharts.remove(tag)
                        refreshChart()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    fun chipUpdate(tag: String, checked: Boolean) {
        val chip = binding.workoutDataChartChipGroup.children
            .filterIsInstance<Chip>()
            .firstOrNull { it.tag == tag }
        chip?.isChecked = checked
    }

    private fun setupOverlayChips(group: ChipGroup) {
        // HR-zone bands: only offered when an HR chart with resolved thresholds is present.
        val hrChart = chartData?.find { it.id == HR_CHART_ID }
        if (hrChart?.zoneThresholds != null) {
            val chip = Chip(this).apply {
                text = context.getString(R.string.HeartRateZones)
                isCheckable = true
                isClickable = true
                isChecked = false
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                showHrZones = isChecked
                refreshChart()
            }
            group.addView(chip)
        }
    }

    fun refreshChart() {
        val combinedData = CombinedData()
        val lineData = LineData()
        val scatterData = ScatterData()
        // Keyed by dataset label rather than position: a metric split into several
        // segments contributes multiple datasets that all share one label.
        val markerFormatters = mutableMapOf<String, ValueFormatter?>()
        val markerUnits = mutableMapOf<String, String?>()
        val legendEntries = mutableListOf<LegendEntry>()
        // Limit lines live on the axis objects and survive a data swap, so clear them before
        // the overlays below re-add them.
        binding.workoutDataChart.xAxis.removeAllLimitLines()
        binding.workoutDataChart.axisLeft.removeAllLimitLines()
        binding.workoutDataChart.axisRight.removeAllLimitLines()
        var leftY = true
        var hrAxis: YAxis.AxisDependency? = null
        // Metric lines are collected first, then added AFTER the zone bands so the bands stay in the
        // background and never hide the lines or the other overlays.
        val metricLineSets = mutableListOf<LineDataSet>()
        selectedCharts.forEach { selectedChart ->
            val workoutChart = chartData?.find { it.id == selectedChart } ?: return@forEach
            val axisDependency = if (leftY) YAxis.AxisDependency.LEFT else YAxis.AxisDependency.RIGHT
            if (workoutChart.id == HR_CHART_ID) {
                hrAxis = axisDependency
            }
            var legendAdded = false
            workoutChart.chartData.dataSets.forEach { rawDataSet ->
                // Zone bands travel inside the HR chart's data; here they are an optional overlay,
                // added below by addHrZoneOverlay.
                if (rawDataSet is HeartRateZoneChartUtils.ZoneAreaDataSet) return@forEach
                val dataSet = rawDataSet as? LineScatterCandleRadarDataSet<Entry> ?: return@forEach
                dataSet.highLightColor = ContextCompat.getColor(context, R.color.chart_highline_dolor)
                dataSet.highlightLineWidth = 1f
                dataSet.axisDependency = axisDependency
                when (dataSet) {
                    is LineDataSet -> metricLineSets.add(dataSet)
                    is ScatterDataSet -> scatterData.addDataSet(dataSet)
                    else -> return@forEach
                }
                // Only the first segment of a gapped series is labelled; ValueMarker resolves the
                // following ones to it.
                dataSet.label?.let {
                    markerFormatters[it] = workoutChart.chartYLabelFormatter
                    markerUnits[it] = workoutChart.unitString
                }
                if (!legendAdded) {
                    legendEntries.add(
                        LegendEntry(
                            dataSet.label,
                            dataSet.form,
                            dataSet.formSize,
                            dataSet.formLineWidth,
                            dataSet.formLineDashEffect,
                            dataSet.color
                        )
                    )
                    legendAdded = true
                }
            }
            val axis = if (leftY) binding.workoutDataChart.axisLeft else binding.workoutDataChart.axisRight
            axis.valueFormatter = workoutChart.chartYLabelFormatter ?: DefaultAxisValueFormatter(0)
            leftY = false
        }
        // Zone bands first (background), then the metric lines on top.
        if (showHrZones) {
            addHrZoneOverlay(lineData, hrAxis)
        }
        for (lineSet in metricLineSets) {
            lineData.addDataSet(lineSet)
        }
        if (selectedCharts.size == 1) {
            val selectedChartId = selectedCharts.first()
            val workoutChart = chartData?.find { it.id == selectedChartId } ?: return
            binding.workoutDataChart.axisRight.valueFormatter = workoutChart.chartYLabelFormatter ?: DefaultAxisValueFormatter(0)
        }
        binding.workoutDataChart.legend.setCustom(legendEntries)
        combinedData.setData(lineData)
        combinedData.setData(scatterData)
        binding.workoutDataChart.data = combinedData
        binding.workoutDataChart.marker = ValueMarker(this, combinedData, markerFormatters, markerUnits)
        binding.workoutDataChart.highlightValues(null)
        binding.workoutDataChart.invalidate()
    }

    /**
     * Draws the HR-zone bands (and dashed threshold lines) on the HR line's axis. No-op unless the
     * HR chart is among the selected charts, since the bands are only meaningful at the HR scale.
     */
    private fun addHrZoneOverlay(lineData: LineData, axis: YAxis.AxisDependency?) {
        if (axis == null) return
        val hrChart = chartData?.find { it.id == HR_CHART_ID } ?: return
        val zones = hrChart.zoneThresholds ?: return
        // The HR line is split into one dataset per gap, and the same chart data also holds the
        // zone bands, so gather the entries of every HR segment.
        val hrEntries = ArrayList<Entry>()
        for (hrDataSet in hrChart.chartData.dataSets) {
            if (hrDataSet is HeartRateZoneChartUtils.ZoneAreaDataSet) continue
            for (i in 0 until hrDataSet.entryCount) {
                hrEntries.add(hrDataSet.getEntryForIndex(i))
            }
        }
        if (hrEntries.isEmpty()) return
        val chartMax = maxOf(HeartRateUtils.getInstance().maxHeartRate, zones.zone5 + 1)
        for (area in HeartRateZoneChartUtils.buildZoneAreas(context, zones, hrEntries, chartMax, axis)) {
            lineData.addDataSet(area)
        }
        val axisObj = if (axis == YAxis.AxisDependency.LEFT) {
            binding.workoutDataChart.axisLeft
        } else {
            binding.workoutDataChart.axisRight
        }
        axisObj.setDrawLimitLinesBehindData(true)
        for ((zoneIdx, hr) in listOf(2 to zones.zone2, 3 to zones.zone3, 4 to zones.zone4, 5 to zones.zone5)) {
            if (hr <= 0) continue
            val limit = LimitLine(hr.toFloat())
            limit.lineColor = HeartRateZonesResolver.colorForZone(context, zoneIdx)
            limit.lineWidth = 0.7f
            limit.enableDashedLine(6f, 6f, 0f)
            axisObj.addLimitLine(limit)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        this.menu = menu
        getMenuInflater().inflate(R.menu.workout_charts_menu, menu)
        setMenuFilterItemVisibility(getResources().configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    }

    fun setMenuFilterItemVisibility(visibility: Boolean) {
        menu?.findItem(R.id.action_filter_charts)?.isVisible = visibility
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_rotate_screen -> {
                val currentOrientation = getResources().configuration.orientation
                if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    setMenuFilterItemVisibility(true)
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                    setMenuFilterItemVisibility(false)
                }
                true
            }
            R.id.action_filter_charts -> {
                showFiltersDialog()
                true
            }

            android.R.id.home -> {
                // back button
                finish()
                true
            }

            else -> false
        }
    }

    private fun showFiltersDialog() {
        val items = chartData!!.map { it.title }.toTypedArray()
        val ids = chartData!!.map { it.id }
        val checkedItems = BooleanArray(items.size) { index ->
            selectedCharts.contains(ids[index])
        }
        val selectedIndices = mutableSetOf<Int>()
        checkedItems.forEachIndexed { index, isChecked ->
            if (isChecked) selectedIndices.add(index)
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setMultiChoiceItems(items, checkedItems, null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            val listView = dialog.listView
            listView.setOnItemClickListener { _, _, which, _ ->
                val isChecked = listView.isItemChecked(which)
                if (isChecked) {
                    if (selectedIndices.size == 2) {
                        listView.setItemChecked(which, false)
                        Toast.makeText(this, context.getString(R.string.charts_two_items_only), Toast.LENGTH_SHORT).show()
                    } else {
                        selectedIndices.add(which)
                        chipUpdate(ids[which], true)
                    }
                } else {
                    if (selectedIndices.size == 1) {
                        listView.setItemChecked(which, true)
                        Toast.makeText(this, context.getString(R.string.charts_at_least_one_item), Toast.LENGTH_SHORT).show()
                    } else {
                        selectedIndices.remove(which)
                        chipUpdate(ids[which], false)
                    }
                }
            }
        }
        dialog.show()
    }

    companion object {
        const val INIT_CHART_ID = "INIT_CHART_ID"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        private const val HR_CHART_ID = "heart_rate"
    }
}
