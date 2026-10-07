/*  Copyright (C) 2026 José Rebelo

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
import android.widget.ImageView
import android.widget.TextView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import java.text.NumberFormat

/**
 * Total steps for the day against the user's step goal.
 */
object StepsWidget : GaugeWidget<StepsWidget.Data>() {
    override val id = "steps"
    override val label = R.string.steps
    override val icon = R.drawable.ic_steps
    override val chartTab = "stepsweek"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsStepCounter(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val total = scope.stepsTotal()
        return Data(total, scope.stepsGoalFactor(total))
    }

    override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        gaugeValue.text = NumberFormat.getInstance().format(data.total)
        drawSimpleGauge(gaugeBar, WidgetColors.activity, data.goalFactor)
    }

    data class Data(val total: Int, val goalFactor: Float)
}
