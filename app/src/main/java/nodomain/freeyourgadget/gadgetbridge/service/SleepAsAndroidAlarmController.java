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
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.externalevents.sleepasandroid.SleepAsAndroidAction;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

/**
 * Decides when a wearable with no alarm of its own buzzes for Sleep as Android, and when it stops.
 * <p>
 * The alarm and the hint are separate {@link SleepAsAndroidVibration} streams so that a hint cannot
 * cancel the schedule of an alarm that is still ringing, but they drive the one find-device state
 * on the wearable, so the hint is held back while the alarm has it.
 */
public class SleepAsAndroidAlarmController {

    private static final Logger LOG = LoggerFactory.getLogger(SleepAsAndroidAlarmController.class);

    /** Sleep as Android's default when START_ALARM carries no DELAY. */
    static final int DEFAULT_ALARM_DELAY_MS = 60_000;

    private final SleepAsAndroidVibration alarm;
    private final SleepAsAndroidVibration hint;

    /**
     * A stream cancels by clearing its handler, so each gets one of its own on the shared looper.
     */
    public SleepAsAndroidAlarmController(final Looper looper, final SleepAsAndroidVibration.Toggle toggle) {
        this.alarm = new SleepAsAndroidVibration(new Handler(looper), toggle);
        this.hint = new SleepAsAndroidVibration(new Handler(looper), toggle);
    }

    /**
     * Apply whatever Sleep as Android has just sent.
     * <p>
     * Stopping is never gated on {@code alarmsEnabled}: an alarm that has already started has to be
     * stoppable whatever the preferences say now, and silencing a wearable is safe in a way that
     * starting it is not.
     *
     * @param maxDurationMs how long an alarm may ring if Sleep as Android never stops it, see
     *                      {@link #alarmMaxDurationMs(Prefs)}
     * @param alarmsEnabled whether the user allows Sleep as Android to ring this wearable
     */
    public void onAction(final String action,
                         @Nullable final Bundle extras,
                         final long maxDurationMs,
                         final boolean alarmsEnabled) {
        if (action == null) {
            return;
        }
        switch (action) {
            case SleepAsAndroidAction.START_ALARM:
                if (!alarmsEnabled) {
                    return;
                }
                // A hint left running would keep its own schedule, and the find-device off at the
                // end of each of its pulses would land in the middle of the alarm's.
                hint.stop();
                alarm.startAlarm(
                        extras != null ? extras.getInt("DELAY", DEFAULT_ALARM_DELAY_MS) : DEFAULT_ALARM_DELAY_MS,
                        maxDurationMs);
                break;
            case SleepAsAndroidAction.STOP_ALARM:
                stop();
                break;
            case SleepAsAndroidAction.STOP_TRACKING:
                // Sleep as Android sends STOP_ALARM before STOP_TRACKING when the user dismisses an
                // alarm, so this normally lands on a stream that is already stopped. It is the only
                // stop there is for an alarm that rang outside a session, which is not always
                // followed by a STOP_ALARM. START_TRACKING is not a stop: Sleep as Android repeats
                // it as its own watchdog, and a smart alarm rings inside a session.
                if (alarm.isAlarmRunning()) {
                    LOG.debug("Stopping the Sleep as Android alarm, the session has ended");
                }
                stop();
                break;
            default:
                break;
        }
    }

    /**
     * Vibrate the fixed number of times Sleep as Android asks for, to tell the user something
     * without ringing at them.
     */
    public void hint(final int repeat) {
        if (alarm.isAlarmRunning()) {
            // Both streams drive the same find-device state, and the alarm has to keep the wearable
            // buzzing until Sleep as Android stops it.
            LOG.debug("Ignoring Sleep as Android hint, the alarm is still ringing");
            return;
        }
        hint.hint(repeat);
    }

    /**
     * The user reached for Gadgetbridge's own find-device control. Without this the next burst
     * would override whatever they asked for a few seconds later, leaving disconnecting the
     * wearable as the only way to silence a runaway alarm.
     */
    public void onFindDevice() {
        if (alarm.isAlarmRunning()) {
            LOG.debug("Stopping the Sleep as Android alarm, the user took the find device control");
        }
        stop();
    }

    /** Cancel both streams and leave the wearable quiet. */
    public void cancel() {
        stop();
    }

    public boolean isAlarmRunning() {
        return alarm.isAlarmRunning();
    }

    /**
     * The user's limit on how long an alarm rings, in milliseconds. Imported or hand-edited
     * preferences can hold a value the settings screen would refuse, so it is clamped here too.
     */
    public static long alarmMaxDurationMs(final Prefs prefs) {
        final int minutes = prefs.getInt(
                GBPrefs.SLEEP_AS_ANDROID_ALARM_MAX_MINUTES,
                SleepAsAndroidVibration.DEFAULT_ALARM_MAX_MINUTES);
        return Math.max(SleepAsAndroidVibration.MIN_ALARM_MAX_MINUTES, minutes) * 60_000L;
    }

    private void stop() {
        alarm.stop();
        hint.stop();
    }
}
