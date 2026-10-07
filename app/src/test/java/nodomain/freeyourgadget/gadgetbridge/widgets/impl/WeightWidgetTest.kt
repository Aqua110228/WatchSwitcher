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
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightWidgetTest : TestBase() {
    private fun render(data: WeightWidget.Data): String {
        val instance = WidgetInstance("test-weight", WeightWidget.id, 1)
        val config = WidgetConfig(instance, Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId)), true, emptySet())
        val view = WeightWidget.createView(LayoutInflater.from(context), FrameLayout(context))
        WeightWidget.bind(view, config, data)
        return view.findViewById<TextView>(R.id.gauge_value).text.toString()
    }

    private fun expected(kg: Double): String =
        WeightUnit.formatWeight(context, kg, GBApplication.getPrefs().weightUnit)

    @Test
    fun noMeasurementShowsEmptyValue() {
        assertEquals(context.getString(R.string.stats_empty_value), render(WeightWidget.Data(0.0, 180)))
    }

    @Test
    fun weightShownWithoutHeight() {
        val text = render(WeightWidget.Data(80.0, 0))
        assertEquals(expected(80.0), text)
        assertTrue("expected the value in '$text'", text.contains("80"))
    }

    @Test
    fun weightShownWithHeight() {
        assertEquals(expected(80.0), render(WeightWidget.Data(80.0, 180)))
    }
}
