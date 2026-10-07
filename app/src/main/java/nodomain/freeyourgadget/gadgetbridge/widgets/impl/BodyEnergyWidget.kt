package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import org.slf4j.LoggerFactory
import kotlin.math.abs

/**
 * Body Energy: today's current level, or the gained/lost breakdown for a past day.
 */
object BodyEnergyWidget : GaugeWidget<BodyEnergyWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(BodyEnergyWidget::class.java)

    override val id = "bodyenergy"
    override val label = R.string.body_energy
    override val icon = R.drawable.ic_bolt
    override val chartTab = "bodyenergy"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsBodyEnergy(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val isToday = DateUtils.isToday(scope.query.timeTo * 1000L)
        val data = Data(isToday = isToday)

        if (isToday) {
            try {
                var latest: BodyEnergySample? = null
                for (dev in scope.devices) {
                    val sample = scope.db { db ->
                        dev.deviceCoordinator.getBodyEnergySampleProvider(dev, db.daoSession)?.latestSample
                    }
                    if (sample != null && (latest == null || sample.timestamp > latest.timestamp)) {
                        latest = sample
                    }
                }
                if (latest != null) {
                    data.value = latest.energy
                }
            } catch (e: Exception) {
                LOG.error("Could not get body energy for today", e)
            }
        } else {
            try {
                for (dev in scope.devices) {
                    val samples = scope.db { db ->
                        dev.deviceCoordinator.getBodyEnergySampleProvider(dev, db.daoSession)?.getAllSamples(
                            scope.query.timeFrom * 1000L,
                            scope.query.timeTo * 1000L
                        )
                    }
                    if (samples == null) {
                        continue
                    }
                    if (samples.size > 1) {
                        var gained = 0
                        var lost = 0
                        for (i in 1 until samples.size) {
                            val s1 = samples[i - 1]
                            val s2 = samples[i]
                            if (s2.energy > s1.energy) {
                                gained += s2.energy - s1.energy
                            } else {
                                lost += s1.energy - s2.energy
                            }
                        }
                        data.gained = gained
                        data.lost = lost
                    }
                }
            } catch (e: Exception) {
                LOG.error("Could not calculate body energy change", e)
            }
        }

        return data
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        val colorEnergy = ContextCompat.getColor(context, R.color.body_energy_level_color)

        if (data.isToday) {
            if (data.value < 0) {
                drawSimpleGauge(gaugeBar, 0, -1f)
                return
            }
            gaugeValue.text = context.getString(R.string.body_energy_percentage, data.value)
            drawSimpleGauge(gaugeBar, colorEnergy, data.value / 100f)
        } else {
            if (data.gained < 0 || data.lost < 0) {
                drawSimpleGauge(gaugeBar, 0, -1f)
                return
            }

            val diff = data.gained - data.lost

            val spanGain = SpannableString("↑${data.gained}")
            val spanLost = SpannableString("↓${data.lost}")
            spanGain.setSpan(RelativeSizeSpan(0.65f), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spanLost.setSpan(RelativeSizeSpan(0.65f), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            gaugeValue.text = TextUtils.concat(spanGain, " ", spanLost)
            drawSimpleGauge(gaugeBar, colorEnergy, abs(diff) / 100f)

            val colors = intArrayOf(
                colorEnergy,
                ContextCompat.getColor(context, R.color.body_energy_lost_color),
            )
            val total = (data.gained + data.lost).toFloat()
            val segments = floatArrayOf(data.gained / total, data.lost / total)

            drawSegmentedGauge(gaugeBar, colors, segments, -1f, false, true)
        }
    }

    data class Data(
        var value: Int = -1,
        var gained: Int = -1,
        var lost: Int = -1,
        val isToday: Boolean,
    )
}
