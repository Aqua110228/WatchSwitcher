package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StepAnalysis
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySession
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import org.slf4j.LoggerFactory
import java.util.Calendar
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery

/**
 * A 24h circular activity clock: not-worn/worn/activity/exercise/sleep-stage segments, with an
 * optional "yesterday" overlay and a current-time indicator.
 */
object TodayWidget : GBWidget<TodayWidget.Data> {
    private val LOG = LoggerFactory.getLogger(TodayWidget::class.java)

    override val id = "today"
    override val label = R.string.pref_dashboard_widget_today_title
    override val icon = R.drawable.ic_calendar_today
    override val defaultColumns = 2
    override val allowedColumns = listOf(1, 2)

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsActivityTracking(device)

    override fun settings(context: Context): DeviceSettingsSpec = deviceSettings {
        switchSetting(
            key = "24h",
            title = R.string.pref_dashboard_widget_today_24h_title,
            summary = R.string.pref_dashboard_widget_today_24h_summary,
            icon = R.drawable.ic_access_time,
            defaultValue = false,
            connectedOnly = false,
        )
        switchSetting(
            key = "24h_upside_down",
            title = R.string.pref_dashboard_widget_today_upside_down_title,
            summary = R.string.pref_dashboard_widget_today_upside_down_summary,
            icon = R.drawable.ic_caret_down_solid,
            defaultValue = false,
            dependency = "24h",
            connectedOnly = false,
        )
        switchSetting(
            key = "show_yesterday",
            title = R.string.pref_dashboard_widget_today_yesterday_data_title,
            summary = R.string.pref_dashboard_widget_today_yesterday_data_summary,
            icon = R.drawable.ic_calendar_from,
            defaultValue = false,
            connectedOnly = false,
        )
        switchSetting(
            key = "dim_yesterday",
            title = R.string.pref_dashboard_widget_today_dim_yesterday_data_title,
            summary = R.string.pref_dashboard_widget_today_dim_yesterday_data_summary,
            icon = R.drawable.ic_brightness_2,
            defaultValue = true,
            connectedOnly = false,
        )
        switchSetting(
            key = "time_indicator",
            title = R.string.pref_dashboard_widget_today_time_indicator_title,
            summary = R.string.pref_dashboard_widget_today_time_indicator_summary,
            icon = R.drawable.ic_info,
            defaultValue = false,
            connectedOnly = false,
        )
        switchSetting(
            key = "legend",
            title = R.string.pref_dashboard_widget_show_legend_title,
            summary = R.string.pref_dashboard_widget_show_legend_summary,
            icon = R.drawable.ic_legend_toggle,
            defaultValue = true,
            connectedOnly = false,
        )
        text(
            key = "hr_interval",
            title = R.string.pref_dashboard_widget_today_hr_interval_title,
            summary = R.string.pref_dashboard_widget_today_hr_interval_summary,
            icon = R.drawable.ic_heartrate,
            defaultValue = "1",
            maxLength = 4,
            inputType = InputType.TYPE_CLASS_NUMBER,
            connectedOnly = false,
        )
    }

    //
    // Data loading (background thread)
    //

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val mode24h = config.prefs.getBoolean("24h", false)
        val showYesterday = config.prefs.getBoolean("show_yesterday", false)
        val hrIntervalSecs = (config.prefs.getString("hr_interval", "1").toIntOrNull() ?: 1) * 60

        var widen = false
        if (showYesterday) {
            val today = Calendar.getInstance()
            val dashboardDate = Calendar.getInstance()
            dashboardDate.timeInMillis = (scope.query.timeFrom + 1) * 1000L
            widen = DateTimeUtils.isSameDay(today, dashboardDate)
        }
        val dataScope = if (widen) {
            WidgetDataScope(
                DashboardQuery(timeFrom = scope.query.timeFrom - 86400, timeTo = scope.query.timeTo),
                scope.devices
            )
        } else {
            scope
        }

        val chartTimeFrom = dataScope.query.timeFrom.toLong()

        val activityTimestamps = HashMap<Long, ActivityKind>()

