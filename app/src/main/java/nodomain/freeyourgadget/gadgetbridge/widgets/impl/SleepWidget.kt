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
 * Sleep duration for the day against the user's sleep goal.
 */
object SleepWidget : GaugeWidget<SleepWidget.Data>() {
    override val id = "sleep"
    override val label = R.string.menuitem_sleep
    override val icon = R.drawable.ic_activity_sleep
    override val chartTab = "sleep"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsSleepMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val total = scope.sleepMinutesTotal()
        return Data(total, scope.sleepGoalFactor(total))
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        gaugeValue.text = String.format(Locale.ROOT, "%d:%02d", data.total / 60, data.total % 60)
        drawSimpleGauge(gaugeBar, WidgetColors.lightSleep, data.goalFactor)
    }

    data class Data(val total: Long, val goalFactor: Float)
}
