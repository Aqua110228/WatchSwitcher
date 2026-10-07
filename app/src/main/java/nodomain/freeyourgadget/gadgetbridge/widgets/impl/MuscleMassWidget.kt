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
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator

/**
 * Skeletal muscle mass estimated from the most recent weight measurement. There is no
 * generally agreed healthy range to judge it against, so it is shown in a neutral colour.
 */
object MuscleMassWidget : BodyCompositionWidget() {
    override val id = "musclemass"
    override val label = R.string.body_composition_muscle_mass
    override val icon = R.drawable.ic_weight

    override fun valueOf(composition: BodyCompositionCalculator.BodyComposition) = composition.muscleMassKg

    override fun format(context: Context, value: Float): String =
        WeightUnit.formatWeight(context, value.toDouble(), GBApplication.getPrefs().weightUnit)

    override fun scale(value: Float): Float = fraction(value, 20f, 60f)
}
