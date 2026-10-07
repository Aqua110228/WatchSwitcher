package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
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
 * Active calories for the day against the user's goal.
 */
object CaloriesActiveWidget : GaugeWidget<CaloriesActiveWidget.Data>() {
    override val id = "calories_active"
    override val label = R.string.active_calories
    override val name = R.string.menuitem_calories_active_goal
    override val icon = R.drawable.ic_calories
    override val chartTab = "calories"
    override val chartMode = CaloriesDailyFragment.GaugeViewMode.ACTIVE_CALORIES_GOAL.toString()

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsActiveCalories(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val total = scope.activeCaloriesTotal()
        return Data(total, scope.activeCaloriesGoalFactor(total))
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        gaugeValue.text = NumberFormat.getInstance().format(data.total)
        val colorCalories = ContextCompat.getColor(context, R.color.calories_color)
        drawSimpleGauge(gaugeBar, colorCalories, data.goalFactor)
    }

    data class Data(val total: Int, val goalFactor: Float)
}
