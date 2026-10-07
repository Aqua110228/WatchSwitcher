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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateWidgetTest : TestBase() {
    private fun render(data: HeartRateWidget.Data): String {
        val instance = WidgetInstance("test-heartrate", HeartRateWidget.id, 1)
        val config = WidgetConfig(instance, Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId)), true, emptySet())
        val view = HeartRateWidget.createView(LayoutInflater.from(context), FrameLayout(context))
        HeartRateWidget.bind(view, config, data)
        return view.findViewById<TextView>(R.id.gauge_value).text.toString()
    }

    @Test
    fun noMeasurementShowsEmptyValue() {
        assertEquals(context.getString(R.string.stats_empty_value), render(HeartRateWidget.Data(0, 190)))
    }

    @Test
    fun heartRateIsShown() {
        assertEquals("72", render(HeartRateWidget.Data(72, 190)))
    }

    @Test
    fun zoneFloorsAreFiftyToNinetyPercentOfMax() {
        assertArrayEquals(intArrayOf(95, 114, 133, 152, 171), HeartRateWidget.zoneFloors(190))
    }

    @Test
    fun zoneIsRestingBelowHalfOfMax() {
        assertEquals(0, HeartRateWidget.zoneFor(60, 190))
        assertEquals(0, HeartRateWidget.zoneFor(94, 190))
    }

    @Test
    fun zoneStartsAtItsFloor() {
        assertEquals(1, HeartRateWidget.zoneFor(95, 190))
        assertEquals(1, HeartRateWidget.zoneFor(113, 190))
        assertEquals(2, HeartRateWidget.zoneFor(114, 190))
        assertEquals(3, HeartRateWidget.zoneFor(133, 190))
        assertEquals(4, HeartRateWidget.zoneFor(152, 190))
        assertEquals(5, HeartRateWidget.zoneFor(171, 190))
    }

    @Test
    fun zoneDependsOnMax() {
        // The same 110 bpm is fat-burn for a 60-year-old (max 160) and only warm-up for a 20-year-old (max 200).
        assertEquals(2, HeartRateWidget.zoneFor(110, 160))
        assertEquals(1, HeartRateWidget.zoneFor(110, 200))
    }

    @Test
    fun heartRateAboveMaxIsStillShown() {
        assertEquals("205", render(HeartRateWidget.Data(205, 190)))
    }
}