        fun addActivity(timeFrom: Long, timeTo: Long, kind: ActivityKind) {
            var i = timeFrom
            while (i <= timeTo) {
                val existing = activityTimestamps[i]
                val replace = when (existing) {
                    null -> true
                    ActivityKind.EXERCISE -> false
                    ActivityKind.ACTIVITY -> kind == ActivityKind.EXERCISE
                    ActivityKind.DEEP_SLEEP -> kind == ActivityKind.EXERCISE || kind == ActivityKind.ACTIVITY
                    ActivityKind.LIGHT_SLEEP -> kind == ActivityKind.EXERCISE || kind == ActivityKind.ACTIVITY || kind == ActivityKind.DEEP_SLEEP
                    ActivityKind.REM_SLEEP -> kind == ActivityKind.EXERCISE || kind == ActivityKind.ACTIVITY ||
                            kind == ActivityKind.DEEP_SLEEP || kind == ActivityKind.LIGHT_SLEEP

                    ActivityKind.AWAKE_SLEEP -> kind == ActivityKind.EXERCISE || kind == ActivityKind.ACTIVITY ||
                            kind == ActivityKind.DEEP_SLEEP || kind == ActivityKind.LIGHT_SLEEP || kind == ActivityKind.REM_SLEEP

                    ActivityKind.SLEEP_ANY, ActivityKind.NOT_MEASURED -> kind == ActivityKind.EXERCISE || kind == ActivityKind.ACTIVITY ||
                            kind == ActivityKind.DEEP_SLEEP || kind == ActivityKind.LIGHT_SLEEP ||
                            kind == ActivityKind.REM_SLEEP || kind == ActivityKind.AWAKE_SLEEP

                    else -> true
                }
                if (replace) activityTimestamps[i] = kind
                i++
            }
        }

        fun calculateWornSessions(samples: List<ActivitySample>) {
            var firstTimestamp = 0
            var lastTimestamp = 0
            for (sample in samples) {
                if (sample.heartRate < 10 && firstTimestamp == 0) continue
                if (firstTimestamp == 0) firstTimestamp = sample.timestamp
                if (lastTimestamp == 0) lastTimestamp = sample.timestamp
                if (HeartRateUtils.getInstance().isValidHeartRateValue(sample.heartRate) &&
                    sample.timestamp > lastTimestamp + hrIntervalSecs &&
                    firstTimestamp != lastTimestamp
                ) {
                    LOG.trace("Registered worn session from {} to {}", firstTimestamp, lastTimestamp)
                    addActivity(firstTimestamp.toLong(), lastTimestamp.toLong(), ActivityKind.NOT_MEASURED)
                    if (sample.heartRate < 10) {
                        firstTimestamp = 0
                        lastTimestamp = 0
                    } else {
                        firstTimestamp = sample.timestamp
                        lastTimestamp = sample.timestamp
                    }
                    continue
                }
                if (HeartRateUtils.getInstance().isValidHeartRateValue(sample.heartRate)) {
                    lastTimestamp = sample.timestamp
                }
            }
            if (firstTimestamp != lastTimestamp) {
                LOG.trace("Registered worn session from {} to {}", firstTimestamp, lastTimestamp)
                addActivity(firstTimestamp.toLong(), lastTimestamp.toLong(), ActivityKind.NOT_MEASURED)
            }
        }

        // Retrieve activity data
        val allActivitySamples = mutableListOf<ActivitySample>()
        val stepSessions = mutableListOf<ActivitySession>()
        var activitySummaries: List<BaseActivitySummary> = emptyList()

        try {
            val deviceIds = mutableListOf<Long>()
            for (dev in dataScope.devices) {
                val samples = dataScope.allSamples(dev)
                allActivitySamples.addAll(samples)
                stepSessions.addAll(StepAnalysis().calculateStepSessions(samples, emptyList()))
                deviceIds.add(dataScope.dbDeviceId(dev))
            }
            activitySummaries = if (deviceIds.isEmpty()) emptyList() else dataScope.workoutSummaries(deviceIds)
        } catch (e: Exception) {
            LOG.warn("Could not retrieve activity amounts: ", e)
        }

        allActivitySamples.sortBy { it.timestamp }

        // Determine worn sessions from heart rate samples
        calculateWornSessions(allActivitySamples)

