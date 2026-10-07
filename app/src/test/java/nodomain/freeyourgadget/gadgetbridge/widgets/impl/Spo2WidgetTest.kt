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
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Spo2WidgetTest : TestBase() {
    private fun render(data: Spo2Widget.Data): String {
        val instance = WidgetInstance("test-spo2", Spo2Widget.id, 1)
        val config = WidgetConfig(instance, Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId)), true, emptySet())
        val view = Spo2Widget.createView(LayoutInflater.from(context), FrameLayout(context))
        Spo2Widget.bind(view, config, data)
        return view.findViewById<TextView>(R.id.gauge_value).text.toString()
    }

    @Test
    fun noMeasurementShowsEmptyValue() {
        assertEquals(context.getString(R.string.stats_empty_value), render(Spo2Widget.Data(0)))
    }

    @Test
    fun measurementIsShownAsPercentage() {
        val text = render(Spo2Widget.Data(97))
        assertEquals(context.getString(R.string.battery_percentage_str, "97"), text)
        assertTrue("expected the value in '$text'", text.contains("97"))
    }
}
