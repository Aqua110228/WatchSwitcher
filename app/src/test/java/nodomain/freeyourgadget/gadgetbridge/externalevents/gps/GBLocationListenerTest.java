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
package nodomain.freeyourgadget.gadgetbridge.externalevents.gps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Most fixes carry no speed, so the listener derives it from the distance and time to the
 * previous fix before passing the fix on to the device.
 */
public class GBLocationListenerTest extends TestBase {
    @Test
    public void speedComesFromDistanceOverTime() {
        final GBLocationListener listener = new GBLocationListener(createDummyGDevice("00:00:00:00:00:71"));
        final long now = SystemClock.elapsedRealtimeNanos();
        final Location first = fix(48.0, now - 1_000_000_000L);
        listener.onLocationChanged(first);
        final Location second = fix(48.0001, now);
        listener.onLocationChanged(second);

        final float seconds = (second.getTime() - first.getTime()) / 1000f;
        assertTrue(second.hasSpeed());
        assertEquals(first.distanceTo(second) / seconds, second.getSpeed(), 0.01f);
    }

    @Test
    public void fixNotNewerThanThePreviousGivesNoSpeed() {
        final GBLocationListener listener = new GBLocationListener(createDummyGDevice("00:00:00:00:00:72"));
        final long now = SystemClock.elapsedRealtimeNanos();
        listener.onLocationChanged(fix(48.0, now));
        final Location older = fix(48.0001, now - 1_000_000_000L);
        listener.onLocationChanged(older);

        assertFalse("speed " + older.getSpeed(), older.hasSpeed());
    }

    @Test
    public void fixTimeIsWallClockMinusItsAge() {
        final GBLocationListener listener = new GBLocationListener(createDummyGDevice("00:00:00:00:00:73"));
        final Location fix = fix(48.0, SystemClock.elapsedRealtimeNanos() - 2_000_000_000L);

        final long before = System.currentTimeMillis();
        listener.onLocationChanged(fix);
        final long after = System.currentTimeMillis();

        assertTrue("fix stamped " + (before - fix.getTime()) + " ms ago, expected 2000",
                fix.getTime() >= before - 2_000L && fix.getTime() <= after - 2_000L);
    }

    private static Location fix(final double latitude, final long elapsedRealtimeNanos) {
        final Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(latitude);
        location.setLongitude(11.0);
        location.setElapsedRealtimeNanos(elapsedRealtimeNanos);
        return location;
    }
}
