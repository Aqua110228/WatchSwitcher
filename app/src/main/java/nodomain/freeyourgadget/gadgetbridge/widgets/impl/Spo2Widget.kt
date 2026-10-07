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
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import org.slf4j.LoggerFactory

/**
 * Latest blood oxygen saturation measurement for the day.
 */
object Spo2Widget : GaugeWidget<Spo2Widget.Data>() {
    private val LOG = LoggerFactory.getLogger(Spo2Widget::class.java)

    /**
     * SpO2 only varies within a narrow band, so the gauge spans
     * [GAUGE_MIN]..100% rather than 0..100%.
     */
    private const val GAUGE_MIN = 80

    override val id = "spo2"
    override val label = R.string.menuitem_spo2
    override val icon = R.drawable.ic_spo2
    override val chartTab = "spo2"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsSpo2(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var latestSpo2 = 0
        var latestTimestamp = 0L

        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    val provider = dev.deviceCoordinator.getSpo2SampleProvider(dev, db.daoSession) ?: continue
                    val samples: List<Spo2Sample> = provider.getAllSamples(
                        scope.query.timeFrom * 1000L,
                        scope.query.timeTo * 1000L
                    )
                    if (samples.isNotEmpty()) {
                        val latest = samples.last()
                        if (latest.spo2 > 0 && latest.timestamp > latestTimestamp) {
                            latestTimestamp = latest.timestamp
                            latestSpo2 = latest.spo2
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get spo2 samples", e)
        }

        return Data(latestSpo2)
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        if (data.spo2 > 0) {
            gaugeValue.text = context.getString(R.string.battery_percentage_str, data.spo2.toString())
            drawSimpleGauge(
                gaugeBar,
                colorFor(data.spo2),
                (data.spo2 - GAUGE_MIN).coerceAtLeast(0) / (100f - GAUGE_MIN)
            )
        } else {
            gaugeValue.text = context.getString(R.string.stats_empty_value)
            drawSimpleGauge(gaugeBar, Color.GRAY, -1f)
        }
    }

    /** Coloured by the usual clinical bands, as the blood pressure widget does. */
    private fun colorFor(spo2: Int): Int = when {
        spo2 >= 95 -> Color.rgb(76, 175, 80)   // green - normal
        spo2 >= 90 -> Color.rgb(255, 152, 0)   // orange - borderline
        else -> Color.rgb(244, 67, 54)         // red - low
    }

    data class Data(val spo2: Int)
}
