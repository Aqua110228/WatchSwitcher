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
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator

/** Body fat percentage estimated from the most recent weight measurement. */
object BodyFatWidget : BodyCompositionWidget() {
    override val id = "bodyfat"
    override val label = R.string.body_composition_body_fat
    override val icon = R.drawable.ic_weight

    override fun valueOf(composition: BodyCompositionCalculator.BodyComposition) = composition.bodyFatPercent

    override fun format(context: Context, value: Float): String =
        context.getString(R.string.body_composition_percent, value)

    override fun scale(value: Float): Float = fraction(value, 5f, 50f)

    /**
     * American Council on Exercise categories: for men essential fat 2-5 %, athletes to
     * average 6-24 %, obese from 25 %; for women 10-13 %, 14-31 % and from 32 %. Essential
     * fat only is shown orange, the athlete-to-average span green, obese red. The categories
     * are defined for men and women only, so other profiles stay neutral.
     */
    override fun colorFor(value: Float, data: Data): Int {
        val (essentialLow, healthyLow, obese) = when (data.gender) {
            ActivityUser.GENDER_MALE -> Triple(2f, 6f, 25f)
            ActivityUser.GENDER_FEMALE -> Triple(10f, 14f, 32f)
            else -> return NEUTRAL
        }
        return when {
            value < essentialLow -> RED
            value < healthyLow -> ORANGE
            value < obese -> GREEN
            else -> RED
        }
    }
}
