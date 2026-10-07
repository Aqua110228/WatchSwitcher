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
package nodomain.freeyourgadget.gadgetbridge.externalevents.sleepasandroid;

import android.content.Intent;
import android.os.Bundle;

import org.junit.Assert;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The receiver is registered as exported, so these extras arrive from another application and are
 * the boundary between the Sleep as Android wire contract and Gadgetbridge.
 */
public class SleepAsAndroidReceiverTest extends TestBase {

    private static Intent intentWith(final Bundle extras) {
        final Intent intent = new Intent(SleepAsAndroidAction.START_TRACKING);
        if (extras != null) {
            intent.putExtras(extras);
        }
        return intent;
    }

    @Test
    public void noExtras_returnsNull() {
        Assert.assertNull(SleepAsAndroidReceiver.sanitizeExtras(new Intent(SleepAsAndroidAction.STOP_TRACKING)));
    }

    @Test
    public void knownKeysSurvive() {
        final Bundle in = new Bundle();
        in.putLong("TIMESTAMP", 1234567890123L);
        in.putBoolean("SUSPENDED", true);
        in.putLong("SIZE", 7L);
        in.putInt("REPEAT", 4);
        in.putInt("DELAY", 30000);

        final Bundle out = SleepAsAndroidReceiver.sanitizeExtras(intentWith(in));

        Assert.assertNotNull(out);
        Assert.assertEquals(1234567890123L, out.getLong("TIMESTAMP"));
        Assert.assertTrue(out.getBoolean("SUSPENDED"));
        Assert.assertEquals(7L, out.getLong("SIZE"));
        Assert.assertEquals(4, out.getInt("REPEAT"));
        Assert.assertEquals(30000, out.getInt("DELAY"));
    }

    @Test
    public void unknownKeysAreDropped() {
        final Bundle in = new Bundle();
        in.putLong("SIZE", 12L);
        in.putString("SOMETHING_ELSE", "dropped");
        in.putSerializable("PAYLOAD", new java.util.HashMap<String, String>());

        final Bundle out = SleepAsAndroidReceiver.sanitizeExtras(intentWith(in));

        Assert.assertNotNull(out);
        Assert.assertFalse(out.containsKey("SOMETHING_ELSE"));
        Assert.assertFalse(out.containsKey("PAYLOAD"));
        Assert.assertEquals(1, out.keySet().size());
    }

    @Test
    public void numericExtrasAreReadByValueNotByWidth() {
        // Sleep as Android puts SIZE in as an int in at least some builds, and DELAY as a long.
        // Bundle.getLong does not widen an Integer, so reading either naively would silently
        // replace it with the default.
        final Bundle asInt = new Bundle();
        asInt.putInt("SIZE", 1);
        Assert.assertEquals(1L, SleepAsAndroidReceiver.sanitizeExtras(intentWith(asInt)).getLong("SIZE"));

        final Bundle asLong = new Bundle();
        asLong.putLong("SIZE", 7L);
        asLong.putLong("DELAY", -1L);
        final Bundle out = SleepAsAndroidReceiver.sanitizeExtras(intentWith(asLong));
        Assert.assertEquals(7L, out.getLong("SIZE"));
        Assert.assertEquals(-1, out.getInt("DELAY"));

        final Bundle nonNumeric = new Bundle();
        nonNumeric.putString("SIZE", "not a number");
        Assert.assertEquals(12L, SleepAsAndroidReceiver.sanitizeExtras(intentWith(nonNumeric)).getLong("SIZE"));
    }

    @Test
    public void sensorRequestIsCarriedByPresence() {
        final Bundle in = new Bundle();
        in.putBoolean("DO_HR_MONITORING", true);

        final Bundle out = SleepAsAndroidReceiver.sanitizeExtras(intentWith(in));

        Assert.assertNotNull(out);
        Assert.assertTrue(out.containsKey("DO_HR_MONITORING"));
        Assert.assertFalse(out.containsKey("DO_OXIMETER_MONITORING"));
    }

    @Test
    public void intentFilterCoversEveryHandledAction() {
        final SleepAsAndroidReceiver receiver = new SleepAsAndroidReceiver();
        final android.content.IntentFilter filter = receiver.getIntentFilter();

        for (final String action : new String[]{
                SleepAsAndroidAction.START_TRACKING,
                SleepAsAndroidAction.STOP_TRACKING,
                SleepAsAndroidAction.SET_PAUSE,
                SleepAsAndroidAction.SET_SUSPENDED,
                SleepAsAndroidAction.SET_BATCH_SIZE,
                SleepAsAndroidAction.START_ALARM,
                SleepAsAndroidAction.STOP_ALARM,
                SleepAsAndroidAction.UPDATE_ALARM,
                SleepAsAndroidAction.HINT,
                SleepAsAndroidAction.CHECK_CONNECTED,
        }) {
            Assert.assertTrue("filter is missing " + action, filter.hasAction(action));
        }
    }
}
