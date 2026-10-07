package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.CaloriesDailyFragment
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import java.text.NumberFormat

/**
 * Total calories for the day, split into active/resting.
 */
object CaloriesSegmentedWidget : GaugeWidget<CaloriesSegmentedWidget.Data>() {
    override val id = "calories_segmented"
    override val label = R.string.calories
    override val name = R.string.menuitem_calories_segmented
    override val icon = R.drawable.ic_calories
    override val chartTab = "calories"
    override val chartMode = CaloriesDailyFragment.GaugeViewMode.TOTAL_CALORIES_SEGMENT.toString()

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsActiveCalories(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data =
        Data(scope.activeCaloriesTotal(), scope.restingCaloriesTotal())

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        val totalCalories = data.activeCalories + data.restingCalories
        gaugeValue.text = NumberFormat.getInstance().format(totalCalories)

        val colors: IntArray
        val segments: FloatArray
        if (totalCalories != 0) {
            colors = intArrayOf(
                ContextCompat.getColor(context, R.color.calories_resting_color),
                ContextCompat.getColor(context, R.color.calories_color),
            )
            segments = floatArrayOf(
                if (data.restingCalories > 0) data.restingCalories / totalCalories.toFloat() else 0f,
                if (data.activeCalories > 0) data.activeCalories / totalCalories.toFloat() else 0f,
            )
        } else {
            colors = intArrayOf(Color.argb(25, 128, 128, 128))
            segments = floatArrayOf(1f)
        }

        drawSegmentedGauge(
            gaugeBar,
            colors,
            segments,
            -1f,
            fadeOutsideDot = false,
            gapBetweenSegments = false,
        )
    }

    data class Data(val activeCalories: Int, val restingCalories: Int)
}
