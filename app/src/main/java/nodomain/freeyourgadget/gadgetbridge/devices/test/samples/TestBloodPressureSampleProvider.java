package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;

public class TestBloodPressureSampleProvider extends AbstractTestSampleProvider<BloodPressureSample> {
    private static final int[] MEASUREMENT_HOURS = {8, 14, 21};

    @NonNull
    @Override
    public List<BloodPressureSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<BloodPressureSample> samples = new ArrayList<>();

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            for (final int hour : MEASUREMENT_HOURS) {
                final long ts = day + hour * 60 * 60 * 1000L;
                if (ts < timestampFrom || ts >= timestampTo) {
                    continue;
                }
                if (TestDeviceRand.randBool(ts, 0.8f)) {
                    samples.add(new TestBloodPressureSample(ts));
                }
            }
        }

        return samples;
    }

    @Nullable
    @Override
    public BloodPressureSample getLatestSample() {
        final long ts = System.currentTimeMillis();
        return new TestBloodPressureSample(ts - TestDeviceRand.randLong(ts, 60 * 60 * 1000L, 12 * 60 * 60 * 1000L));
    }

    @Nullable
    @Override
    public BloodPressureSample getFirstSample() {
        return new TestBloodPressureSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    protected static class TestBloodPressureSample implements BloodPressureSample {
        private final long timestamp;
        private final int systolic;

        public TestBloodPressureSample(final long timestamp) {
            this.timestamp = timestamp;
            this.systolic = TestDeviceRand.randInt(timestamp, 105, 140);
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public int getBpSystolic() {
            return systolic;
        }

        @Override
        public int getBpDiastolic() {
            return systolic - TestDeviceRand.randInt(timestamp + 1, 35, 50);
        }
    }
}
