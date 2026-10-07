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
import android.os.SystemClock;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.externalevents.sleepasandroid.SleepAsAndroidAction;

import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_SLEEP_AS_ANDROID_ACTION;

/**
 * Holds the one Sleep as Android action that has to survive a connect.
 * <p>
 * Sleep as Android starts tracking and rings its alarms whether or not the wearable happens to be
 * connected, so a disconnected provider gets connected on demand and the request is replayed once
 * it is ready. Only the two actions a user notices are worth holding: START_TRACKING, without which
 * no session exists at all, and START_ALARM, without which the wearable stays silent while the
 * phone rings. CHECK_CONNECTED is repeated every few seconds anyway, so it is not held, but it
 * still triggers a connect: once a wearable has lost its link, it is the only action Sleep as
 * Android sends, and the one answered after the connect is what resumes the session. The rest are
 * meaningless without an active session. A device whose auto-reconnect is off is left alone.
 */
public class PendingSleepAsAndroidAction {

    /**
     * Sleep as Android falls back to the phone sensors after about two minutes, so a connect that
     * has not completed by then is replayed too late to be of any use. The hold expires earlier
     * than that, leaving room for the session to actually start on the wearable.
     */
    static final long TIMEOUT_MS = 90_000L;

    @Nullable
    private Intent intent;
    private String action;
    private String address;
    private long deadline;
    private long nextWake;

    /**
     * How much a held action is worth against another one arriving while the connect is still in
     * flight. An alarm outranks tracking: by the time it rings, a session that never started is of
     * no use, and the wearable buzzing is what the user is waiting for.
     *
     * @return 0 for an action that is not worth holding at all
     */
    private static int rank(@Nullable final String action) {
        if (SleepAsAndroidAction.START_ALARM.equals(action)) {
            return 2;
        }
        if (SleepAsAndroidAction.START_TRACKING.equals(action)) {
            return 1;
        }
        return 0;
    }

    /**
     * @return true if the action was worth holding across a connect
     */
    public boolean store(final Intent intent, final String deviceAddress) {
        final String action = intent.getStringExtra(EXTRA_SLEEP_AS_ANDROID_ACTION);
        final int rank = rank(action);
        if (rank == 0) {
            return false;
        }
        if (isPending() && rank < rank(this.action)) {
            return false;
        }

        this.intent = new Intent(intent);
        this.action = action;
        this.address = deviceAddress;
        this.deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        return true;
    }

    /**
     * Whether an action that is not held should still connect the wearable. CHECK_CONNECTED comes
     * every five seconds for as long as the wearable is missing, so an unreachable one is tried at
     * most once per {@link #TIMEOUT_MS}, until {@link #linkUp()} reports it reachable again.
     */
    public boolean wakes(@Nullable final String action) {
        if (!SleepAsAndroidAction.CHECK_CONNECTED.equals(action)) {
            return false;
        }
        final long now = SystemClock.elapsedRealtime();
        if (now < nextWake) {
            return false;
        }
        nextWake = now + TIMEOUT_MS;
        return true;
    }

    /**
     * Re-arm {@link #wakes} once the wearable has a live link, so a link lost soon after a connect
     * is retried on the next CHECK_CONNECTED instead of at the end of the window.
     */
    public void linkUp() {
        nextWake = 0;
    }

    /**
     * Drop a held request that Sleep as Android has since given up on, so a connect that completes
     * after the user has already dismissed the alarm or ended the session does not buzz the
     * wearable, or open a session, for nothing.
     * <p>
     * STOP_TRACKING ends both: an alarm that rang outside a session is not always followed by a
     * STOP_ALARM, and the session ending is then the only word that it is over.
     *
     * @return true if the action is one that cancels a hold rather than being held itself
     */
    public boolean cancels(@Nullable final String action) {
        if (SleepAsAndroidAction.STOP_ALARM.equals(action)) {
            if (SleepAsAndroidAction.START_ALARM.equals(this.action)) {
                clear();
            }
            return true;
        }
        if (SleepAsAndroidAction.STOP_TRACKING.equals(action)) {
            if (rank(this.action) > 0) {
                clear();
            }
            return true;
        }
        return false;
    }

    /**
     * Hand back the held action for a device that has just become usable, clearing it either way so
     * it can never fire twice or into a later, unrelated session.
     *
     * @return the action, or null if none is held, it belongs to another device, or it expired
     */
    @Nullable
    public Intent take(final String deviceAddress) {
        if (intent == null || !this.address.equals(deviceAddress)) {
            return null;
        }

        final Intent held = intent;
        final boolean expired = SystemClock.elapsedRealtime() > deadline;
        clear();

        return expired ? null : held;
    }

    public void clear() {
        intent = null;
        action = null;
        address = null;
        deadline = 0;
    }

    /**
     * @return true while an action is held and still worth replaying
     */
    public boolean isPending() {
        return intent != null && SystemClock.elapsedRealtime() <= deadline;
    }
}
