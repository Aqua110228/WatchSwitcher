package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample;

public class TestWeightSampleProvider extends AbstractTestSampleProvider<WeightSample> {
    private static final long WEIGH_TIME_MILLIS = 7 * 60 * 60 * 1000L;
    private static final float WEIGHT_MIN_KG = 68f;
    private static final float WEIGHT_MAX_KG = 76f;

    @NonNull
    @Override
    public List<WeightSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<WeightSample> samples = new ArrayList<>();

        float weight = TestDeviceRand.randFloat(timestampFrom, WEIGHT_MIN_KG, WEIGHT_MAX_KG);

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            final long ts = day + WEIGH_TIME_MILLIS;
            weight = clamp(weight + TestDeviceRand.randFloat(ts, -0.4f, 0.4f), WEIGHT_MIN_KG, WEIGHT_MAX_KG);
            if (ts < timestampFrom || ts >= timestampTo) {
                continue;
            }
            if (TestDeviceRand.randBool(ts, 0.7f)) {
                samples.add(new TestWeightSample(ts, weight));
            }
        }

        return samples;
    }

    @Nullable
    @Override
    public WeightSample getLatestSample() {
        final long ts = startOfDay(System.currentTimeMillis()) + WEIGH_TIME_MILLIS;
        return new TestWeightSample(ts, TestDeviceRand.randFloat(ts, WEIGHT_MIN_KG, WEIGHT_MAX_KG));
    }

    @Nullable
    @Override
    public WeightSample getFirstSample() {
        return new TestWeightSample(
            TestDeviceRand.BASE_TIMESTAMP,
            TestDeviceRand.randFloat(TestDeviceRand.BASE_TIMESTAMP, WEIGHT_MIN_KG, WEIGHT_MAX_KG)
        );
    }

    protected static class TestWeightSample implements WeightSample {
        private final long timestamp;
        private final float weightKg;

        public TestWeightSample(final long timestamp, final float weightKg) {
            this.timestamp = timestamp;
            this.weightKg = weightKg;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public float getWeightKg() {
            return weightKg;
        }

        @Nullable
        @Override
        public Integer getImpedanceOhm() {
            return TestDeviceRand.randInt(timestamp, 450, 550);
        }
    }
}
