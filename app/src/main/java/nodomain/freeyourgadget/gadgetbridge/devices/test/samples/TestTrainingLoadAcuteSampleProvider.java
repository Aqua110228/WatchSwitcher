/*  Copyright (C) 2026 José Rebelo

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSample;

/**
 * The training load of the last 7 days. One sample per day.
 */
public class TestTrainingLoadAcuteSampleProvider extends AbstractTestSampleProvider<GenericTrainingLoadAcuteSample> {
    private static final long SAMPLE_TIME_MILLIS = 23 * 60 * 60 * 1000L;

    @NonNull
    @Override
    public List<GenericTrainingLoadAcuteSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<GenericTrainingLoadAcuteSample> samples = new ArrayList<>();

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            final long ts = day + SAMPLE_TIME_MILLIS;
            if (ts < timestampFrom || ts >= timestampTo) {
                continue;
            }
            samples.add(createSample(ts));
        }

        return samples;
    }

    @Nullable
    @Override
    public GenericTrainingLoadAcuteSample getLatestSample() {
        return createSample(startOfDay(System.currentTimeMillis()) + SAMPLE_TIME_MILLIS);
    }

    @Nullable
    @Override
    public GenericTrainingLoadAcuteSample getFirstSample() {
        return createSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    private GenericTrainingLoadAcuteSample createSample(final long timestamp) {
        final GenericTrainingLoadAcuteSample sample = new GenericTrainingLoadAcuteSample();
        sample.setTimestamp(timestamp);
        sample.setValue(TestDeviceRand.randInt(timestamp, 200, 900));
        return sample;
    }
}
