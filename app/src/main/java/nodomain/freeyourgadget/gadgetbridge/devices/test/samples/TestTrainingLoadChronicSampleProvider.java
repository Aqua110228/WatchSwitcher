package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSample;

/**
 * The training load of the last 28 days. One sample per day.
 */
public class TestTrainingLoadChronicSampleProvider extends AbstractTestSampleProvider<GenericTrainingLoadChronicSample> {
    private static final long SAMPLE_TIME_MILLIS = 23 * 60 * 60 * 1000L;

    @NonNull
    @Override
    public List<GenericTrainingLoadChronicSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<GenericTrainingLoadChronicSample> samples = new ArrayList<>();

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
    public GenericTrainingLoadChronicSample getLatestSample() {
        return createSample(startOfDay(System.currentTimeMillis()) + SAMPLE_TIME_MILLIS);
    }

    @Nullable
    @Override
    public GenericTrainingLoadChronicSample getFirstSample() {
        return createSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    private GenericTrainingLoadChronicSample createSample(final long timestamp) {
        final GenericTrainingLoadChronicSample sample = new GenericTrainingLoadChronicSample();
        sample.setTimestamp(timestamp);
        sample.setValue(TestDeviceRand.randInt(timestamp, 300, 700));
        return sample;
    }
}
