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
package nodomain.freeyourgadget.gadgetbridge.service;

import android.os.Bundle;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

/**
 * Which sensors a session asked for, as read from the START_TRACKING extras.
 */
public class SleepAsAndroidSenderSensorsTest extends TestBase {

    private static final String ADDRESS = "00:11:22:33:44:55";

    private SleepAsAndroidSender sender;

    @Before
    public void setUpSender() {
        final GBDevice device = createDummyGDevice(ADDRESS);
        device.setState(GBDevice.State.INITIALIZED);

        GBApplication.getPrefs().getPreferences().edit()
                .putBoolean(GBPrefs.SLEEP_AS_ANDROID_ENABLED, true)
                .putString(GBPrefs.SLEEP_AS_ANDROID_DEVICE, ADDRESS)
                .apply();

        sender = new SleepAsAndroidSender(device);
    }

    @After
    public void tearDownSender() {
        sender.stopTracking();
    }

    /** Sleep as Android marks the sensors it wants by adding the extra at all. */
    private static Bundle trackingExtras(final boolean heartRate, final boolean oximetry) {
        final Bundle extras = new Bundle();
        if (heartRate) {
            extras.putBoolean("DO_HR_MONITORING", true);
        }
        if (oximetry) {
            extras.putBoolean("DO_OXIMETER_MONITORING", true);
        }
        return extras;
    }

    @Test
    public void sensorRequestFollowsExtraPresence() {
        sender.startTracking(trackingExtras(true, false));
        Assert.assertTrue(sender.isHeartRateRequested());
        Assert.assertFalse(sender.isOximetryRequested());

        sender.stopTracking();
        sender.startTracking(null);
        Assert.assertFalse(sender.isHeartRateRequested());
        Assert.assertFalse(sender.isOximetryRequested());
    }

    @Test
    public void theWatchdogRestartKeepsTheSensorRequest() {
        sender.startTracking(trackingExtras(true, true));

        // Sleep as Android repeats START_TRACKING as its own watchdog, and that repeat carries the
        // heart rate extra alone, which must not read as the user turning oximetry off.
        sender.startTracking(trackingExtras(true, false));

        Assert.assertTrue(sender.isHeartRateRequested());
        Assert.assertTrue(sender.isOximetryRequested());
    }

    @Test
    public void pauseAndResumeKeepTheSensorRequest() {
        sender.startTracking(trackingExtras(true, true));

        sender.pauseTracking(true);
        sender.pauseTracking(false);

        // Resuming goes through the no-argument startTracking(), which must not reset the request
        // and silently re-enable a sensor the user turned off, or drop one that was asked for.
        Assert.assertTrue(sender.isHeartRateRequested());
        Assert.assertTrue(sender.isOximetryRequested());
    }

    @Test
    public void aNewSessionStartsFromItsOwnRequest() {
        sender.startTracking(trackingExtras(true, true));
        sender.stopTracking();

        sender.startTracking(trackingExtras(false, false));

        Assert.assertFalse(sender.isHeartRateRequested());
        Assert.assertFalse(sender.isOximetryRequested());
    }
}
