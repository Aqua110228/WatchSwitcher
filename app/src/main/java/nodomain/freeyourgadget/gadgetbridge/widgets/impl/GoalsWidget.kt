package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

/**
 * Draws 4 concentric goal-progress arcs: steps, distance, active time, sleep.
 */
@Suppress("UnnecessaryVariable")
object GoalsWidget : GBWidget<GoalsWidget.Data> {
    override val id = "goals"
    override val label = R.string.pref_dashboard_widget_goals_chart_title
    override val icon = R.drawable.ic_done_all
    override val defaultColumns = 2
    override val allowedColumns = listOf(1, 2)

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsActivityTracking(device)

    override fun settings(context: Context): DeviceSettingsSpec = deviceSettings {
        switchSetting(
            key = "legend",
            title = R.string.pref_dashboard_widget_show_legend_title,
            summary = R.string.pref_dashboard_widget_show_legend_summary,
            icon = R.drawable.ic_legend_toggle,
            defaultValue = true,
        )
    }

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val stepsFactor = scope.stepsGoalFactor(scope.stepsTotal())
        val distanceFactor = scope.distanceGoalFactor(scope.distanceTotal())
        val activeMinutesFactor = scope.activeMinutesGoalFactor(scope.activeMinutesTotal())
        val sleepFactor = scope.sleepGoalFactor(scope.sleepMinutesTotal())

        val width = Resources.getSystem().displayMetrics.widthPixels
        val height = width
        val barWidth = (height * 0.04f).roundToInt()
        var barMargin = ceil(barWidth / 2.0).toInt()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        fun drawRing(factor: Float, color: Int) {
            paint.strokeWidth = barWidth * 0.75f
            paint.color = WidgetColors.unknown
            canvas.drawArc(
                barMargin.toFloat(),
                barMargin.toFloat(),
                width - barMargin.toFloat(),
                height - barMargin.toFloat(),
                270f,
                360f,
                false,
                paint
            )
            paint.strokeWidth = barWidth.toFloat()
            paint.color = color
            canvas.drawArc(
                barMargin.toFloat(),
                barMargin.toFloat(),
                width - barMargin.toFloat(),
                height - barMargin.toFloat(),
                270f,
                360f * factor,
                false,
                paint
            )
            barMargin += (barWidth * 1.5).toInt()
        }

        drawRing(stepsFactor, WidgetColors.activity)
        drawRing(distanceFactor, WidgetColors.distance)
        drawRing(activeMinutesFactor, WidgetColors.activeTime)
        drawRing(sleepFactor, WidgetColors.lightSleep)

        return Data(bitmap)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflater.inflate(R.layout.dashboard_widget_goals, parent, false)

    override fun bind(view: View, config: WidgetConfig, data: Data) {
        view.findViewById<ImageView>(R.id.dashboard_goals_chart).setImageBitmap(data.bitmap)

        val legend = view.findViewById<TextView>(R.id.dashboard_goals_legend)
        val context = view.context
        val steps = spanned("■ ${context.getString(R.string.steps)}", WidgetColors.activity)
        val distance = spanned("■ ${context.getString(R.string.distance)}", WidgetColors.distance)
        val activeTime =
            spanned("■ ${context.getString(R.string.activity_list_summary_active_time)}", WidgetColors.activeTime)
        val sleep = spanned("■ ${context.getString(R.string.menuitem_sleep)}", WidgetColors.lightSleep)
        legend.text = SpannableStringBuilder()
            .append(steps).append(" ").append(distance)
            .append("\n").append(activeTime).append(" ").append(sleep)
        legend.visibility = if (config.prefs.getBoolean("legend", true)) View.VISIBLE else View.GONE
    }

    private fun spanned(text: String, color: Int): SpannableString {
        val span = SpannableString(text)
        span.setSpan(ForegroundColorSpan(color), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    data class Data(val bitmap: Bitmap)
}
