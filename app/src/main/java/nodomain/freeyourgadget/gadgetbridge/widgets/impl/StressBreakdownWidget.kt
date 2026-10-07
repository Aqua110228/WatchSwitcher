package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig

/**
 * Time-in-zone breakdown of stress over the period.
 */
object StressBreakdownWidget : GaugeWidget<StressData?>() {
    override val id = "stress_breakdown"
    override val label = R.string.menuitem_stress
    override val name = R.string.menuitem_stress_breakdown
    override val icon = R.drawable.ic_stress
    override val chartTab = "stress"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsStressMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): StressData? = computeStress(scope)

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: StressData?) {
        if (data == null) {
            drawSimpleGauge(gaugeBar, 0, -1f)
            return
        }

        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.chart_stress_relaxed),
            ContextCompat.getColor(context, R.color.chart_stress_mild),
            ContextCompat.getColor(context, R.color.chart_stress_moderate),
            ContextCompat.getColor(context, R.color.chart_stress_high),
        )

        val segments = FloatArray(4)
        val sum = data.totalTime.sum()
        if (sum != 0) {
            for (i in 0 until 4) {
                segments[i] = data.totalTime[i] / sum.toFloat()
            }
        }

        gaugeValue.text = data.value.toString()
        drawSegmentedGauge(gaugeBar, colors, segments, -1f, false, true)
    }
}
