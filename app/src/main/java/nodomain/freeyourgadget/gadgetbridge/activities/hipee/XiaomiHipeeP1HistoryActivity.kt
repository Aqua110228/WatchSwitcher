/*  Copyright (C) 2026 David Giron

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.hipee

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiHipeeP1Reading
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiHipeeP1ReadingDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Local, device-scoped P1 history. The P1 protocol has not established degree values or bad-posture
 * thresholds, so this screen visualizes only the raw reading fields retained from the device.
 */
class XiaomiHipeeP1HistoryActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var dateView: TextView
    private lateinit var totalPositiveMeasurementsView: TextView
    private lateinit var forwardCountView: TextView
    private lateinit var leftCountView: TextView
    private lateinit var rightCountView: TextView
    private lateinit var chart: BarChart
    private var chartHours: Array<HourCounts> = emptyArray()
    private var deviceId = 0L
    private val selectedDay: Calendar = Calendar.getInstance().apply { clearTime() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        device = requireNotNull(intent.getDevice())
        setContentView(R.layout.activity_xiaomi_hipee_p1_history)
        supportActionBar?.apply {
            setTitle(R.string.mi_hipee_p1_position)
            setDisplayHomeAsUpEnabled(true)
        }

        dateView = findViewById(R.id.hipee_p1_history_date)
        totalPositiveMeasurementsView = findViewById(R.id.hipee_p1_history_total_corrections)
        forwardCountView = findViewById(R.id.hipee_p1_history_forward_count)
        leftCountView = findViewById(R.id.hipee_p1_history_left_count)
        rightCountView = findViewById(R.id.hipee_p1_history_right_count)
        chart = findViewById(R.id.hipee_p1_history_chart)
        setupChart()

        findViewById<View>(R.id.hipee_p1_history_previous_month).setOnClickListener { changeDate(Calendar.MONTH, -1) }
        findViewById<View>(R.id.hipee_p1_history_previous_week).setOnClickListener { changeDate(Calendar.WEEK_OF_YEAR, -1) }
        findViewById<View>(R.id.hipee_p1_history_previous_day).setOnClickListener { changeDate(Calendar.DAY_OF_YEAR, -1) }
        findViewById<View>(R.id.hipee_p1_history_next_day).setOnClickListener { changeDate(Calendar.DAY_OF_YEAR, 1) }
        findViewById<View>(R.id.hipee_p1_history_next_week).setOnClickListener { changeDate(Calendar.WEEK_OF_YEAR, 1) }
        findViewById<View>(R.id.hipee_p1_history_next_month).setOnClickListener { changeDate(Calendar.MONTH, 1) }

        try {
            GBApplication.acquireDbReadOnly().use { db ->
                deviceId = requireNotNull(DBHelper.getDevice(device, db.daoSession).id)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Unable to open P1 history database", e)
        }
        // Calendar.getInstance() makes today the initial, selected date rather than only its month.
        draw()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_xiaomi_hipee_p1_history, menu)
        if (!device.isConnected || !device.deviceCoordinator.supportsDataFetching(device)) {
            menu.removeItem(R.id.hipee_p1_history_sync)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.hipee_p1_history_sync -> {
            fetchRecordedData()
            true
        }
        R.id.hipee_p1_history_set_date -> {
            showDatePicker()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun fetchRecordedData() {
        if (device.isInitialized) {
            GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_ACTIVITY)
        } else {
            GB.toast(this, getString(R.string.device_not_connected), Toast.LENGTH_SHORT, GB.ERROR)
        }
    }

    private fun showDatePicker() {
        val month = (selectedDay.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        fun renderMonth() {
            content.removeAllViews()
            val monthStart = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val activityDays = readActivityDays(monthStart)
            val header = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val previous = Button(this).apply { text = "<" }
            val title = TextView(this).apply {
                gravity = Gravity.CENTER
                text = String.format(Locale.getDefault(), "%tB %tY", monthStart, monthStart)
                textSize = 18f
            }
            val next = Button(this).apply { text = ">" }
            header.addView(previous, LinearLayout.LayoutParams(dp(48), dp(48)))
            header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
            header.addView(next, LinearLayout.LayoutParams(dp(48), dp(48)))
            content.addView(header)

            val weekdays = GridLayout(this).apply {
                columnCount = 7
                useDefaultMargins = false
            }
            val firstDay = monthStart.firstDayOfWeek
            repeat(7) { index ->
                val day = ((firstDay - 1 + index) % 7) + 1
                val label = TextView(this).apply {
                    gravity = Gravity.CENTER
                    text = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, day) }
                        .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                }
                weekdays.addView(label, GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(32)
                    columnSpec = GridLayout.spec(index, 1f)
                })
            }
            content.addView(weekdays)

            val days = GridLayout(this).apply {
                columnCount = 7
                rowCount = 6
                useDefaultMargins = false
            }
            val offset = (monthStart.get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7
            val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
            repeat(42) { index ->
                val dayOfMonth = index - offset + 1
                val cell = FrameLayout(this)
                if (dayOfMonth in 1..daysInMonth) {
                    val day = TextView(this).apply {
                        gravity = Gravity.CENTER
                        text = dayOfMonth.toString()
                        setOnClickListener {
                            selectedDay.set(monthStart.get(Calendar.YEAR), monthStart.get(Calendar.MONTH), dayOfMonth)
                            selectedDay.clearTime()
                            dialog.dismiss()
                            draw()
                        }
                    }
                    cell.addView(day, FrameLayout.LayoutParams(-1, -1))
                    if (activityDays.contains(dayKey(monthStart.get(Calendar.YEAR), monthStart.get(Calendar.MONTH), dayOfMonth))) {
                        val dot = View(this).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(GBApplication.getTextColor(this@XiaomiHipeeP1HistoryActivity))
                            }
                        }
                        cell.addView(dot, FrameLayout.LayoutParams(dp(6), dp(6), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                            bottomMargin = dp(4)
                        })
                    }
                }
                days.addView(cell, GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(48)
                    columnSpec = GridLayout.spec(index % 7, 1f)
                    rowSpec = GridLayout.spec(index / 7)
                })
            }
            content.addView(days)
            previous.setOnClickListener {
                month.add(Calendar.MONTH, -1)
                renderMonth()
            }
            next.setOnClickListener {
                month.add(Calendar.MONTH, 1)
                renderMonth()
            }
        }

        dialog.setOnShowListener { renderMonth() }
        dialog.show()
    }

    private fun readActivityDays(month: Calendar): Set<Int> {
        val from = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); clearTime() }
        val to = (from.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return try {
            GBApplication.acquireDbReadOnly().use { db: DBHandler ->
                db.daoSession.xiaomiHipeeP1ReadingDao.queryBuilder()
                    .where(
                        XiaomiHipeeP1ReadingDao.Properties.DeviceId.eq(deviceId),
                        XiaomiHipeeP1ReadingDao.Properties.StartupTime.ge(maxOf(EPOCH_MIN_SECONDS, from.timeInMillis / 1000)),
                        XiaomiHipeeP1ReadingDao.Properties.StartupTime.lt(to.timeInMillis / 1000),
                    )
                    .list()
                    .mapTo(mutableSetOf()) {
                        val day = Calendar.getInstance().apply { timeInMillis = it.startupTime * 1000 }
                        dayKey(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
                    }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Unable to read P1 history database", e)
        }
    }

    private fun dayKey(year: Int, month: Int, day: Int): Int = year * 10000 + month * 100 + day

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.legend.textColor = GBApplication.getTextColor(this)
        chart.setNoDataText("")
        chart.setTouchEnabled(true)
        chart.setPinchZoom(false)
        chart.setScaleEnabled(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.apply {
            axisMinimum = 0f
            granularity = 1f
            textColor = GBApplication.getSecondaryTextColor(this@XiaomiHipeeP1HistoryActivity)
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelCount = 12
            textColor = GBApplication.getSecondaryTextColor(this@XiaomiHipeeP1HistoryActivity)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    if (value.toInt() in 0..23) String.format(Locale.getDefault(), "%02d", value.toInt()) else ""
            }
        }
        chart.onChartGestureListener = object : OnChartGestureListener {
            override fun onChartSingleTapped(me: MotionEvent?) {
                me ?: return
                val x = chart.getTransformer(YAxis.AxisDependency.LEFT)
                    .getValuesByTouchPoint(me.x, me.y).x
                val hour = x.roundToInt()
                // MPAndroidChart cannot hit-test a zero-height bar. Select the retained zero-only
                // hour explicitly so its marker can state that no corrections were recorded.
                if (hour in chartHours.indices && abs(x - hour) <= 0.425 &&
                    chartHours[hour].hasOnlyZeroCorrections
                ) {
                    chart.highlightValue(Highlight(hour.toFloat(), 0f, 0))
                }
            }

            override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) = Unit
            override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) = Unit
            override fun onChartLongPressed(me: MotionEvent?) = Unit
            override fun onChartDoubleTapped(me: MotionEvent?) = Unit
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) = Unit
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) = Unit
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) = Unit
        }
    }

    private fun draw() {
        dateView.text = DateFormat.getDateInstance(DateFormat.FULL).format(selectedDay.time)
        val data = readDay(selectedDay)
        // Stored P1 rows do not contain a verified wearing-duration or correction-event field.
        // The visible total is only the aggregate of positive raw posture fields; do not derive a duration.
        totalPositiveMeasurementsView.text = data.totalPositiveMeasurements.toString()
        forwardCountView.text = data.forwardCount.toString()
        leftCountView.text = data.leftCount.toString()
        rightCountView.text = data.rightCount.toString()
        updateChart(data)
    }

    private fun changeDate(field: Int, amount: Int) {
        selectedDay.add(field, amount)
        selectedDay.clearTime()
        draw()
    }

    private fun readDay(day: Calendar): DayData {
        val from = day.clone() as Calendar
        from.clearTime()
        val to = from.clone() as Calendar
        to.add(Calendar.DAY_OF_YEAR, 1)
        val startSeconds = maxOf(EPOCH_MIN_SECONDS, from.timeInMillis / 1000)
        return try {
            GBApplication.acquireDbReadOnly().use { db: DBHandler ->
                val rows = db.daoSession.xiaomiHipeeP1ReadingDao.queryBuilder()
                    .where(
                        XiaomiHipeeP1ReadingDao.Properties.DeviceId.eq(deviceId),
                        XiaomiHipeeP1ReadingDao.Properties.StartupTime.ge(startSeconds),
                        XiaomiHipeeP1ReadingDao.Properties.StartupTime.lt(to.timeInMillis / 1000),
                    )
                    .list()
                aggregate(rows)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Unable to read P1 history database", e)
        }
    }

    private fun updateChart(data: DayData) {
        chartHours = data.hours
        val entries = data.hours.mapIndexed { hour, counts ->
            BarEntry(hour.toFloat(), floatArrayOf(counts.forward.toFloat(), counts.left.toFloat(), counts.right.toFloat()))
        }
        val set = BarDataSet(entries, "").apply {
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.LEFT
            setColors(
                ContextCompat.getColor(this@XiaomiHipeeP1HistoryActivity, R.color.chart_stress_high),
                ContextCompat.getColor(this@XiaomiHipeeP1HistoryActivity, R.color.chart_stress_moderate),
                ContextCompat.getColor(this@XiaomiHipeeP1HistoryActivity, R.color.chart_stress_mild),
            )
            stackLabels = arrayOf(
                getString(R.string.mi_hipee_p1_history_forward),
                getString(R.string.mi_hipee_p1_history_leftward),
                getString(R.string.mi_hipee_p1_history_rightward),
            )
        }
        chart.data = BarData(set).apply { barWidth = 0.85f }
        chart.marker = P1HistoryMarker(data.hours)
        chart.invalidate()
    }

    internal data class HourCounts(
        var readingCount: Int = 0,
        var forward: Int = 0,
        var left: Int = 0,
        var right: Int = 0,
    ) {
        val hasOnlyZeroCorrections: Boolean
            get() = readingCount > 0 && forward == 0 && left == 0 && right == 0
    }

    internal data class DayData(val hours: Array<HourCounts> = Array(24) { HourCounts() }) {
        val forwardCount: Int get() = hours.sumOf { it.forward }
        val leftCount: Int get() = hours.sumOf { it.left }
        val rightCount: Int get() = hours.sumOf { it.right }
        /** Count of positive raw fields, not a verified device correction-event total. */
        val totalPositiveMeasurements: Int get() = forwardCount + leftCount + rightCount
    }

    private inner class P1HistoryMarker(private val hours: Array<HourCounts>) : MarkerView(this@XiaomiHipeeP1HistoryActivity, R.layout.value_marker) {
        private val markerContent: TextView = findViewById(R.id.marker_content)

        override fun refreshContent(entry: Entry, highlight: Highlight) {
            val hour = entry.x.toInt()
            val counts = hours.getOrNull(hour)
            markerContent.text = buildString {
                append(String.format(Locale.getDefault(), "%02d:00", hour))
                if (counts != null) {
                    if (counts.forward > 0) append('\n').append(getString(R.string.mi_hipee_p1_history_marker_forward, counts.forward))
                    if (counts.left > 0) append('\n').append(getString(R.string.mi_hipee_p1_history_marker_leftward, counts.left))
                    if (counts.right > 0) append('\n').append(getString(R.string.mi_hipee_p1_history_marker_rightward, counts.right))
                    if (counts.hasOnlyZeroCorrections) {
                        append('\n').append(getString(R.string.mi_hipee_p1_history_marker_no_corrections))
                    }
                }
            }
            super.refreshContent(entry, highlight)
        }

        override fun getOffset(): MPPointF = MPPointF(-(width / 2f), -height.toFloat())
    }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    internal companion object {
        /** Smaller values are raw durations/counters, never calendar dates. */
        const val EPOCH_MIN_SECONDS = 946684800L // 2000-01-01 UTC

        internal fun aggregate(rows: List<XiaomiHipeeP1Reading>): DayData {
            val dayData = DayData()
            rows.forEach { row ->
                val calendar = Calendar.getInstance().apply { timeInMillis = row.startupTime * 1000 }
                val counts = dayData.hours[calendar.get(Calendar.HOUR_OF_DAY)]
                // A retained row is evidence that the device reported for this hour. Keep that
                // evidence even when both raw posture fields are zero, so it can be presented as
                // no corrections rather than as an hour with no reading.
                counts.readingCount++
                // Stored 0x37 fields are raw bytes. Non-zero forward is shown separately; the bank byte's
                // high bit is left/right split. Neither field is a degree threshold.
                if (row.forwardAngle != 0) counts.forward++
                when {
                    row.bankAngle >= 128 -> counts.left++
                    row.bankAngle != 0 -> counts.right++
                }
            }
            return dayData
        }
    }
}
