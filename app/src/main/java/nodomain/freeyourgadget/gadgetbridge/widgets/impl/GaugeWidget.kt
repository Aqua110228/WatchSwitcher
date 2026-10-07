package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.toSpannable
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetActions
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig

/**
 * Base for a gauge widget: a label, a value, and a gauge bar rendered as a bitmap by [GaugeDrawer].
 */
abstract class GaugeWidget<D> : GBWidget<D> {
    /**
     * Gauge widgets are always a single column.
     */
    override val allowedColumns: List<Int> = listOf(1)

    /**
     * Chart tab opened when the widget is tapped, or null if nothing should be opened.
     */
    protected open val chartTab: String? = null

    /**
     * Sets [nodomain.freeyourgadget.gadgetbridge.activities.charts.ActivityChartsActivity.EXTRA_MODE].
     **/
    protected open val chartMode: String = ""

    private val gaugeDrawer = GaugeDrawer()

    override fun createView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflater.inflate(R.layout.dashboard_widget_generic_gauge, parent, false)

    final override fun bind(view: View, config: WidgetConfig, data: D) {
        view.findViewById<TextView>(R.id.gauge_label).text = config.title(view.context, this)
        val gaugeValue = view.findViewById<TextView>(R.id.gauge_value)
        val gaugeBar = view.findViewById<ImageView>(R.id.gauge_bar)
        draw(view.context, gaugeValue, gaugeBar, data)
        shrinkUnits(gaugeValue)
    }

    final override fun onClick(view: View, config: WidgetConfig, timestamp: Int) {
        val tab = chartTab ?: return
        WidgetActions.openChart(view.context, config, this, tab, label, chartMode, timestamp)
    }

    /**
     * Sets [gaugeValue]'s text and draws the gauge bitmap into [gaugeBar] via [gaugeDrawer].
     */
    protected abstract fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: D)

    private fun shrinkUnits(gaugeValue: TextView) {
        val text = gaugeValue.text
        if (text is Spanned || text.none { it.isDigit() }) {
            return
        }
        val spannable = text.toSpannable()
        for (match in UNIT_REGEX.findAll(text)) {
            spannable.setSpan(
                RelativeSizeSpan(UNIT_SIZE),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        gaugeValue.text = spannable
    }

    protected fun drawSimpleGauge(gaugeBar: ImageView, color: Int, value: Float) {
        gaugeDrawer.drawSimpleGauge(gaugeBar, color, value)
    }

    protected fun drawSegmentedGauge(
        gaugeBar: ImageView,
        colors: IntArray,
        segments: FloatArray,
        value: Float,
        fadeOutsideDot: Boolean,
        gapBetweenSegments: Boolean,
    ) {
        gaugeDrawer.drawSegmentedGauge(gaugeBar, colors, segments, value, fadeOutsideDot, gapBetweenSegments)
    }

    private companion object {
        val UNIT_REGEX = Regex("[\\p{L}%]+(?:\\s+[\\p{L}%]+)*")
        const val UNIT_SIZE = 0.7f
    }
}
