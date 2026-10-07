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
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionEstimates
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope

/**
 * Base for the widgets showing a body composition value of the most recent weight
 * measurement. Like the weight widget they show the last known measurement rather than one
 * for the selected day, since a scale is not stepped on every day. The values are estimated
 * from the measurement's impedance when shown, the same way the weight chart does it.
 */
abstract class BodyCompositionWidget : GaugeWidget<BodyCompositionWidget.Data>() {
    override val chartTab = "weight"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsWeightMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val sample = WeightWidget.latestWeightSample(scope)
        val composition = sample?.let { s -> scope.db { db -> BodyCompositionEstimates.composition(db.daoSession, s) } }
        return Data(composition, ActivityUser().gender)
    }

    final override fun draw(context: Context, gaugeValue: TextView, gaugeBar: ImageView, data: Data) {
        val value = data.composition?.let { valueOf(it) }
        if (value == null) {
            gaugeValue.text = context.getString(R.string.stats_empty_value)
            drawSimpleGauge(gaugeBar, Color.GRAY, -1f)
            return
        }
        gaugeValue.text = format(context, value)
        drawSimpleGauge(gaugeBar, colorFor(value, data), scale(value))
    }

    /** The value this widget shows. */
    protected abstract fun valueOf(composition: BodyCompositionCalculator.BodyComposition): Float

    protected abstract fun format(context: Context, value: Float): String

    /** Where the value sits on the gauge, as a 0..1 fraction. */
    protected abstract fun scale(value: Float): Float

    /**
     * Colour for the value. Neutral by default, for values without a generally agreed
     * healthy range; body fat overrides it with published categories.
     */
    protected open fun colorFor(value: Float, data: Data): Int = NEUTRAL

    data class Data(val composition: BodyCompositionCalculator.BodyComposition?, val gender: Int)

    companion object {
        internal val GREEN = Color.rgb(76, 175, 80)
        internal val ORANGE = Color.rgb(255, 152, 0)
        internal val RED = Color.rgb(244, 67, 54)
        internal val NEUTRAL = Color.rgb(3, 169, 244)

        internal fun fraction(value: Float, min: Float, max: Float): Float =
            ((value - min) / (max - min)).coerceIn(0f, 1f)
    }
}
