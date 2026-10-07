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
 * Stress as a segmented gauge (relaxed/mild/moderate/high zones).
 */
object StressSegmentedWidget : GaugeWidget<StressData?>() {
    override val id = "stress_segmented"
    override val label = R.string.menuitem_stress
    override val name = R.string.menuitem_stress_segmented
    override val icon = R.drawable.ic_stress
    override val chartTab = "stress"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsStressMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): StressData? = computeStress(scope)

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: StressData?) {
        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.chart_stress_relaxed),
            ContextCompat.getColor(context, R.color.chart_stress_mild),
            ContextCompat.getColor(context, R.color.chart_stress_moderate),
            ContextCompat.getColor(context, R.color.chart_stress_high),
        )

        val segments: FloatArray
        val value: Float
        val valueText: String

        if (data != null) {
            segments = floatArrayOf(
                (data.ranges[1] - data.ranges[0]) / 100f,
                (data.ranges[2] - data.ranges[1]) / 100f,
                (data.ranges[3] - data.ranges[2]) / 100f,
                1 - data.ranges[2] / 100f,
            )
            value = data.value / 100f
            valueText = data.value.toString()
        } else {
            segments = floatArrayOf(40 / 100f, 20 / 100f, 20 / 100f, 20 / 100f)
            value = -1f
            valueText = context.getString(R.string.stats_empty_value)
        }

        gaugeValue.text = valueText
        drawSegmentedGauge(
            gaugeBar,
            colors,
            segments,
            value,
            fadeOutsideDot = false,
            gapBetweenSegments = true,
        )
    }
}
