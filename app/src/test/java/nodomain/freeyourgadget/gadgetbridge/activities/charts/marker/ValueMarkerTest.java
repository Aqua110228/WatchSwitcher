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
package nodomain.freeyourgadget.gadgetbridge.activities.charts.marker;

import static org.junit.Assert.assertEquals;

import android.view.ContextThemeWrapper;
import android.widget.TextView;

import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.HeartRateZoneChartUtils;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class ValueMarkerTest extends TestBase {
    private static List<Entry> run(final int fromX, final int toX, final float y) {
        final List<Entry> entries = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            entries.add(new Entry(x, y));
        }
        return entries;
    }

    private String markerTextAt(final float x) {
        // A gapped HR series: only the first segment is labelled. A zone band spans both.
        final List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(new HeartRateZoneChartUtils.ZoneAreaDataSet(run(0, 30, 100)));
        dataSets.add(new LineDataSet(run(0, 10, 120), "HR"));
        dataSets.add(new LineDataSet(run(20, 30, 140), null));
        final CombinedData data = new CombinedData();
        data.setData(new LineData(dataSets));

        final Map<String, ValueFormatter> formatters = Collections.singletonMap("HR", new ValueFormatter() {
            @Override
            public String getFormattedValue(final float value) {
                return String.valueOf((int) value);
            }
        });
        final ValueMarker marker = new ValueMarker(new ContextThemeWrapper(getContext(), R.style.GadgetbridgeTheme),
                data, formatters, Collections.singletonMap("HR", "bpm"));
        marker.refreshContent(new Entry(x, 0), new Highlight(x, 0, 0));
        return ((TextView) marker.findViewById(R.id.marker_content)).getText().toString();
    }

    @Test
    public void laterGapSegmentUsesTheSeriesFormatterAndUnit() {
        assertEquals("140 bpm", markerTextAt(25));
    }

    @Test
    public void firstSegmentReportsOnce() {
        assertEquals("120 bpm", markerTextAt(5));
    }

    @Test
    public void nothingIsReportedInsideAGap() {
        assertEquals("", markerTextAt(15));
    }
}
