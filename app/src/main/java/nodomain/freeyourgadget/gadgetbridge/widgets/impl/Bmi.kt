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

import android.graphics.Color

/**
 * Body mass index and its WHO classification, shared by the widgets that show or colour by it.
 */
internal object Bmi {
    private val ORANGE = Color.rgb(255, 152, 0)
    private val GREEN = Color.rgb(76, 175, 80)
    private val RED = Color.rgb(244, 67, 54)

    /** BMI from weight and height, or null when either is missing. */
    fun of(weightKg: Double, heightCm: Int): Double? {
        if (weightKg <= 0 || heightCm <= 0) return null
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }

    /** WHO classification: underweight and overweight orange, normal green, obese red. */
    fun colorFor(bmi: Double): Int = when {
        bmi < 18.5 -> ORANGE
        bmi < 25 -> GREEN
        bmi < 30 -> ORANGE
        else -> RED
    }

    /** Position on the gauge, one step per WHO class. */
    fun gaugeFraction(bmi: Double): Float = when {
        bmi < 18.5 -> 0.15f
        bmi < 25 -> 0.40f
        bmi < 30 -> 0.65f
        else -> 0.88f
    }
}
