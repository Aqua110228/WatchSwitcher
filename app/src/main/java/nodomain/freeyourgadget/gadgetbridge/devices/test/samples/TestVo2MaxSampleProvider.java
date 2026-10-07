package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.Vo2MaxSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample;

public class TestVo2MaxSampleProvider extends AbstractTestSampleProvider<Vo2MaxSample>
    implements Vo2MaxSampleProvider<Vo2MaxSample> {
    private static final long MEASUREMENT_TIME_MILLIS = 19 * 60 * 60 * 1000L;

    private final GBDevice device;

    public TestVo2MaxSampleProvider(final GBDevice device) {
        this.device = device;
    }

    @NonNull
    @Override
    public List<Vo2MaxSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<Vo2MaxSample> samples = new ArrayList<>();

        final boolean multiSport = device.getDeviceCoordinator().supportsVO2MultiSport(device);

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            final long ts = day + MEASUREMENT_TIME_MILLIS;
            if (ts < timestampFrom || ts >= timestampTo) {
                continue;
            }
            if (!TestDeviceRand.randBool(ts, 0.3f)) {
                continue;
            }
            if (multiSport) {
                samples.add(new TestVo2MaxSample(ts, TestDeviceRand.randBool(ts, 0.5f) ? Vo2MaxSample.Type.RUNNING : Vo2MaxSample.Type.CYCLING));
            } else {
                samples.add(new TestVo2MaxSample(ts, Vo2MaxSample.Type.ANY));
            }
        }

        return samples;
    }

    @Nullable
    @Override
    public Vo2MaxSample getLatestSample(final Vo2MaxSample.Type type, final long until) {
        final List<Vo2MaxSample> samples = getAllSamples(until - 30 * DAY_MILLIS, until);
        for (int i = samples.size() - 1; i >= 0; i--) {
            if (type == Vo2MaxSample.Type.ANY || samples.get(i).getType() == type) {
                return samples.get(i);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Vo2MaxSample getLatestSample() {
        return getLatestSample(Vo2MaxSample.Type.ANY, System.currentTimeMillis());
    }

    @Nullable
    @Override
    public Vo2MaxSample getFirstSample() {
        return new TestVo2MaxSample(TestDeviceRand.BASE_TIMESTAMP, Vo2MaxSample.Type.ANY);
    }

    protected static class TestVo2MaxSample implements Vo2MaxSample {
        private final long timestamp;
        private final Type type;

        public TestVo2MaxSample(final long timestamp, final Type type) {
            this.timestamp = timestamp;
            this.type = type;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public float getValue() {
            return TestDeviceRand.randInt(timestamp, 38, 55);
        }
    }
}
