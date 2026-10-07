package nodomain.freeyourgadget.gadgetbridge.devices;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminActivitySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminActivitySample;

/**
 * Tests for {@link AbstractSampleProvider#sameDay(int, int)}.
 * <p>
 * The local day must be computed exactly like the previous Calendar/LocalDate based implementation,
 * including DST transitions, zones with :30/:45 offsets, historic offsets and the epoch boundaries.
 */
public class AbstractSampleProviderTest {
    private static final String[] ZONES = {
            "UTC",
            "Europe/Moscow",
            "Europe/Berlin",
            "Europe/Dublin",
            "Europe/Lisbon",
            "America/New_York",
            "America/Santiago",
            "America/St_Johns",
            "Australia/Lord_Howe",
            "Australia/Sydney",
            "Pacific/Chatham",
            "Pacific/Apia",
            "Asia/Kolkata",
            "Asia/Kathmandu",
            "Asia/Tehran",
            "Africa/Cairo",
            "Antarctica/Troll",
    };

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_DAY = 24 * 60 * SECONDS_PER_MINUTE;

    // not a divisor of a day, so the grid hits different times of day
    private static final int GRID_STEP_SECONDS = 3607;

    /**
     * UTC days in 2026 with an offset transition in at least one of the zones above.
     */
    private static final int[] TRANSITION_DAYS = {
            epochDay(2026, 3, 8),
            epochDay(2026, 3, 29),
            epochDay(2026, 4, 4),
            epochDay(2026, 4, 5),
            epochDay(2026, 4, 23),
            epochDay(2026, 9, 6),
            epochDay(2026, 9, 26),
            epochDay(2026, 10, 3),
            epochDay(2026, 10, 25),
            epochDay(2026, 10, 29),
            epochDay(2026, 11, 1),
    };

    private static final int[] LIMIT_TIMESTAMPS = {
            Integer.MIN_VALUE,
            Integer.MIN_VALUE + 1,
            Integer.MIN_VALUE + SECONDS_PER_DAY,
            -SECONDS_PER_DAY - 1,
            -1,
            0,
            1,
            SECONDS_PER_DAY,
            Integer.MAX_VALUE - SECONDS_PER_DAY,
            Integer.MAX_VALUE - 1,
            Integer.MAX_VALUE,
    };

    private static final int GRID_YEAR = 2026;
    private static final int GRID_FROM = yearStartEpochSecond(GRID_YEAR);
    private static final int GRID_TO = yearStartEpochSecond(GRID_YEAR + 1);

    private final AbstractSampleProvider<GarminActivitySample> sampleProvider = new GarminActivitySampleProvider(null, null);

    @Test
    public void matchesReferenceImplementation() {
        for (final String zone : ZONES) {
            withTimeZone(zone, () -> {
                for (int ts = GRID_FROM; ts < GRID_TO; ts += GRID_STEP_SECONDS) {
                    assertSameDay(ts - 1, ts);
                    assertSameDay(ts, ts + 1);
                }

                // per minute, so every local midnight of these days and the offset switch itself are hit
                for (final int day : TRANSITION_DAYS) {
                    final int from = day * SECONDS_PER_DAY;
                    for (int ts = from; ts < from + SECONDS_PER_DAY; ts += SECONDS_PER_MINUTE) {
                        assertSameDay(ts - 1, ts);
                        assertSameDay(ts, ts + 1);
                    }
                }

                for (final int t1 : LIMIT_TIMESTAMPS) {
                    for (final int t2 : LIMIT_TIMESTAMPS) {
                        assertSameDay(t1, t2);
                    }
                }
            });
        }
    }

    private void assertSameDay(final int t1, final int t2) {
        assertEquals(
                "sameDay(" + t1 + ", " + t2 + ") in " + TimeZone.getDefault().getID(),
                referenceSameDay(t1, t2),
                sampleProvider.sameDay(t1, t2)
        );
    }

    /**
     * The implementation as it was before the day index optimization, kept here as a reference.
     */
    private static boolean referenceSameDay(final int t1, final int t2) {
        final Calendar cal = Calendar.getInstance();

        cal.setTimeInMillis(t1 * 1000L - 1000L);
        final LocalDate d1 = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        cal.setTimeInMillis(t2 * 1000L - 1000L);
        final LocalDate d2 = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        return d1.equals(d2);
    }

    private static void withTimeZone(final String zone, final Runnable check) {
        final TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            check.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static int epochDay(final int year, final int month, final int day) {
        return (int) LocalDate.of(year, month, day).toEpochDay();
    }

    private static int yearStartEpochSecond(final int year) {
        return epochDay(year, 1, 1) * SECONDS_PER_DAY;
    }
}
