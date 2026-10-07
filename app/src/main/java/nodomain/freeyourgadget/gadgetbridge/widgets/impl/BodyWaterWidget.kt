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
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator

/**
 * Body water percentage estimated from the most recent weight measurement. Shown in a
 * neutral colour: the ranges quoted for it vary between sources.
 */
object BodyWaterWidget : BodyCompositionWidget() {
    override val id = "bodywater"
    override val label = R.string.body_composition_body_water
    override val icon = R.drawable.ic_weight

    override fun valueOf(composition: BodyCompositionCalculator.BodyComposition) = composition.bodyWaterPercent

    override fun format(context: Context, value: Float): String =
        context.getString(R.string.body_composition_percent, value)

    override fun scale(value: Float): Float = fraction(value, 35f, 75f)
}
