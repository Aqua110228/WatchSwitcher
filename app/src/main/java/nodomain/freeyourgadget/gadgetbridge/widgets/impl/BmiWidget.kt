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
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import java.util.Locale

/**
 * Body mass index of the most recent weight measurement, from the profile height. Coloured by
 * WHO class like the weight widget's gauge, but showing the index itself.
 */
object BmiWidget : GaugeWidget<WeightWidget.Data>() {
    override val id = "bmi"
    override val label = R.string.body_mass_index
    override val icon = R.drawable.ic_monitor_weight
    override val chartTab = "weight"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsWeightMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): WeightWidget.Data =
        WeightWidget.loadData(scope, config)

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: WeightWidget.Data) {
        val bmi = Bmi.of(data.weightKg, data.heightCm)
        if (bmi == null) {
            gaugeValue.text = context.getString(R.string.stats_empty_value)
            drawSimpleGauge(gaugeBar, Color.GRAY, -1f)
            return
        }
        gaugeValue.text = String.format(Locale.getDefault(), "%.1f", bmi)
        drawSimpleGauge(gaugeBar, Bmi.colorFor(bmi), Bmi.gaugeFraction(bmi))
    }
}
