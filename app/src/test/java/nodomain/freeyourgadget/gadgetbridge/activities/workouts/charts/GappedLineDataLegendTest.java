/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class GappedLineDataLegendTest extends TestBase {
    @Test
    public void gappedSeriesIsNamedOnceInTheLegend() {
        // three runs of one-second samples separated by two long pauses
        final List<Entry> entries = new ArrayList<>();
        for (final int start : new int[]{0, 100, 200}) {
            for (int i = 0; i < 10; i++) {
                entries.add(new Entry(start + i, 5));
            }
        }

        final LineData data = DefaultWorkoutCharts.createGappedLineData(getContext(), entries, "Power", 0);

        assertEquals(3, data.getDataSetCount());
        assertEquals("Power", data.getDataSetByIndex(0).getLabel());
        assertNull(data.getDataSetByIndex(1).getLabel());
        assertEquals(Legend.LegendForm.NONE, data.getDataSetByIndex(1).getForm());
        assertEquals(Legend.LegendForm.NONE, data.getDataSetByIndex(2).getForm());
    }
}
