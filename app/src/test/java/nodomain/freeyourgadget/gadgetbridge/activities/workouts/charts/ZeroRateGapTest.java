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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class ZeroRateGapTest extends TestBase {
    /** 20 s moving, a 60 s stop reported as zeros, then 20 s moving, at 1 Hz. */
    private static List<ActivityPoint> stopAndGo() {
        final List<ActivityPoint> points = new ArrayList<>();
        for (int s = 0; s < 100; s++) {
            final boolean stopped = s >= 20 && s < 80;
            final ActivityPoint p = new ActivityPoint(new Date(1_700_000_000_000L + s * 1000L));
            p.setHeartRate(120);
            p.setSpeed(stopped ? 0f : 5f);
            p.setCadence(stopped ? 0 : 80);
            p.setRespiratoryRate(stopped ? 0f : 20f);
            points.add(p);
        }
        return points;
    }

    @Test
    public void zeroRatesGapTheSeriesOut() {
        final List<WorkoutChart> charts = DefaultWorkoutCharts.buildDefaultCharts(getContext(), stopAndGo(), ActivityKind.CYCLING);

        final WorkoutChart speed = find(charts, "speed");
        assertEquals(2, speed.getChartData().getDataSetCount());
        assertNoZeros(speed);

        final WorkoutChart respiratoryRate = find(charts, "respiratory_rate");
        assertEquals(2, respiratoryRate.getChartData().getDataSetCount());
        assertNoZeros(respiratoryRate);

        assertNoZeros(find(charts, "cadence"));
    }

    @Test
    public void allZeroRatesProduceNoChart() {
        final List<ActivityPoint> points = stopAndGo();
        for (final ActivityPoint p : points) {
            p.setSpeed(0f);
            p.setCadence(0);
            p.setRespiratoryRate(0f);
        }
        final List<WorkoutChart> charts = DefaultWorkoutCharts.buildDefaultCharts(getContext(), points, ActivityKind.CYCLING);

        assertNull(findOrNull(charts, "speed"));
        assertNull(findOrNull(charts, "cadence"));
        assertNull(findOrNull(charts, "respiratory_rate"));
    }

    private static void assertNoZeros(final WorkoutChart chart) {
        for (final IDataSet<? extends Entry> dataSet : chart.getChartData().getDataSets()) {
            for (int i = 0; i < dataSet.getEntryCount(); i++) {
                assertTrue(chart.getId() + " has a zero entry", dataSet.getEntryForIndex(i).getY() > 0f);
            }
        }
    }

    private static WorkoutChart find(final List<WorkoutChart> charts, final String id) {
        final WorkoutChart chart = findOrNull(charts, id);
        assertNotNull(id + " chart missing", chart);
        return chart;
    }

    private static WorkoutChart findOrNull(final List<WorkoutChart> charts, final String id) {
        for (final WorkoutChart chart : charts) {
            if (id.equals(chart.getId())) {
                return chart;
            }
        }
        return null;
    }
}
