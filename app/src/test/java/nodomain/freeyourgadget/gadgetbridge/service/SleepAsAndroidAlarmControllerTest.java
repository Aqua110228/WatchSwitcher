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
import android.os.Looper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.externalevents.sleepasandroid.SleepAsAndroidAction;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

/**
 * The policy that decides when a wearable with no alarm of its own buzzes for Sleep as Android.
 * <p>
 * Every case ends by asserting the wearable was left quiet, because the failure that matters here
 * is a band still buzzing after the phone has finished with the alarm.
 */
public class SleepAsAndroidAlarmControllerTest extends TestBase {

    private static final boolean ALARMS_ON = true;
    private static final boolean ALARMS_OFF = false;

    private static final long CAP = SleepAsAndroidVibration.DEFAULT_ALARM_MAX_MINUTES * 60_000L;
    /** Comfortably longer than one burst plus the gap to the next one. */
    private static final long SEVERAL_BURSTS_MS = 30_000L;

    /** Every find-device toggle, in order. */
    private List<Boolean> toggles;
    private int wakes;
    private SleepAsAndroidAlarmController controller;
    /** Stands in for a link that cannot carry the command right now. */
    private boolean toggleSuppressed;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        toggles = new ArrayList<>();
        wakes = 0;
        toggleSuppressed = false;
        controller = new SleepAsAndroidAlarmController(
                Looper.getMainLooper(),
                new SleepAsAndroidVibration.Toggle() {
                    @Override
                    public void set(final boolean on) {
                        if (!toggleSuppressed) {
                            toggles.add(on);
                        }
                    }

                    @Override
                    public void wake() {
                        wakes++;
                    }
                });
    }

    // --- driving ----------------------------------------------------------------------------

    private void idle(final long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private void send(final String action, final Bundle extras, final long maxDurationMs, final boolean alarmsEnabled) {
        controller.onAction(action, extras, maxDurationMs, alarmsEnabled);
    }

    private void send(final String action) {
        send(action, null, CAP, ALARMS_ON);
    }

    private void startAlarm(final int delayMs, final long maxDurationMs, final boolean alarmsEnabled) {
        final Bundle extras = new Bundle();
        extras.putInt("DELAY", delayMs);
        send(SleepAsAndroidAction.START_ALARM, extras, maxDurationMs, alarmsEnabled);
    }

    private void startAlarm(final int delayMs, final boolean alarmsEnabled) {
        startAlarm(delayMs, CAP, alarmsEnabled);
    }

    private void startAlarm() {
        startAlarm(0, ALARMS_ON);
    }

    private void hint(final int repeat) {
        controller.hint(repeat);
    }

    private int countOn() {
        int n = 0;
        for (final boolean on : toggles) {
            if (on) {
                n++;
            }
        }
        return n;
    }

    /** The assertion every case shares: nothing is still buzzing once the dust has settled. */
    private void assertLeftQuiet() {
        final int settled = toggles.size();
        idle(CAP * 2);

        Assert.assertFalse("the alarm is still running", controller.isAlarmRunning());
        Assert.assertEquals("something fired after everything should have stopped",
                settled, toggles.size());
        if (!toggles.isEmpty()) {
            Assert.assertFalse("the wearable was left vibrating", toggles.get(toggles.size() - 1));
        }
    }

    // --- alarm lifecycle --------------------------------------------------------------------

    @Test
    public void stopAlarmStopsTheAlarm() {
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        Assert.assertTrue(controller.isAlarmRunning());

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);

        assertLeftQuiet();
    }

    @Test
    public void stopTrackingStopsTheAlarm() {
        // The reported failure: Sleep as Android rang an alarm that no session claimed, the user
        // finished with it in the app, and no STOP_ALARM ever came. The band kept buzzing until the
        // wearable was disconnected by hand.
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        Assert.assertTrue(controller.isAlarmRunning());

        send(SleepAsAndroidAction.STOP_TRACKING);
        idle(1);

        assertLeftQuiet();
    }

    @Test
    public void startTrackingDoesNotStopTheAlarm() {
        // Sleep as Android repeats START_TRACKING as its own watchdog, and a smart alarm rings
        // inside a session, so the wearable has to keep buzzing through one.
        startAlarm();
        idle(SEVERAL_BURSTS_MS);

        send(SleepAsAndroidAction.START_TRACKING);
        final int atWatchdog = countOn();
        idle(SEVERAL_BURSTS_MS);

        Assert.assertTrue(controller.isAlarmRunning());
        Assert.assertTrue("the alarm fell silent on a watchdog START_TRACKING",
                countOn() > atWatchdog);

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);
        assertLeftQuiet();
    }

    @Test
    public void anUnstoppedAlarmGivesUpAtTheCap() {
        startAlarm();
        idle(CAP + SEVERAL_BURSTS_MS);

        assertLeftQuiet();
    }

    @Test
    public void aNegativeDelayIsSleepAsAndroidsCancel() {
        startAlarm();
        idle(SEVERAL_BURSTS_MS);

        startAlarm(-1, ALARMS_ON);
        idle(1);

        assertLeftQuiet();
    }

    @Test
    public void aDelayIsHonouredBeforeTheFirstBurst() {
        startAlarm(10_000, ALARMS_ON);

        idle(9_000);
        Assert.assertEquals(0, countOn());

        idle(SleepAsAndroidVibration.WAKE_LEAD_MS + 2_000);
        Assert.assertTrue(countOn() > 0);

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);
        assertLeftQuiet();
    }

    @Test
    public void aSecondStartAlarmDoesNotStackLoops() {
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        final int firstRun = countOn();

        startAlarm();
        idle(SEVERAL_BURSTS_MS);

        Assert.assertTrue("pulse rate suggests two loops running at once",
                countOn() - firstRun <= firstRun + SleepAsAndroidVibration.ALARM_BURST_PULSES);

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);
        assertLeftQuiet();
    }

    @Test
    public void aStopWithNothingRunningSaysNothing() {
        send(SleepAsAndroidAction.STOP_ALARM);
        send(SleepAsAndroidAction.STOP_TRACKING);
        idle(1);

        Assert.assertTrue("a redundant stop must not reach the wire", toggles.isEmpty());
        assertLeftQuiet();
    }

    @Test
    public void unhandledActionsChangeNothing() {
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        final int running = countOn();

        send(SleepAsAndroidAction.CHECK_CONNECTED);
        send(SleepAsAndroidAction.UPDATE_ALARM);
        send(SleepAsAndroidAction.SET_BATCH_SIZE);
        send(SleepAsAndroidAction.SHOW_NOTIFICATION);
        // Hints come in through hint(), not through an action.
        send(SleepAsAndroidAction.HINT);
        send("com.urbandroid.sleep.watch.SOMETHING_NEW");
        controller.onAction(null, null, CAP, ALARMS_ON);
        idle(1);

        Assert.assertTrue(controller.isAlarmRunning());
        Assert.assertEquals(running, countOn());

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);
        assertLeftQuiet();
    }

    @Test
    public void theNightThatWasReported() {
        // 2026-09-14, replayed from the logs. The smart alarm rang inside a session and was
        // snoozed, the snoozed alarm rang half an hour later with no session behind it and never
        // got a STOP_ALARM, and the session that closed a minute later had to be what stopped it.
        startAlarm();
        idle(100_000);
        send(SleepAsAndroidAction.STOP_ALARM);
        send(SleepAsAndroidAction.STOP_TRACKING);
        idle(1);
        Assert.assertFalse("the dismissed alarm must not survive the session", controller.isAlarmRunning());

        final int afterSnooze = toggles.size();
        idle(Duration.ofMinutes(29).toMillis());
        Assert.assertEquals("nothing may buzz between the snooze and the next alarm",
                afterSnooze, toggles.size());

        startAlarm();
        idle(60_000);
        for (int i = 0; i < 5; i++) {
            send(SleepAsAndroidAction.CHECK_CONNECTED);
        }
        hint(1);
        send(SleepAsAndroidAction.START_TRACKING);
        send(SleepAsAndroidAction.UPDATE_ALARM);
        idle(3_000);
        Assert.assertTrue("the alarm has not been stopped yet", controller.isAlarmRunning());

        send(SleepAsAndroidAction.STOP_TRACKING);
        idle(1);

        assertLeftQuiet();
    }

    // --- cap --------------------------------------------------------------------------------

    @Test
    public void anAlarmRingsUntilTheCapItWasGiven() {
        final long cap = Duration.ofMinutes(1).toMillis();
        startAlarm(0, cap, ALARMS_ON);

        idle(cap - SEVERAL_BURSTS_MS);
        Assert.assertTrue("the alarm gave up before its cap", controller.isAlarmRunning());

        idle(2 * SEVERAL_BURSTS_MS);
        Assert.assertFalse("the alarm outlived its cap", controller.isAlarmRunning());
        assertLeftQuiet();
    }

    @Test
    public void theDefaultCapOutlastsAnUnattendedAlarm() {
        // 2026-09-28: two alarms rang outside a session under a two minute cap, and the band went
        // quiet while the phone rang on for more than two minutes after that.
        startAlarm();

        idle(Duration.ofMinutes(4).toMillis());
        Assert.assertTrue(controller.isAlarmRunning());

        idle(Duration.ofMinutes(1).toMillis() + SEVERAL_BURSTS_MS);
        assertLeftQuiet();
    }

    @Test
    public void theCapComesFromThePreference() {
        final Prefs prefs = GBApplication.getPrefs();
        try {
            Assert.assertEquals(Duration.ofMinutes(5).toMillis(),
                    SleepAsAndroidAlarmController.alarmMaxDurationMs(prefs));

            prefs.getPreferences().edit().putString(GBPrefs.SLEEP_AS_ANDROID_ALARM_MAX_MINUTES, "3").commit();
            Assert.assertEquals(Duration.ofMinutes(3).toMillis(),
                    SleepAsAndroidAlarmController.alarmMaxDurationMs(prefs));

            prefs.getPreferences().edit().putString(GBPrefs.SLEEP_AS_ANDROID_ALARM_MAX_MINUTES, "0").commit();
            Assert.assertEquals(Duration.ofMinutes(1).toMillis(),
                    SleepAsAndroidAlarmController.alarmMaxDurationMs(prefs));

            prefs.getPreferences().edit().putString(GBPrefs.SLEEP_AS_ANDROID_ALARM_MAX_MINUTES, "-2").commit();
            Assert.assertEquals(Duration.ofMinutes(1).toMillis(),
                    SleepAsAndroidAlarmController.alarmMaxDurationMs(prefs));

            prefs.getPreferences().edit().putString(GBPrefs.SLEEP_AS_ANDROID_ALARM_MAX_MINUTES, "").commit();
            Assert.assertEquals(Duration.ofMinutes(5).toMillis(),
                    SleepAsAndroidAlarmController.alarmMaxDurationMs(prefs));
        } finally {
            prefs.getPreferences().edit().remove(GBPrefs.SLEEP_AS_ANDROID_ALARM_MAX_MINUTES).commit();
        }
    }

    // --- hint against alarm -----------------------------------------------------------------

    @Test
    public void aHintIsIgnoredWhileTheAlarmRings() {
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        final int beforeHint = countOn();

        hint(3);
        idle(SEVERAL_BURSTS_MS);

        Assert.assertTrue("the alarm must survive a hint", controller.isAlarmRunning());
        Assert.assertTrue("the hint interrupted the alarm", countOn() > beforeHint);

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);
        assertLeftQuiet();
    }

    @Test
    public void aHintOnItsOwnPulsesTheRequestedNumberOfTimes() {
        hint(3);
        idle(SEVERAL_BURSTS_MS);

        Assert.assertEquals(3, countOn());
        assertLeftQuiet();
    }

    @Test
    public void stoppingTheAlarmAlsoStopsARunningHint() {
        // Both streams drive the one find-device state, so a hint left running would keep the
        // wearable buzzing after the alarm was stopped.
        hint(20);
        idle(SleepAsAndroidVibration.WAKE_LEAD_MS + SleepAsAndroidVibration.PULSE_MS);

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);

        assertLeftQuiet();
    }

    @Test
    public void anAlarmStartingMidHintTakesOverTheWearable() {
        // The two streams keep separate schedules and separate ideas of the find-device state, so a
        // hint left running would put its own off in the middle of one of the alarm's pulses.
        hint(20);
        idle(SleepAsAndroidVibration.WAKE_LEAD_MS + SleepAsAndroidVibration.PULSE_MS);

        startAlarm();
        idle(1);
        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);

        // The hint had eighteen pulses left to give.
        assertLeftQuiet();
    }

    // --- feature gating ---------------------------------------------------------------------

    @Test
    public void alarmsTurnedOffKeepTheWearableQuiet() {
        startAlarm(0, ALARMS_OFF);
        idle(SEVERAL_BURSTS_MS);

        Assert.assertFalse(controller.isAlarmRunning());
        Assert.assertTrue(toggles.isEmpty());
        assertLeftQuiet();
    }

    @Test
    public void stoppingIsNeverGatedOnTheAlarmFeature() {
        // validateAction gates STOP_TRACKING on the sensor features and START_ALARM on the alarm
        // one, so a preference changed mid-alarm must not leave the alarm unstoppable.
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        Assert.assertTrue(controller.isAlarmRunning());

        send(SleepAsAndroidAction.STOP_TRACKING, null, CAP, ALARMS_OFF);
        idle(1);

        assertLeftQuiet();
    }

    // --- connection state -------------------------------------------------------------------

    @Test
    public void cancelStopsEverything() {
        // What dispose and a new connection both run.
        startAlarm();
        idle(SEVERAL_BURSTS_MS);

        controller.cancel();
        idle(1);

        assertLeftQuiet();
    }

    @Test
    public void anAlarmOnADeadLinkStillGivesUpOnItsOwn() {
        // setFindWatchIfInitialized drops the toggle while the wearable is not initialized. The
        // schedule has to keep running so the cap can end it, rather than wedging until a reconnect.
        toggleSuppressed = true;
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        Assert.assertTrue(controller.isAlarmRunning());
        Assert.assertTrue("nothing may reach a link that cannot carry it", toggles.isEmpty());

        idle(CAP + SEVERAL_BURSTS_MS);

        Assert.assertFalse(controller.isAlarmRunning());
    }

    @Test
    public void everyBurstWakesTheLinkFirst() {
        // An idle link takes around 700ms to carry its first command, long enough to swallow the
        // leading pulse of a burst that did not wake it.
        startAlarm();
        idle(SEVERAL_BURSTS_MS);

        Assert.assertTrue("expected one wake per burst, saw " + wakes + " for " + countOn() + " pulses",
                wakes >= countOn() / SleepAsAndroidVibration.ALARM_BURST_PULSES);

        send(SleepAsAndroidAction.STOP_ALARM);
        idle(1);
        assertLeftQuiet();
    }

    // --- escape hatch -----------------------------------------------------------------------

    @Test
    public void theFindDeviceControlStopsARunawayAlarm() {
        // Without this the next burst overrides whatever the user asked for a few seconds later,
        // leaving disconnecting the wearable as the only way out.
        startAlarm();
        idle(SEVERAL_BURSTS_MS);
        Assert.assertTrue(controller.isAlarmRunning());

        controller.onFindDevice();
        idle(1);

        assertLeftQuiet();
    }

    @Test
    public void theFindDeviceControlSaysNothingWhenNothingIsRunning() {
        controller.onFindDevice();
        idle(1);

        Assert.assertTrue(toggles.isEmpty());
        assertLeftQuiet();
    }
}
