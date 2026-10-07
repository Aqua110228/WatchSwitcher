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

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class SleepAsAndroidVibrationTest extends TestBase {

    private static final long CAP = SleepAsAndroidVibration.DEFAULT_ALARM_MAX_MINUTES * 60_000L;
    private static final long SHORT_CAP = SleepAsAndroidVibration.MIN_ALARM_MAX_MINUTES * 60_000L;

    /** Every find-device toggle, in order. */
    private List<Boolean> toggles;
    /** Elapsed time of every link wake, in order. */
    private List<Long> wakes;
    private SleepAsAndroidVibration vibration;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        toggles = new ArrayList<>();
        wakes = new ArrayList<>();
        vibration = new SleepAsAndroidVibration(new Handler(Looper.getMainLooper()), new SleepAsAndroidVibration.Toggle() {
            @Override
            public void set(final boolean on) {
                toggles.add(on);
            }

            @Override
            public void wake() {
                wakes.add(SystemClock.elapsedRealtime());
            }
        });
    }

    private void idle(final long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    /** Length of one complete burst of n pulses, counting the wake that precedes it. */
    private static long burstDuration(final int pulses) {
        return SleepAsAndroidVibration.WAKE_LEAD_MS
                + pulses * SleepAsAndroidVibration.PULSE_MS
                + (pulses - 1) * SleepAsAndroidVibration.GAP_MS;
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

    // --- hint -----------------------------------------------------------------------------

    @Test
    public void hintPulsesTheRequestedNumberOfTimes() {
        vibration.hint(3);
        idle(burstDuration(3));

        Assert.assertEquals(3, countOn());
        Assert.assertFalse("must not be left vibrating", toggles.get(toggles.size() - 1));
    }

    // --- alarm ----------------------------------------------------------------------------

    @Test
    public void alarmRepeatsWhileNothingStopsIt() {
        // The failure this guards: Sleep as Android sends START_ALARM once, and the band used to
        // buzz a single burst then fall silent while the phone kept ringing.
        vibration.startAlarm(0, CAP);
        idle(30_000);

        Assert.assertTrue("expected repeated bursts, saw " + countOn() + " pulses",
                countOn() > SleepAsAndroidVibration.ALARM_BURST_PULSES);
    }

    @Test
    public void alarmHonoursTheInitialDelay() {
        vibration.startAlarm(10_000, CAP);

        idle(9_000);
        Assert.assertEquals(0, countOn());

        idle(2_000);
        Assert.assertTrue(countOn() > 0);
    }

    @Test
    public void stopEndsTheAlarmPromptly() {
        vibration.startAlarm(0, CAP);
        idle(12_000);
        Assert.assertTrue(vibration.isAlarmRunning());

        vibration.stop();
        final int afterStop = toggles.size();

        idle(60_000);

        Assert.assertFalse(vibration.isAlarmRunning());
        Assert.assertEquals("nothing may fire after stop", afterStop, toggles.size());
        Assert.assertFalse("must leave the wearable quiet", toggles.get(toggles.size() - 1));
    }

    @Test
    public void stopFromAnotherThreadIsQueuedOnTheHandler() throws InterruptedException {
        // A connection cancels the alarm from the Bluetooth thread. Touching the schedule there
        // would let a burst that is midway through posting its next step outlive the cancel.
        vibration.startAlarm(0, CAP);
        idle(12_000);
        Assert.assertTrue(vibration.isAlarmRunning());

        final int beforeCancel = toggles.size();
        final Thread canceller = new Thread(vibration::stop);
        canceller.start();
        canceller.join();

        Assert.assertEquals("the cancel may not run on the caller's thread", beforeCancel, toggles.size());

        idle(1);
        Assert.assertFalse(vibration.isAlarmRunning());
        Assert.assertFalse("must leave the wearable quiet", toggles.get(toggles.size() - 1));

        final int afterStop = toggles.size();
        idle(60_000);
        Assert.assertEquals("nothing may fire after the cancel", afterStop, toggles.size());
    }

    @Test
    public void negativeDelayCancelsARunningAlarm() {
        vibration.startAlarm(0, CAP);
        idle(12_000);

        vibration.startAlarm(-1, CAP);
        final int afterCancel = toggles.size();
        idle(60_000);

        Assert.assertFalse(vibration.isAlarmRunning());
        Assert.assertEquals(afterCancel, toggles.size());
    }

    @Test
    public void alarmStopsAtTheSafetyCap() {
        // STOP_ALARM never arrives, so the loop must give up rather than vibrate until the
        // battery is flat.
        vibration.startAlarm(0, CAP);
        idle(CAP + 30_000);

        final int atCap = toggles.size();
        Assert.assertFalse(vibration.isAlarmRunning());

        idle(120_000);
        Assert.assertEquals(atCap, toggles.size());
    }

    @Test
    public void theCapIsMeasuredFromTheFirstBurstNotFromScheduling() {
        // Sleep as Android's default DELAY is a minute, which would otherwise eat all of the
        // shortest cap the user can set.
        final long cap = SHORT_CAP;
        vibration.startAlarm((int) cap, cap);

        idle(cap + 1);
        Assert.assertTrue("the alarm must still be running when it has only just begun",
                vibration.isAlarmRunning());

        idle(cap + 30_000);
        Assert.assertFalse(vibration.isAlarmRunning());
        Assert.assertFalse("must leave the wearable quiet", toggles.get(toggles.size() - 1));
    }

    @Test
    public void aShorterCapStopsTheAlarmSooner() {
        vibration.startAlarm(0, SHORT_CAP);

        idle(SHORT_CAP + 30_000);

        Assert.assertFalse(vibration.isAlarmRunning());
        Assert.assertFalse("must leave the wearable quiet", toggles.get(toggles.size() - 1));
    }

    // --- waking the link --------------------------------------------------------------------

    @Test
    public void theLinkIsWokenBeforeAHintPulses() {
        vibration.hint(3);

        Assert.assertEquals(1, wakes.size());
        Assert.assertTrue("nothing may be toggled until the link has had time to wake",
                toggles.isEmpty());

        idle(burstDuration(3));
        Assert.assertEquals(3, countOn());
    }

    @Test
    public void everyAlarmBurstWakesTheLinkFirst() {
        // The link goes idle between bursts, so each one pays the wake-up latency again.
        vibration.startAlarm(0, CAP);
        idle(30_000);

        Assert.assertTrue("expected one wake per burst, saw " + wakes.size() + " for "
                        + countOn() + " pulses",
                wakes.size() >= countOn() / SleepAsAndroidVibration.ALARM_BURST_PULSES);
    }

    @Test
    public void theLeadPulseWaitsOutTheWakeLead() {
        vibration.startAlarm(0, CAP);

        idle(SleepAsAndroidVibration.WAKE_LEAD_MS - 1);
        Assert.assertEquals("the leading pulse must not go out before the link is awake",
                0, countOn());

        idle(2);
        Assert.assertEquals(1, countOn());
    }

    @Test
    public void aCancelledAlarmNeverWakesTheLinkAgain() {
        vibration.startAlarm(0, CAP);
        idle(12_000);
        vibration.stop();
        final int afterStop = wakes.size();

        idle(60_000);

        Assert.assertEquals(afterStop, wakes.size());
    }

    @Test
    public void aStopWithNothingRunningSaysNothing() {
        // Hints and alarms are stopped together and share the one find-device state, so a stop
        // that changes nothing must not put a second command on the wire.
        vibration.stop();
        idle(1);

        Assert.assertTrue(toggles.isEmpty());
    }

    @Test
    public void aSecondStopSaysNothing() {
        vibration.startAlarm(0, CAP);
        idle(12_000);

        vibration.stop();
        idle(1);
        final int afterFirstStop = toggles.size();

        vibration.stop();
        idle(1);

        Assert.assertEquals(afterFirstStop, toggles.size());
    }

    @Test
    public void restartingTheAlarmDoesNotStackLoops() {
        vibration.startAlarm(0, CAP);
        idle(20_000);
        final int firstRun = countOn();

        vibration.startAlarm(0, CAP);
        idle(20_000);

        // A second loop running in parallel would roughly double the pulse rate.
        Assert.assertTrue("pulse rate suggests two loops: " + firstRun + " then " + (countOn() - firstRun),
                countOn() - firstRun <= firstRun + SleepAsAndroidVibration.ALARM_BURST_PULSES);
    }
}
