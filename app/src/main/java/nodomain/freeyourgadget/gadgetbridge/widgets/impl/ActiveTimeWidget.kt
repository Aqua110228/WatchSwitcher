package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import java.util.Locale

/**
 * Active minutes for the day against the user's active time goal.
 */
object ActiveTimeWidget : GaugeWidget<ActiveTimeWidget.Data>() {
    override val id = "activetime"
    override val label = R.string.activity_list_summary_active_time
    override val icon = R.drawable.ic_timer
    override val chartTab = "activity"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsStepCounter(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val total = scope.activeMinutesTotal()
        return Data(total, scope.activeMinutesGoalFactor(total))
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        gaugeValue.text = String.format(Locale.ROOT, "%d:%02d", data.total / 60, data.total % 60)
        drawSimpleGauge(gaugeBar, WidgetColors.activeTime, data.goalFactor)
    }

    data class Data(val total: Long, val goalFactor: Float)
}
