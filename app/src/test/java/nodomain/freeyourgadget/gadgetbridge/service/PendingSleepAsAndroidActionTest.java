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

import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;

import java.time.Duration;

import nodomain.freeyourgadget.gadgetbridge.externalevents.sleepasandroid.SleepAsAndroidAction;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class PendingSleepAsAndroidActionTest extends TestBase {

    private static final String ADDRESS = "00:11:22:33:44:55";
    private static final String OTHER_ADDRESS = "AA:BB:CC:DD:EE:FF";

    private PendingSleepAsAndroidAction pending;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        pending = new PendingSleepAsAndroidAction();
    }

    private static Intent actionIntent(final String sleepAsAndroidAction) {
        return new Intent(DeviceService.ACTION_SLEEP_AS_ANDROID)
                .putExtra(DeviceService.EXTRA_SLEEP_AS_ANDROID_ACTION, sleepAsAndroidAction);
    }

    /** Robolectric advances elapsedRealtime() with the main looper. */
    private void advance(final long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    @Test
    public void trackingIsHeldAcrossTheConnect() {
        Assert.assertTrue(pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS));
        Assert.assertTrue(pending.isPending());

        final Intent replayed = pending.take(ADDRESS);

        Assert.assertNotNull(replayed);
        Assert.assertEquals(SleepAsAndroidAction.START_TRACKING,
                replayed.getStringExtra(DeviceService.EXTRA_SLEEP_AS_ANDROID_ACTION));
    }

    @Test
    public void connectionCheckIsNotHeld() {
        // Sleep as Android repeats CHECK_CONNECTED every few seconds, so the next one succeeds
        // naturally once the link is up. Holding it would answer a question already re-asked.
        Assert.assertFalse(pending.store(actionIntent(SleepAsAndroidAction.CHECK_CONNECTED), ADDRESS));
        Assert.assertFalse(pending.isPending());
        Assert.assertNull(pending.take(ADDRESS));
    }

    @Test
    public void connectionCheckWakesTheWearable() {
        Assert.assertTrue(pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));
    }

    @Test
    public void connectionCheckWakesOncePerWindow() {
        Assert.assertTrue(pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));

        advance(PendingSleepAsAndroidAction.TIMEOUT_MS - 5_000);
        Assert.assertFalse("an unreachable wearable must not be retried every five seconds",
                pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));

        advance(10_000);
        Assert.assertTrue(pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));
    }

    @Test
    public void aLiveLinkRearmsTheWake() {
        Assert.assertTrue(pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));

        pending.linkUp();

        Assert.assertTrue("a link lost right after a connect must be retried at once",
                pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));
    }

    @Test
    public void endingTheSessionDoesNotRearmTheWake() {
        Assert.assertTrue(pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));

        pending.cancels(SleepAsAndroidAction.STOP_TRACKING);
        pending.clear();

        Assert.assertFalse(pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));
    }

    @Test
    public void onlyTheConnectionCheckWakes() {
        Assert.assertFalse(pending.wakes(SleepAsAndroidAction.SET_BATCH_SIZE));
        Assert.assertFalse(pending.wakes(SleepAsAndroidAction.STOP_TRACKING));
        Assert.assertFalse(pending.wakes(null));
        Assert.assertTrue("a refused action must not use up the window",
                pending.wakes(SleepAsAndroidAction.CHECK_CONNECTED));
    }

    @Test
    public void theAlarmIsHeldAcrossTheConnect() {
        Assert.assertTrue(pending.store(actionIntent(SleepAsAndroidAction.START_ALARM), ADDRESS));

        final Intent replayed = pending.take(ADDRESS);

        Assert.assertNotNull(replayed);
        Assert.assertEquals(SleepAsAndroidAction.START_ALARM,
                replayed.getStringExtra(DeviceService.EXTRA_SLEEP_AS_ANDROID_ACTION));
    }

    @Test
    public void theAlarmTakesOverFromTracking() {
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);

        Assert.assertTrue(pending.store(actionIntent(SleepAsAndroidAction.START_ALARM), ADDRESS));

        Assert.assertEquals(SleepAsAndroidAction.START_ALARM,
                pending.take(ADDRESS).getStringExtra(DeviceService.EXTRA_SLEEP_AS_ANDROID_ACTION));
    }

    @Test
    public void trackingDoesNotTakeOverFromTheAlarm() {
        pending.store(actionIntent(SleepAsAndroidAction.START_ALARM), ADDRESS);

        Assert.assertFalse(pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS));

        Assert.assertEquals(SleepAsAndroidAction.START_ALARM,
                pending.take(ADDRESS).getStringExtra(DeviceService.EXTRA_SLEEP_AS_ANDROID_ACTION));
    }

    @Test
    public void stoppingTheAlarmDropsTheHeldAlarm() {
        pending.store(actionIntent(SleepAsAndroidAction.START_ALARM), ADDRESS);

        Assert.assertTrue(pending.cancels(SleepAsAndroidAction.STOP_ALARM));

        Assert.assertFalse(pending.isPending());
        Assert.assertNull(pending.take(ADDRESS));
    }

    @Test
    public void stoppingTheAlarmLeavesTrackingAlone() {
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);

        Assert.assertTrue(pending.cancels(SleepAsAndroidAction.STOP_ALARM));

        Assert.assertTrue(pending.isPending());
    }

    @Test
    public void onlyTheStopActionsCancel() {
        Assert.assertFalse(pending.cancels(SleepAsAndroidAction.START_ALARM));
        Assert.assertFalse(pending.cancels(SleepAsAndroidAction.START_TRACKING));
        Assert.assertFalse(pending.cancels(SleepAsAndroidAction.CHECK_CONNECTED));
        Assert.assertFalse(pending.cancels(null));
    }

    @Test
    public void endingTheSessionDropsAHeldAlarm() {
        // An alarm that rang outside a session gets no STOP_ALARM of its own, so a connect
        // completing after the user has finished with it would buzz the wearable for nothing.
        pending.store(actionIntent(SleepAsAndroidAction.START_ALARM), ADDRESS);

        Assert.assertTrue(pending.cancels(SleepAsAndroidAction.STOP_TRACKING));

        Assert.assertFalse(pending.isPending());
        Assert.assertNull(pending.take(ADDRESS));
    }

    @Test
    public void endingTheSessionDropsHeldTracking() {
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);

        Assert.assertTrue(pending.cancels(SleepAsAndroidAction.STOP_TRACKING));

        Assert.assertFalse(pending.isPending());
    }

    @Test
    public void endingTheSessionWithNothingHeldIsHarmless() {
        Assert.assertTrue(pending.cancels(SleepAsAndroidAction.STOP_TRACKING));

        Assert.assertFalse(pending.isPending());
    }

    @Test
    public void anExpiredAlarmDoesNotRingLate() {
        pending.store(actionIntent(SleepAsAndroidAction.START_ALARM), ADDRESS);

        advance(PendingSleepAsAndroidAction.TIMEOUT_MS + 1);

        Assert.assertNull(pending.take(ADDRESS));
    }

    @Test
    public void replayHappensOnlyOnce() {
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);

        Assert.assertNotNull(pending.take(ADDRESS));
        Assert.assertNull("a second connect must not start tracking again", pending.take(ADDRESS));
        Assert.assertFalse(pending.isPending());
    }

    @Test
    public void anotherDeviceDoesNotConsumeIt() {
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);

        Assert.assertNull(pending.take(OTHER_ADDRESS));
        Assert.assertTrue("it must still be waiting for its own device", pending.isPending());
        Assert.assertNotNull(pending.take(ADDRESS));
    }

    @Test
    public void expiredRequestIsDropped() {
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);

        advance(PendingSleepAsAndroidAction.TIMEOUT_MS + 1_000);

        Assert.assertNull("a stale request must not fire into a later session", pending.take(ADDRESS));
        Assert.assertFalse(pending.isPending());
    }

    @Test
    public void requestSurvivesUpToTheTimeout() {
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);

        advance(PendingSleepAsAndroidAction.TIMEOUT_MS - 5_000);

        Assert.assertNotNull(pending.take(ADDRESS));
    }

    @Test
    public void holdStopsBlockingOnceItExpires() {
        // The connect is skipped while a request is held, so a hold that outlived its window must
        // not keep an unreachable wearable from being tried again.
        pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS);
        Assert.assertTrue(pending.isPending());

        advance(PendingSleepAsAndroidAction.TIMEOUT_MS + 1_000);

        Assert.assertFalse(pending.isPending());
        Assert.assertTrue(pending.store(actionIntent(SleepAsAndroidAction.START_TRACKING), ADDRESS));
    }

    @Test
    public void timeoutFitsInsideTheWindowSleepAsAndroidWaits() {
        // Sleep as Android waits about 120 s before falling back to phone sensors, so the held
        // request has to expire before that rather than after.
        Assert.assertTrue(PendingSleepAsAndroidAction.TIMEOUT_MS < 120_000L);
    }
}
