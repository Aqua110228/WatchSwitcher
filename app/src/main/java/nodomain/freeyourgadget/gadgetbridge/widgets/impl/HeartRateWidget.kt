/*  Copyright (C) 2026 oddballza

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesUtils
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import org.slf4j.LoggerFactory

/**
 * Most recent heart rate measurement of the day, taken from the activity samples so that
 * it works on any device that records heart rate, not only the ones that report daily
 * resting or manual measurements.
 */
object HeartRateWidget : GaugeWidget<HeartRateWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(HeartRateWidget::class.java)

    /**
     * Zone floors as a percentage of the maximum heart rate, the same split the heart rate
     * zone settings use for the "maximum heart rate" method: warm-up, fat burn, aerobic,
     * anaerobic and extreme. Anything below the first floor is resting.
     */
    private val ZONE_PERCENTAGES = intArrayOf(50, 60, 70, 80, 90)

    override val id = "heartrate"
    override val label = R.string.menuitem_hr
    override val icon = R.drawable.ic_heartrate
    override val chartTab = "heartrate"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsHeartRateMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var latestHeartRate = 0
        var latestTimestamp = 0

        try {
            val utils = HeartRateUtils.getInstance()
            for (dev in scope.devices) {
                for (sample in scope.allSamples(dev)) {
                    if (utils.isValidHeartRateValue(sample.heartRate) && sample.timestamp > latestTimestamp) {
                        latestTimestamp = sample.timestamp
                        latestHeartRate = sample.heartRate
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get heart rate samples", e)
        }

        return Data(latestHeartRate, maxHeartRate())
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        val colors = intArrayOf(
            Color.GRAY,
            ContextCompat.getColor(context, R.color.hr_zone_warm_up_color),
            ContextCompat.getColor(context, R.color.hr_zone_easy_color),
            ContextCompat.getColor(context, R.color.hr_zone_aerobic_color),
            ContextCompat.getColor(context, R.color.hr_zone_threshold_color),
            ContextCompat.getColor(context, R.color.hr_zone_maximum_color),
        )

        val maxHeartRate = data.maxHeartRate.coerceAtLeast(1)
        val floors = zoneFloors(maxHeartRate)
        val segments = FloatArray(colors.size)
        var previous = 0
        for (i in floors.indices) {
            segments[i] = (floors[i] - previous).toFloat() / maxHeartRate
            previous = floors[i]
        }
        segments[floors.size] = (maxHeartRate - previous).toFloat() / maxHeartRate

        val value: Float
        if (data.heartRate > 0) {
            gaugeValue.text = data.heartRate.toString()
            value = (data.heartRate.toFloat() / maxHeartRate).coerceIn(0f, 1f)
        } else {
            gaugeValue.text = context.getString(R.string.stats_empty_value)
            value = -1f
        }

        drawSegmentedGauge(
            gaugeBar,
            colors,
            segments,
            value,
            fadeOutsideDot = false,
            gapBetweenSegments = true,
        )
    }

    /** The user's maximum heart rate, estimated as 220 minus age like the heart rate zone settings do. */
    private fun maxHeartRate(): Int = HeartRateZonesUtils.MAXIMUM_HEART_RATE - ActivityUser().age

    /** The lowest heart rate of each of the five zones, in ascending order. */
    fun zoneFloors(maxHeartRate: Int): IntArray =
        IntArray(ZONE_PERCENTAGES.size) { Math.round(maxHeartRate * ZONE_PERCENTAGES[it] / 100f) }

    /** 0 when [heartRate] is below the first zone (resting), otherwise the zone number 1..5. */
    fun zoneFor(heartRate: Int, maxHeartRate: Int): Int =
        zoneFloors(maxHeartRate).count { heartRate >= it }

    data class Data(val heartRate: Int, val maxHeartRate: Int)
}
