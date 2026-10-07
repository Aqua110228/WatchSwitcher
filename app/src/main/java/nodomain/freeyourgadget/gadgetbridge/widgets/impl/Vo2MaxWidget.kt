package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.VO2MaxRanges
import nodomain.freeyourgadget.gadgetbridge.devices.Vo2MaxSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Base for the VO2 Max widgets (any/running/cycling sport).
 */
abstract class Vo2MaxGaugeWidget : GaugeWidget<Vo2MaxGaugeWidget.Data>() {
    abstract val vo2MaxType: Vo2MaxSample.Type

    override val chartTab = "vo2max"

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var value = -1f
        try {
            scope.db { db ->
                var latest: Vo2MaxSample? = null
                for (dev in scope.devices) {
                    @Suppress("UNCHECKED_CAST")
                    val provider = dev.deviceCoordinator.getVo2MaxSampleProvider(dev, db.daoSession)
                            as Vo2MaxSampleProvider<Vo2MaxSample>
                    val sample = provider.getLatestSample(vo2MaxType, scope.query.timeTo * 1000L)
                    if (sample != null && (latest == null || sample.timestamp > latest.timestamp)) {
                        latest = sample
                    }
                }
                if (latest != null) {
                    value = latest.value
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get vo2max for today", e)
        }

        val activityUser = ActivityUser()
        val selectedDay = LocalDate.ofInstant(
            Instant.ofEpochSecond(scope.query.timeTo.toLong()),
            ZoneId.systemDefault()
        )
        return Data(value, age = activityUser.getAgeAt(selectedDay), gender = activityUser.gender)
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        val percentile = VO2MaxRanges.calculateVO2MaxPercentile(
            if (data.value != -1f) data.value else 0f,
            data.age,
            data.gender,
        )
        gaugeValue.text = if (data.value != -1f) {
            String.format(Locale.getDefault(), "%.1f", data.value)
        } else {
            "-"
        }
        drawSegmentedGauge(gaugeBar, colors(context), SEGMENTS, percentile, false, true)
    }

    data class Data(val value: Float, val age: Int, val gender: Int)

    companion object {
        private val LOG = LoggerFactory.getLogger(Vo2MaxGaugeWidget::class.java)

        @JvmStatic
        fun colors(context: Context) = intArrayOf(
            ContextCompat.getColor(context, R.color.vo2max_value_poor_color),
            ContextCompat.getColor(context, R.color.vo2max_value_fair_color),
            ContextCompat.getColor(context, R.color.vo2max_value_good_color),
            ContextCompat.getColor(context, R.color.vo2max_value_excellent_color),
            ContextCompat.getColor(context, R.color.vo2max_value_superior_color),
        )

        // Should match the percentiles in VO2MaxRanges
        @JvmField
        val SEGMENTS = floatArrayOf(0.40f, 0.20f, 0.20f, 0.15f, 0.05f)
    }
}

/**
 * VO2 Max, any sport type.
 */
object Vo2MaxAnyWidget : Vo2MaxGaugeWidget() {
    override val id = "vo2max"
    override val label = R.string.menuitem_vo2_max
    override val icon = R.drawable.ic_activity_running
    override val vo2MaxType = Vo2MaxSample.Type.ANY

    override fun isSupportedBy(device: GBDevice): Boolean = device.deviceCoordinator.supportsVO2Max(device)
}

/**
 * VO2 Max, running.
 */
object Vo2MaxRunningWidget : Vo2MaxGaugeWidget() {
    override val id = "vo2max_running"
    override val label = R.string.vo2max_running
    override val icon = R.drawable.ic_activity_running
    override val vo2MaxType = Vo2MaxSample.Type.RUNNING

    override fun isSupportedBy(device: GBDevice): Boolean = device.deviceCoordinator.supportsVO2MultiSport(device)
}

/**
 * VO2 Max, cycling.
 */
object Vo2MaxCyclingWidget : Vo2MaxGaugeWidget() {
    override val id = "vo2max_cycling"
    override val label = R.string.vo2max_cycling
    override val icon = R.drawable.ic_directions_bike
    override val vo2MaxType = Vo2MaxSample.Type.CYCLING

    override fun isSupportedBy(device: GBDevice): Boolean = device.deviceCoordinator.supportsVO2MultiSport(device)
}