        // Integrate various data from multiple devices
        for (sample in allActivitySamples) {
            // Handle only TYPE_NOT_WORN and TYPE_SLEEP (including variants) here
            if (sample.kind != ActivityKind.NOT_WORN &&
                (sample.kind == ActivityKind.NOT_MEASURED || !ActivityKind.isSleep(sample.kind))
            ) {
                continue
            }
            // Add to day results
            addActivity(sample.timestamp.toLong(), sample.timestamp.toLong() + 60, sample.kind)
        }
        for (summary in activitySummaries) {
            addActivity(summary.startTime.time / 1000, summary.endTime.time / 1000, ActivityKind.EXERCISE)
        }
        for (session in stepSessions) {
            addActivity(session.startTime.time / 1000, session.endTime.time / 1000, ActivityKind.ACTIVITY)
        }

        // Merge per-second activities into minute-resolution generalized ranges
        val currentTime = System.currentTimeMillis() / 1000
        val timeTo = scope.query.timeTo.toLong()
        val midDaySecond = timeTo - 12 * 60 * 60
        var previous: GeneralizedActivity? = null
        val result = mutableListOf<GeneralizedActivity>()
        for (entry in activityTimestamps.entries.sortedBy { it.key }) {
            val timestamp = entry.key
            val kind = entry.value
            val prev = previous
            // Start a new merged activity on certain conditions
            if (prev == null ||
                prev.activityKind != kind ||
                (!mode24h && timestamp == midDaySecond) ||
                (!mode24h && timestamp == midDaySecond - 86400) ||
                timestamp == timeTo - 86400 ||
                timestamp == currentTime - 86400 ||
                prev.timeTo < timestamp - 60
            ) {
                val newActivity = GeneralizedActivity(kind, timestamp, timestamp)
                result.add(newActivity)
                previous = newActivity
            } else {
                prev.timeTo = timestamp
            }
        }

