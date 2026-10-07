package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import org.slf4j.LoggerFactory

/**
 * Latest sleep score for the day.
 */
object SleepScoreWidget : GaugeWidget<SleepScoreWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(SleepScoreWidget::class.java)

    override val id = "sleepscore"
    override val label = R.string.sleep_score
    override val icon = R.drawable.ic_star_gray
    override val chartTab = "sleep"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsSleepScore(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var value = -1
        try {
            var latest: SleepScoreSample? = null
            for (dev in scope.devices) {
                val sample = scope.db { db ->
                    dev.deviceCoordinator.getSleepScoreProvider(dev, db.daoSession)?.getLatestSample(
                        scope.query.timeTo * 1000L
                    )
                }
                if (sample != null && (latest == null || sample.timestamp > latest.timestamp)) {
                    latest = sample
                }
            }
            if (latest != null) {
                value = latest.sleepScore
            }
        } catch (e: Exception) {
            LOG.error("Could not get sleep score", e)
        }
        return Data(value)
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        if (data.value > 0) {
            gaugeValue.text = data.value.toString()
            drawSimpleGauge(gaugeBar, WidgetColors.lightSleep, data.value / 100f)
        } else {
            // no device reported a sleep score for the day
            gaugeValue.text = context.getString(R.string.stats_empty_value)
            drawSimpleGauge(gaugeBar, Color.GRAY, -1f)
        }
    }

    data class Data(val value: Int)
}
