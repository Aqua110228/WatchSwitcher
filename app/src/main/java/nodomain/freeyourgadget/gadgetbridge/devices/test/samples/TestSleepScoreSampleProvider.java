package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample;

public class TestSleepScoreSampleProvider extends AbstractTestSampleProvider<SleepScoreSample> {
    private static final long WAKE_TIME_MILLIS = 8 * 60 * 60 * 1000L;

    @NonNull
    @Override
    public List<SleepScoreSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<SleepScoreSample> samples = new ArrayList<>();

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            final long ts = day + WAKE_TIME_MILLIS;
            if (ts < timestampFrom || ts >= timestampTo) {
                continue;
            }
            samples.add(new TestSleepScoreSample(ts));
        }

        return samples;
    }

    @Nullable
    @Override
    public SleepScoreSample getLatestSample() {
        return new TestSleepScoreSample(startOfDay(System.currentTimeMillis()) + WAKE_TIME_MILLIS);
    }

    @Nullable
    @Override
    public SleepScoreSample getFirstSample() {
        return new TestSleepScoreSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    protected static class TestSleepScoreSample implements SleepScoreSample {
        private final long timestamp;

        public TestSleepScoreSample(final long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public int getSleepScore() {
            return TestDeviceRand.randInt(timestamp, 55, 95);
        }
    }
}
