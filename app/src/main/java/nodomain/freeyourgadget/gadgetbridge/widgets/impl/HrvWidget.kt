package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import org.slf4j.LoggerFactory

/**
 * Weekly-average HRV, and the baseline bands.
 */
object HrvWidget : GaugeWidget<HrvWidget.Data?>() {
    private val LOG = LoggerFactory.getLogger(HrvWidget::class.java)

    override val id = "hrv"
    override val label = R.string.hrv
    override val icon = R.drawable.ic_heartrate
    override val chartTab = "hrvstatus"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsHrvMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data? {
        try {
            val latest = scope.db { db ->
                var latest: HrvSummarySample? = null
                for (dev in scope.devices) {
                    val provider = dev.deviceCoordinator.getHrvSummarySampleProvider(dev, db.daoSession)
                    val deviceSummaries = provider?.getAllSamples(
                        scope.query.timeFrom * 1000L,
                        scope.query.timeTo * 1000L
                    )
                    if (deviceSummaries.isNullOrEmpty()) {
                        continue
                    }
                    val candidate = deviceSummaries.last()
                    if (latest == null || candidate.timestamp >= latest.timestamp) {
                        latest = candidate
                    }
                }
                latest
            }

            if (latest != null) {
                return Data(
                    weeklyAverage = latest.weeklyAverage ?: 0,
                    baselineLowUpper = latest.baselineLowUpper ?: 0,
                    baselineBalancedLower = latest.baselineBalancedLower ?: 0,
                    baselineBalancedUpper = latest.baselineBalancedUpper ?: 0,
                )
            }
        } catch (e: Exception) {
            LOG.error("Could not get hrv sample", e)
        }
        return null
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data?) {
        val value = if (data != null) {
            calculateGaugeValue(
                data.weeklyAverage,
                data.baselineLowUpper,
                data.baselineBalancedLower,
                data.baselineBalancedUpper
            )
        } else {
            -1f
        }
        gaugeValue.text = if (data != null && data.weeklyAverage > 0) {
            context.getString(R.string.hrv_status_unit, data.weeklyAverage)
        } else {
            context.getString(R.string.stats_empty_value)
        }
        drawSegmentedGauge(gaugeBar, colors(context), SEGMENTS, value, false, true)
    }

    @JvmStatic
    fun colors(context: Context) = intArrayOf(
        ContextCompat.getColor(context, R.color.hrv_status_low),
        ContextCompat.getColor(context, R.color.hrv_status_unbalanced),
        ContextCompat.getColor(context, R.color.hrv_status_balanced),
        ContextCompat.getColor(context, R.color.hrv_status_unbalanced),
    )

    @JvmField
    val SEGMENTS = floatArrayOf(0.125f, 0.125f, 0.5f, 0.25f)

    @JvmStatic
    fun calculateGaugeValue(
        weeklyAverage: Int,
        baselineLowUpper: Int,
        baselineBalancedLower: Int,
        baselineBalancedUpper: Int,
    ): Float {
        if (weeklyAverage == 0 || baselineLowUpper == 0 || baselineBalancedLower == 0 || baselineBalancedUpper == 0) {
            return -1f
        }
        return when {
            weeklyAverage <= baselineLowUpper ->
                GaugeDrawer.normalize(weeklyAverage.toDouble(), 0.0, baselineLowUpper.toDouble(), 0.0, 0.124).toFloat()

            weeklyAverage < baselineBalancedLower ->
                GaugeDrawer.normalize(
                    weeklyAverage.toDouble(),
                    (baselineLowUpper + 1).toDouble(),
                    (baselineBalancedLower - 1).toDouble(),
                    0.126,
                    0.249
                ).toFloat()

            weeklyAverage <= baselineBalancedUpper ->
                GaugeDrawer.normalize(
                    weeklyAverage.toDouble(),
                    baselineBalancedLower.toDouble(),
                    baselineBalancedUpper.toDouble(),
                    0.251,
                    0.749
                ).toFloat()

            else ->
                GaugeDrawer.normalize(
                    weeklyAverage.toDouble(),
                    baselineBalancedUpper.toDouble(),
                    (2 * baselineBalancedUpper).toDouble(),
                    0.751,
                    1.0
                ).toFloat()
        }
    }

    data class Data(
        val weeklyAverage: Int,
        val baselineLowUpper: Int,
        val baselineBalancedLower: Int,
        val baselineBalancedUpper: Int,
    )
}