        return Data(result, chartTimeFrom, scope.query.timeTo, mode24h)
    }

    //
    // View (UI thread)
    //

    override fun createView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflater.inflate(R.layout.dashboard_widget_today, parent, false)

    override fun bind(view: View, config: WidgetConfig, data: Data) {
        val context = view.context

        val legend = view.findViewById<TextView>(R.id.dashboard_piechart_legend)
        legend.text = initializeLegend(context)
        legend.visibility = if (config.prefs.getBoolean("legend", true)) View.VISIBLE else View.GONE

        val chart = view.findViewById<ImageView>(R.id.dashboard_today_chart)
        chart.setImageBitmap(drawChart(context, config, data))
    }

    private fun initializeLegend(context: Context): CharSequence {
        fun labeled(text: String, color: Int): SpannableString {
            val span = SpannableString("■ $text")
            span.setSpan(ForegroundColorSpan(color), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            return span
        }

        val notWorn = labeled(context.getString(R.string.abstract_chart_fragment_kind_not_worn), WidgetColors.notWorn)
        val worn = labeled(context.getString(R.string.activity_type_worn), WidgetColors.worn)
        val activity = labeled(context.getString(R.string.activity_type_activity), WidgetColors.activity)
        val exercise = labeled(context.getString(R.string.activity_type_exercise), WidgetColors.exercise)
        val deepSleep = labeled(context.getString(R.string.activity_type_deep_sleep), WidgetColors.deepSleep)
        val lightSleep = labeled(context.getString(R.string.activity_type_light_sleep), WidgetColors.lightSleep)
        val remSleep = labeled(context.getString(R.string.activity_type_rem_sleep), WidgetColors.remSleep)

        return SpannableStringBuilder()
            .append(notWorn).append(" ").append(worn)
            .append("\n").append(activity).append(" ").append(exercise)
            .append("\n").append(lightSleep).append(" ").append(deepSleep).append(" ").append(remSleep)
    }

    @Suppress("UnnecessaryVariable", "ReplaceJavaStaticMethodWithKotlinAnalog", "UnusedVariable", "unused")
    private fun drawChart(context: Context, config: WidgetConfig, data: Data): Bitmap {
        val mode24h = data.mode24h
        val upsideDown24h = config.prefs.getBoolean("24h_upside_down", false)
        val showYesterday = config.prefs.getBoolean("show_yesterday", false)
        val dimYesterday = config.prefs.getBoolean("dim_yesterday", true)
        val timeIndicator = config.prefs.getBoolean("time_indicator", false)

        val currentDayStart = data.timeTo - 86400
        val midDaySecond = currentDayStart + 12 * 60 * 60
        val width = Resources.getSystem().displayMetrics.widthPixels
        val height = width
        val barWidth = (width * 0.08f).roundToInt()
        val hourTextSp = (width * 0.024f).roundToInt()
        val hourTextPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            hourTextSp.toFloat(),
            context.resources.displayMetrics,
        )
        val outerCircleMargin = if (mode24h) barWidth / 2f else barWidth / 2f + hourTextPixels * 1.3f
        val innerCircleMargin = outerCircleMargin + barWidth * 1.3f
        val degreeFactor = if (mode24h) 240f else 120f

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE

        // Draw clock stripes
        val clockMargin = outerCircleMargin + if (mode24h) barWidth.toFloat() else barWidth * 2.3f
        val clockStripesInterval = if (mode24h) 15 else 30
        val clockStripesWidth = barWidth / 3f
        paint.strokeWidth = clockStripesWidth
        paint.color = WidgetColors.worn
        var i = 0
        while (i < 360) {
            canvas.drawArc(
                clockMargin,
                clockMargin,
                width - clockMargin,
                height - clockMargin,
                i.toFloat(),
                1f,
                false,
                paint
            )
            i += clockStripesInterval
        }

        // Draw hours
        val normalClock = DateFormat.is24HourFormat(context)
        val hours = mapOf(
            0 to if (normalClock) (if (mode24h) "0" else "12") else (if (mode24h) "12am" else "12pm"),
            3 to "3",
            6 to if (normalClock) "6" else "6am",
            9 to "9",
            12 to if (normalClock) (if (mode24h) "12" else "0") else (if (mode24h) "12pm" else "12am"),
            15 to if (normalClock) "15" else "3",
            18 to if (normalClock) "18" else "6pm",
            21 to if (normalClock) "21" else "9",
        )
        val textPaint = Paint()
        textPaint.isAntiAlias = true
        textPaint.color = WidgetColors.worn
        textPaint.textSize = hourTextPixels
        textPaint.textAlign = Paint.Align.CENTER
        val textBounds = Rect()

        fun bounds(text: String): Rect {
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            return textBounds
        }

        if (mode24h && upsideDown24h) {
            canvas.drawText(hours.getValue(0), width / 2f, height - (clockMargin + clockStripesWidth), textPaint)
            val b6 = bounds(hours.getValue(6))
            canvas.drawText(
                hours.getValue(6),
                clockMargin + clockStripesWidth + b6.width() / 2f,
                height / 2f + b6.height() / 2f,
                textPaint
            )
            val b12 = bounds(hours.getValue(12))
            canvas.drawText(hours.getValue(12), width / 2f, clockMargin + clockStripesWidth + b12.height(), textPaint)
            val b18 = bounds(hours.getValue(18))
            canvas.drawText(
                hours.getValue(18),
                width - (clockMargin + clockStripesWidth + b18.width()),
                height / 2f + b18.height() / 2f,
                textPaint
            )
        } else if (mode24h) {
            val b0 = bounds(hours.getValue(0))
            canvas.drawText(hours.getValue(0), width / 2f, clockMargin + clockStripesWidth + b0.height(), textPaint)
            val b6 = bounds(hours.getValue(6))
            canvas.drawText(
                hours.getValue(6),
                width - (clockMargin + clockStripesWidth + b6.width()),
                height / 2f + b6.height() / 2f,
                textPaint
            )
            val b12 = bounds(hours.getValue(12))
            canvas.drawText(hours.getValue(12), width / 2f, height - (clockMargin + clockStripesWidth), textPaint)
            val b18 = bounds(hours.getValue(18))
            canvas.drawText(
                hours.getValue(18),
                clockMargin + clockStripesWidth + b18.width() / 2f,
                height / 2f + b18.height() / 2f,
                textPaint
            )
        } else {
            val b0 = bounds(hours.getValue(0))
            canvas.drawText(hours.getValue(0), width / 2f, b0.height().toFloat(), textPaint)
            val b3 = bounds(hours.getValue(3))
            canvas.drawText(
                hours.getValue(3),
                width - (clockMargin + clockStripesWidth + b3.width()),
                height / 2f + b3.height() / 2f,
                textPaint
            )
            val b6 = bounds(hours.getValue(6))
            canvas.drawText(hours.getValue(6), width / 2f, height - (clockMargin + clockStripesWidth), textPaint)
            val b9 = bounds(hours.getValue(9))
            canvas.drawText(
                hours.getValue(9),
                clockMargin + clockStripesWidth + b9.width() / 2f,
                height / 2f + b9.height() / 2f,
                textPaint
            )
            val b12 = bounds(hours.getValue(12))
            canvas.drawText(hours.getValue(12), width / 2f, clockMargin + clockStripesWidth + b12.height(), textPaint)
            val b15 = bounds(hours.getValue(15))
            canvas.drawText(
                hours.getValue(15),
                Math.ceil((width - b15.width() / 2f).toDouble()).toFloat(),
                height / 2f + b15.height() / 2f,
                textPaint
            )
            val b18 = bounds(hours.getValue(18))
            canvas.drawText(hours.getValue(18), width / 2f, height - b18.height() / 2f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            val b21 = bounds(hours.getValue(21))
            canvas.drawText(hours.getValue(21), 1f, height / 2f + b21.height() / 2f, textPaint)
        }

        // Draw generalized activities on circular chart
        var secondIndex = data.chartTimeFrom
        val currentTime = System.currentTimeMillis() / 1000
        val dayIsToday = data.timeTo >= currentTime
        val startAngle = if (mode24h && upsideDown24h) 90f else 270f

        for (activity in data.activities) {
            var margin = innerCircleMargin
            if (mode24h || activity.timeFrom >= midDaySecond) {
                margin = outerCircleMargin
            }
            if (!mode24h && showYesterday && dayIsToday) {
                if (activity.timeFrom < currentDayStart && activity.timeFrom > midDaySecond - 86400) {
                    margin = outerCircleMargin
                }
            }
            // Skip activities from before 24h ago (to prevent double-drawing the same position)
            if (showYesterday && dayIsToday && activity.timeTo < currentTime - 86400) {
                continue
            }
            // Draw inactive slices
            if (!mode24h && secondIndex < midDaySecond && activity.timeFrom >= midDaySecond) {
                paint.strokeWidth = barWidth / 3f
                paint.color = WidgetColors.unknown
                canvas.drawArc(
                    innerCircleMargin,
                    innerCircleMargin,
                    width - innerCircleMargin,
                    height - innerCircleMargin,
                    startAngle + (secondIndex - data.chartTimeFrom) / degreeFactor,
                    (midDaySecond - secondIndex) / degreeFactor,
                    false,
                    paint,
                )
                secondIndex = midDaySecond.toLong()
            }
            if (activity.timeFrom > secondIndex) {
                paint.strokeWidth = barWidth / 3f
                paint.color = WidgetColors.unknown
                canvas.drawArc(
                    margin,
                    margin,
                    width - margin,
                    height - margin,
                    startAngle + (secondIndex - data.chartTimeFrom) / degreeFactor,
                    (activity.timeFrom - secondIndex) / degreeFactor,
                    false,
                    paint,
                )
            }
            val startAngleForActivity = startAngle + (activity.timeFrom - data.chartTimeFrom) / degreeFactor
            val sweepAngle = (activity.timeTo - activity.timeFrom) / degreeFactor
            val dim = showYesterday && dimYesterday && dayIsToday && activity.timeFrom < currentDayStart

            val (strokeWidth, color) = when (activity.activityKind) {
                ActivityKind.NOT_MEASURED -> barWidth / 3f to WidgetColors.worn
                ActivityKind.NOT_WORN -> barWidth / 3f to WidgetColors.notWorn
                ActivityKind.LIGHT_SLEEP, ActivityKind.SLEEP_ANY -> barWidth.toFloat() to WidgetColors.lightSleep
                ActivityKind.REM_SLEEP -> barWidth.toFloat() to WidgetColors.remSleep
                ActivityKind.DEEP_SLEEP -> barWidth.toFloat() to WidgetColors.deepSleep
                ActivityKind.AWAKE_SLEEP -> barWidth.toFloat() to WidgetColors.awakeSleep
                ActivityKind.EXERCISE -> barWidth.toFloat() to WidgetColors.exercise
                else -> barWidth.toFloat() to WidgetColors.activity
            }
            paint.strokeWidth = strokeWidth
            paint.color = color
            // Alpha is only ever set to 64 here, never reset to 255 -- leaves every later arc in this draw
            // pass dimmed too once any one segment has been.
            if (dim) paint.alpha = 64
            canvas.drawArc(
                margin,
                margin,
                width - margin,
                height - margin,
                startAngleForActivity,
                sweepAngle,
                false,
                paint
            )

            secondIndex = activity.timeTo
        }

        // Draw indicator for current time
        if (timeIndicator && currentTime < data.timeTo) {
            val margin = if (mode24h || currentTime >= midDaySecond) outerCircleMargin else innerCircleMargin
            paint.strokeWidth = barWidth.toFloat()
            paint.color = GBApplication.getTextColor(context)
            canvas.drawArc(
                margin, margin, width - margin, height - margin,
                startAngle + (currentTime - data.chartTimeFrom) / degreeFactor, 300 / degreeFactor,
                false, paint,
            )
        }
        // Fill remaining time until current time in 12h mode before midday
        if (!mode24h && currentTime < midDaySecond) {
            // Fill inner bar up until current time
            paint.strokeWidth = barWidth / 3f
            paint.color = WidgetColors.unknown
            canvas.drawArc(
                innerCircleMargin,
                innerCircleMargin,
                width - innerCircleMargin,
                height - innerCircleMargin,
                startAngle + (secondIndex - data.chartTimeFrom) / degreeFactor,
                (currentTime - secondIndex) / degreeFactor,
                false,
                paint,
            )
            // Fill inner bar up until midday
            canvas.drawArc(
                innerCircleMargin,
                innerCircleMargin,
                width - innerCircleMargin,
                height - innerCircleMargin,
                startAngle + (currentTime - data.chartTimeFrom) / degreeFactor,
                (midDaySecond - currentTime) / degreeFactor,
                false,
                paint,
            )
            // Fill outer bar up until midnight
            canvas.drawArc(
                outerCircleMargin,
                outerCircleMargin,
                width - outerCircleMargin,
                height - outerCircleMargin,
                0f,
                360f,
                false,
                paint
            )
        }
        // Fill remaining time until current time in 24h mode or in 12h mode after midday
        if ((mode24h || currentTime >= midDaySecond) && currentTime < data.timeTo) {
            // Fill inner bar up until midday
            if (!mode24h && secondIndex < midDaySecond) {
                paint.strokeWidth = barWidth / 3f
                paint.color = WidgetColors.unknown
                canvas.drawArc(
                    innerCircleMargin,
                    innerCircleMargin,
                    width - innerCircleMargin,
                    height - innerCircleMargin,
                    startAngle + (secondIndex - data.chartTimeFrom) / degreeFactor,
                    (midDaySecond - secondIndex) / degreeFactor,
                    false,
                    paint,
                )
                secondIndex = midDaySecond.toLong()
            }
            // Fill outer bar up until current time
            paint.strokeWidth = barWidth / 3f
            paint.color = WidgetColors.unknown
            canvas.drawArc(
                outerCircleMargin,
                outerCircleMargin,
                width - outerCircleMargin,
                height - outerCircleMargin,
                startAngle + (secondIndex - data.chartTimeFrom) / degreeFactor,
                (currentTime - secondIndex) / degreeFactor,
                false,
                paint,
            )
            // Fill outer bar up until midnight
            canvas.drawArc(
                outerCircleMargin,
                outerCircleMargin,
                width - outerCircleMargin,
                height - outerCircleMargin,
                startAngle + (currentTime - data.chartTimeFrom) / degreeFactor,
                (data.timeTo - currentTime) / degreeFactor,
                false,
                paint,
            )
        }
        // Only when displaying a past day
        if (data.timeTo in (secondIndex + 1)..<currentTime) {
            // Fill outer bar up until midnight
            paint.strokeWidth = barWidth / 3f
            paint.color = WidgetColors.unknown
            canvas.drawArc(
                outerCircleMargin,
                outerCircleMargin,
                width - outerCircleMargin,
                height - outerCircleMargin,
                startAngle + (secondIndex - data.chartTimeFrom) / degreeFactor,
                (data.timeTo - secondIndex) / degreeFactor,
                false,
                paint,
            )
        }

        return bitmap
    }

    data class GeneralizedActivity(val activityKind: ActivityKind, val timeFrom: Long, var timeTo: Long)

    data class Data(
        val activities: List<GeneralizedActivity>,
        val chartTimeFrom: Long,
        val timeTo: Int,
        val mode24h: Boolean,
    )
}
