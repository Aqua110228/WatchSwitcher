package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;

public class TestSolarChargeSampleProvider extends AbstractTestSampleProvider<SolarChargeSample> {
    private static final int SUNRISE_HOUR = 7;
    private static final int SUNSET_HOUR = 20;

    @NonNull
    @Override
    public List<SolarChargeSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<SolarChargeSample> samples = new ArrayList<>();

        for (long ts = timestampFrom; ts < timestampTo; ts += 15 * 60 * 1000L) {
            samples.add(new TestSolarChargeSample(ts));
        }

        return samples;
    }

    @Nullable
    @Override
    public SolarChargeSample getLatestSample() {
        return new TestSolarChargeSample(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public SolarChargeSample getFirstSample() {
        return new TestSolarChargeSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    /**
     * Follow daylight hours, zero at night.
     */
    protected static class TestSolarChargeSample implements SolarChargeSample {
        private final long timestamp;
        private final float percent;

        public TestSolarChargeSample(final long timestamp) {
            this.timestamp = timestamp;

            final int hour = hourOfDay(timestamp);
            if (hour < SUNRISE_HOUR || hour >= SUNSET_HOUR) {
                this.percent = 0;
            } else {
                final double dayProgress = (double) (hour - SUNRISE_HOUR) / (SUNSET_HOUR - SUNRISE_HOUR);
                final double daylight = Math.sin(dayProgress * Math.PI);
                final float clouds = TestDeviceRand.randFloat(timestamp, 0.4f, 1f);
                this.percent = clamp((float) (daylight * 100 * clouds), 0f, 100f);
            }
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public float getPercent() {
            return percent;
        }

        @Override
        public long getGain() {
            return Math.round(percent * 60 * 1000L);
        }
    }
}
