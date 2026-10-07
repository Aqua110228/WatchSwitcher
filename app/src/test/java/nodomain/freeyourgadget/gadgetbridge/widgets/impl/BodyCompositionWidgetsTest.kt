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

import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BodyCompositionWidgetsTest : TestBase() {
    // Synthetic profile and reading (the calculator's own test vectors), not a real person's.
    private val composition = BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 30, 180, 80f, 500f)

    private fun render(widget: BodyCompositionWidget, data: BodyCompositionWidget.Data): String {
        val instance = WidgetInstance("test-" + widget.id, widget.id, 1)
        val config = WidgetConfig(instance, Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId)), true, emptySet())
        val view = widget.createView(LayoutInflater.from(context), FrameLayout(context))
        widget.bind(view, config, data)
        return view.findViewById<TextView>(R.id.gauge_value).text.toString()
    }

    private fun data() = BodyCompositionWidget.Data(composition, ActivityUser.GENDER_MALE)

    @Test
    fun eachWidgetShowsItsOwnValue() {
        assertNotNull(composition)
        val c = composition!!
        assertEquals(context.getString(R.string.body_composition_percent, c.bodyFatPercent), render(BodyFatWidget, data()))
        assertEquals(context.getString(R.string.body_composition_percent, c.bodyWaterPercent), render(BodyWaterWidget, data()))
        assertEquals(WeightUnit.formatWeight(context, c.muscleMassKg.toDouble(), GBApplication.getPrefs().weightUnit), render(MuscleMassWidget, data()))
        assertEquals(context.getString(R.string.body_composition_kcal, c.basalMetabolicRate), render(BasalMetabolicRateWidget, data()))
        // and they are four different numbers, so a mix-up between them would show
        assertEquals(4, setOf(c.bodyFatPercent, c.bodyWaterPercent, c.muscleMassKg, c.basalMetabolicRate.toFloat()).size)
    }

    @Test
    fun noEstimateShowsEmptyValue() {
        val empty = BodyCompositionWidget.Data(null, ActivityUser.GENDER_MALE)
        for (w in listOf(BodyFatWidget, BodyWaterWidget, MuscleMassWidget, BasalMetabolicRateWidget)) {
            assertEquals(w.id, context.getString(R.string.stats_empty_value), render(w, empty))
        }
    }
}
