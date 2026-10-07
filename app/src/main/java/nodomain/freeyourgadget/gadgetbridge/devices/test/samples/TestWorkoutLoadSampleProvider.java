package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.WorkoutLoadSample;

/**
 * The load of a single workout. Up to one workout per day, in the evening.
 */
public class TestWorkoutLoadSampleProvider extends AbstractTestSampleProvider<WorkoutLoadSample> {
    private static final long WORKOUT_TIME_MILLIS = 19 * 60 * 60 * 1000L;

    @NonNull
    @Override
    public List<WorkoutLoadSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<WorkoutLoadSample> samples = new ArrayList<>();

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            final long ts = day + WORKOUT_TIME_MILLIS;
            if (ts < timestampFrom || ts >= timestampTo) {
                continue;
            }
            if (TestDeviceRand.randBool(ts, 0.6f)) {
                samples.add(new TestWorkoutLoadSample(ts));
            }
        }

        return samples;
    }

    @Nullable
    @Override
    public WorkoutLoadSample getLatestSample() {
        return new TestWorkoutLoadSample(startOfDay(System.currentTimeMillis()) + WORKOUT_TIME_MILLIS);
    }

    @Nullable
    @Override
    public WorkoutLoadSample getFirstSample() {
        return new TestWorkoutLoadSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    protected static class TestWorkoutLoadSample implements WorkoutLoadSample {
        private final long timestamp;

        public TestWorkoutLoadSample(final long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public int getValue() {
            return TestDeviceRand.randInt(timestamp, 40, 220);
        }
    }
}
