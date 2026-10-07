/*  Copyright (C) 2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.model.TimeSample;

public abstract class AbstractTestSampleProvider<S extends TimeSample> implements TimeSampleProvider<S> {
    public static final long DAY_MILLIS = 24 * 60 * 60 * 1000L;

    /**
     * Returns the start of the day of the given timestamp.
     */
    protected static long startOfDay(final long timestamp) {
        final Calendar cal = GregorianCalendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Returns the hour of the day of the given timestamp.
     */
    protected static int hourOfDay(final long timestamp) {
        final Calendar cal = GregorianCalendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * Keeps a value between a minimum and a maximum.
     */
    protected static int clamp(final int value, final int min, final int max) {
        return Math.min(max, Math.max(min, value));
    }

    /**
     * Keeps a value between a minimum and a maximum.
     */
    protected static float clamp(final float value, final float min, final float max) {
        return Math.min(max, Math.max(min, value));
    }

    @Nullable
    @Override
    public S getLatestSample(final long until) {
        final List<S> allSamples = getAllSamples(until - 7 * DAY_MILLIS, until);
        return !allSamples.isEmpty() ? allSamples.get(allSamples.size() - 1) : null;
    }

    @Override
    public void addSample(final S timeSample) {
        throw new UnsupportedOperationException("read-only sample provider");
    }

    @Override
    public void addSamples(final List<S> timeSamples) {
        throw new UnsupportedOperationException("read-only sample provider");
    }

    @Override
    public S createSample() {
        throw new UnsupportedOperationException("read-only sample provider");
    }
}
