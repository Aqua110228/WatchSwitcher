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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.mikephil.charting.data.Entry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;

/**
 * Some sources (Xiaomi/Bangle GPS tracks) expose position but no distance field, so the distance
 * chart used to stay empty for them. {@link DefaultWorkoutCharts#deriveCumulativeDistances} fills a
 * running distance from the fixes when none is provided. These checks pin that derivation (haversine,
 * cumulative metres) without touching Android's {@code Location} or the chart rendering.
 */
public class DefaultWorkoutChartsTest {

    private static ActivityPoint point(final long ts, final double lon, final double lat) {
        final ActivityPoint ap = new ActivityPoint(new Date(ts));
        ap.setLocation(new GPSCoordinate(lon, lat));
        return ap;
    }

    /** An indoor point: machine-reported speed (m/s), no location. */
    private static ActivityPoint speedPoint(final long ts, final float speedMps) {
        final ActivityPoint ap = new ActivityPoint(new Date(ts));
        ap.setSpeed(speedMps);
        return ap;
    }

    @Test
    public void firstFixIsZeroAndDistanceAccumulates() {
        // 45.000,5.000 -> 45.000,5.001 is ~78.6 m of easting at this latitude;
        // -> 45.001,5.001 adds ~111.2 m of northing.
        final List<ActivityPoint> points = Arrays.asList(
                point(1000, 5.000, 45.000),
                point(2000, 5.001, 45.000),
                point(3000, 5.001, 45.001));

        final double[] d = DefaultWorkoutCharts.deriveCumulativeDistances(points);

        assertEquals(0.0, d[0], 1e-9);
        assertEquals(78.6, d[1], 1.0);
        assertEquals(78.6 + 111.2, d[2], 1.5);
        assertTrue("monotonic non-decreasing", d[2] > d[1]);
    }

    @Test
    public void nullIslandFixesAreSkippedAndDoNotJumpDistance() {
        final List<ActivityPoint> points = Arrays.asList(
                point(1000, 5.001, 45.000),
                point(2000, 0.0, 0.0),      // no-fix placeholder
                point(3000, 5.001, 45.001));

        final double[] d = DefaultWorkoutCharts.deriveCumulativeDistances(points);

        assertEquals(0.0, d[0], 1e-9);
        assertTrue("no-fix point has no distance", Double.isNaN(d[1]));
        // distance across the dropout is p0->p2 (~111.2 m northing), not a jump via 0,0
        assertEquals(111.2, d[2], 1.5);
    }

    @Test
    public void pointsWithoutLocationAreSkipped() {
        final List<ActivityPoint> points = Arrays.asList(
                point(1000, 5.000, 45.000),
                new ActivityPoint(new Date(2000)), // no location
                point(3000, 5.001, 45.000));

        final double[] d = DefaultWorkoutCharts.deriveCumulativeDistances(points);

        assertTrue("point without location has no distance", Double.isNaN(d[1]));
        assertEquals(78.6, d[2], 1.0);
    }

    @Test
    public void nativeDistanceIsHonouredNotDerived() {
        final ActivityPoint withDistance = point(1000, 5.000, 45.000);
        withDistance.setDistance(42.0);
        final List<ActivityPoint> points = Arrays.asList(
                withDistance,
                point(2000, 5.001, 45.000));

        // Any point carrying a distance => derivation stands down entirely.
        assertNull(DefaultWorkoutCharts.deriveCumulativeDistances(points));
    }

    @Test
    public void noUsableFixesOrSpeedReturnsNull() {
        final List<ActivityPoint> points = Arrays.asList(
                new ActivityPoint(new Date(1000)),
                point(2000, 0.0, 0.0));

        assertNull(DefaultWorkoutCharts.deriveCumulativeDistances(points));
    }

    @Test
    public void indoorSpeedIsIntegratedOverTimeWhenNoGps() {
        // 2 m/s held across two 1 s intervals -> 2 m then 4 m (trapezoid rule).
        final List<ActivityPoint> points = Arrays.asList(
                speedPoint(1000, 2.0f),
                speedPoint(2000, 2.0f),
                speedPoint(3000, 2.0f));

        final double[] d = DefaultWorkoutCharts.deriveCumulativeDistances(points);

        assertEquals(0.0, d[0], 1e-9);
        assertEquals(2.0, d[1], 1e-9);
        assertEquals(4.0, d[2], 1e-9);
    }

    @Test
    public void speedGapBridgesFromLastSampleAndNoDistanceOnGap() {
        final List<ActivityPoint> points = Arrays.asList(
                speedPoint(1000, 3.0f),
                new ActivityPoint(new Date(2000)), // no speed
                speedPoint(3000, 3.0f));

        final double[] d = DefaultWorkoutCharts.deriveCumulativeDistances(points);

        assertEquals(0.0, d[0], 1e-9);
        assertTrue("no-speed point has no distance", Double.isNaN(d[1]));
        // bridges the 2 s gap using the last valid speed: 0.5*(3+3)*2 = 6 m
        assertEquals(6.0, d[2], 1e-9);
    }

    @Test
    public void gpsWinsOverSpeedWhenBothPresent() {
        // Points carry both a fix and a speed; the GPS derivation must take priority.
        final ActivityPoint p0 = point(1000, 5.000, 45.000);
        final ActivityPoint p1 = point(2000, 5.001, 45.000);
        p0.setSpeed(50.0f); // absurd speed that would dominate if integrated
        p1.setSpeed(50.0f);

        final double[] d = DefaultWorkoutCharts.deriveCumulativeDistances(Arrays.asList(p0, p1));

        assertEquals(0.0, d[0], 1e-9);
        assertEquals(78.6, d[1], 1.0); // ~78.6 m from the fixes, not 50 m/s * 1 s
    }

    private static List<Entry> entriesEvery(final long stepMs, final int count) {
        final List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(i * stepMs, 25));
        }
        return entries;
    }

    @Test
    public void perSecondAndHalfSecondSeriesAreDense() {
        assertTrue(DefaultWorkoutCharts.isDenseSeries(entriesEvery(1000, 60)));
        assertTrue(DefaultWorkoutCharts.isDenseSeries(entriesEvery(500, 60)));
    }

    @Test
    public void sparseSeriesIsNotDense() {
        assertFalse(DefaultWorkoutCharts.isDenseSeries(entriesEvery(5000, 60)));
    }

    @Test
    public void pauseDoesNotMakeDenseSeriesSparse() {
        final List<Entry> entries = entriesEvery(1000, 30);
        for (int i = 0; i < 30; i++) {
            entries.add(new Entry(600_000 + i * 1000, 25));
        }
        assertTrue(DefaultWorkoutCharts.isDenseSeries(entries));
    }

    @Test
    public void tooFewPointsAreNotDense() {
        assertFalse(DefaultWorkoutCharts.isDenseSeries(entriesEvery(1000, 2)));
    }
}
