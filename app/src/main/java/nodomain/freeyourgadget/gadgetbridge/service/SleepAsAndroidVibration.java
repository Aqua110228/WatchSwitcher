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

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the vibration Sleep as Android asks for on devices that have no alarm of their own to
 * ring, by toggling find-device on and off.
 * <p>
 * START_ALARM is level-based: Sleep as Android sends it once and expects the wearable to keep
 * alarming until STOP_ALARM, which can be minutes later while the user has not yet dismissed the
 * alarm or solved its captcha. A single burst therefore leaves the wearable silent for most of the
 * time the phone is ringing.
 * <p>
 * One instance drives one stream, so hints and alarms each get their own and neither cancels the
 * other's schedule. They still share the one find-device state on the wearable, so a caller that
 * starts a hint while an alarm is ringing has to hold it back itself.
 * <p>
 * A schedule is started from the handler's own thread. Only {@link #stop()} may be called from
 * elsewhere.
 */
public class SleepAsAndroidVibration {

    private static final Logger LOG = LoggerFactory.getLogger(SleepAsAndroidVibration.class);

    public interface Toggle {
        void set(boolean on);

        /**
         * Put something on the wire so the link is awake by the time the next pulse is set.
         * Whatever is sent has to be free of side effects; only the fact that it was sent matters.
         */
        default void wake() {
        }
    }

    /**
     * How long before a burst the link is woken.
     * <p>
     * A Bluetooth link that has been idle takes around 700ms to carry its first command, against
     * roughly 100ms once it is awake. A pulse shorter than that gets its off queued behind its own
     * on, and the wearable runs the two back to back for a few milliseconds instead of buzzing:
     * measured on a Mi Band 10, every burst outside a tracking session lost its leading pulse that
     * way while none inside one did. This has to stay comfortably above the idle latency.
     */
    static final long WAKE_LEAD_MS = 900L;
    static final long PULSE_MS = 500L;
    static final long GAP_MS = 300L;
    static final int ALARM_BURST_PULSES = 3;
    static final long ALARM_BURST_INTERVAL_MS = 5_000L;
    /**
     * How long an alarm rings when the user has not set a limit. Nothing stops the alarm if
     * STOP_ALARM never arrives, which happens to an alarm that rang outside a session, so it cannot
     * run unbounded. It has to outlast a real alarm by a wide margin, though: the wearable falling
     * silent while the phone is still ringing is the louder failure of the two.
     */
    public static final int DEFAULT_ALARM_MAX_MINUTES = 5;
    public static final int MIN_ALARM_MAX_MINUTES = 1;

    private final Handler handler;
    private final Toggle toggle;

    private boolean alarmRunning = false;
    private boolean toggledOn = false;
    private long maxDuration = DEFAULT_ALARM_MAX_MINUTES * 60_000L;
    private long alarmDeadline = 0;

    public SleepAsAndroidVibration(final Handler handler, final Toggle toggle) {
        this.handler = handler;
        this.toggle = toggle;
    }

    /**
     * Vibrate a fixed number of times, once.
     */
    public void hint(final int pulses) {
        if (pulses <= 0) {
            return;
        }
        stopNow();
        wakeThenBurst(pulses, null);
    }

    /**
     * Start alarming after {@code delayMs}, repeating until {@link #stop()} or {@code maxDurationMs}
     * of alarming has passed. A delay of -1 is Sleep as Android's cancel convention.
     * <p>
     * The cap measures alarming rather than waiting, so a delay does not eat into it.
     */
    public void startAlarm(final int delayMs, final long maxDurationMs) {
        stopNow();
        if (delayMs == -1) {
            return;
        }

        alarmRunning = true;
        maxDuration = maxDurationMs;
        alarmDeadline = 0;
        handler.postDelayed(this::alarmTick, Math.max(0, delayMs));
    }

    /**
     * Cancel whatever is running and leave the wearable quiet.
     * <p>
     * A caller on another thread only gets the cancel queued: everything a schedule touches has to
     * run on the handler, or a burst caught midway through posting its next step survives the
     * cancel and keeps the wearable buzzing.
     */
    public void stop() {
        if (Looper.myLooper() == handler.getLooper()) {
            stopNow();
        } else {
            handler.post(this::stopNow);
        }
    }

    private void stopNow() {
        alarmRunning = false;
        handler.removeCallbacksAndMessages(null);
        set(false);
    }

    /**
     * Two streams share the one find-device state, and both are stopped together, so the state is
     * tracked to keep the second stop off the wire.
     */
    private void set(final boolean on) {
        if (toggledOn == on) {
            return;
        }
        toggledOn = on;
        toggle.set(on);
    }

    public boolean isAlarmRunning() {
        return alarmRunning;
    }

    private void alarmTick() {
        if (!alarmRunning) {
            return;
        }
        if (alarmDeadline == 0) {
            alarmDeadline = SystemClock.elapsedRealtime() + maxDuration;
        } else if (SystemClock.elapsedRealtime() > alarmDeadline) {
            LOG.info("Sleep as Android alarm reached its {} ms cap, stopping", maxDuration);
            stopNow();
            return;
        }
        wakeThenBurst(ALARM_BURST_PULSES, () -> handler.postDelayed(this::alarmTick, ALARM_BURST_INTERVAL_MS));
    }

    private void wakeThenBurst(final int pulses, @Nullable final Runnable onComplete) {
        toggle.wake();
        handler.postDelayed(() -> burst(pulses, 0, onComplete), WAKE_LEAD_MS);
    }

    private void burst(final int pulses, final int index, @Nullable final Runnable onComplete) {
        set(true);
        handler.postDelayed(() -> {
            set(false);
            if (index + 1 >= pulses) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            handler.postDelayed(() -> burst(pulses, index + 1, onComplete), GAP_MS);
        }, PULSE_MS);
    }
}
