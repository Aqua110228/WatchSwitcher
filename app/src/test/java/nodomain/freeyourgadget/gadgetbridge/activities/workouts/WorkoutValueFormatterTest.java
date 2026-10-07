package nodomain.freeyourgadget.gadgetbridge.activities.workouts;

import static org.junit.Assert.assertEquals;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_CELSIUS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_FAHRENHEIT;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_FOOT;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_JUMPS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KILOMETERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KMPH;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KNOTS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MILE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_METERS_PER_SECOND;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_REPS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_REVS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_100_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_500_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_KM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_M;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SPM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_STEPS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_STROKES;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.DistanceUnit;
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Exhaustive per-unit checks for {@link WorkoutValueFormatter}: every distance/speed/pace unit
 * is asserted in both metric and imperial, including the rowing 500 m pace which stays metric.
 */
public class WorkoutValueFormatterTest extends TestBase {
    private WorkoutValueFormatter metric;
    private WorkoutValueFormatter imperial;
    private Locale previousLocale;

    @Before
    public void setUpFormatters() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        metric = new WorkoutValueFormatter(ActivityKind.UNKNOWN, DistanceUnit.METRIC, WeightUnit.KILOGRAM, false);
        imperial = new WorkoutValueFormatter(ActivityKind.UNKNOWN, DistanceUnit.IMPERIAL, WeightUnit.KILOGRAM, false);
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    private String metric(final Object value, final String unit) {
        return metric.formatValue(value, unit, true);
    }

    private String imperial(final Object value, final String unit) {
        return imperial.formatValue(value, unit, true);
    }

    // --- Pace (min:sec) ---

    @Test
    public void paceSecondsPerKm() {
        assertEquals("6:00 min/km", metric(360, UNIT_SECONDS_PER_KM));
        assertEquals("9:39 min/mi", imperial(360, UNIT_SECONDS_PER_KM));
    }

    @Test
    public void paceRoundingCarriesSecondsIntoMinutes() {
        final String[] units = {
                UNIT_SECONDS_PER_KM,
                UNIT_SECONDS_PER_M,
                UNIT_SECONDS_PER_100_METERS,
                UNIT_SECONDS_PER_500_METERS
        };
        final String[] metricLabels = {"min/km", "min/km", "min/100m", "min/500m"};
        final String[] imperialLabels = {"min/mi", "min/mi", "min/100yd", "min/500m"};
        final double[] metricFactors = {1, 1000, 1, 1};
        final double[] imperialFactors = {1.609344, 1609.344, 0.9144, 1};
        final double[] seconds = {0, 299.4, 299.6, 300};
        final String[] expected = {"0:00", "4:59", "5:00", "5:00"};

        for (int i = 0; i < units.length; i++) {
            for (int j = 0; j < seconds.length; j++) {
                final double metricValue = seconds[j] / metricFactors[i];
                final double imperialValue = seconds[j] / imperialFactors[i];
                assertEquals(expected[j] + " " + metricLabels[i], metric(metricValue, units[i]));
                assertEquals(expected[j] + " " + imperialLabels[i], imperial(imperialValue, units[i]));
                assertEquals(expected[j], metric.formatValue(metricValue, units[i], false));
                assertEquals(expected[j], imperial.formatValue(imperialValue, units[i], false));
            }
        }
    }

    @Test
    public void paceSecondsPerMeter() {
        assertEquals("5:00 min/km", metric(0.3, UNIT_SECONDS_PER_M));
        assertEquals("8:03 min/mi", imperial(0.3, UNIT_SECONDS_PER_M));
    }

    @Test
    public void paceSecondsPer100Meters() {
        assertEquals("1:30 min/100m", metric(90, UNIT_SECONDS_PER_100_METERS));
        assertEquals("1:22 min/100yd", imperial(90, UNIT_SECONDS_PER_100_METERS));
    }

    @Test
    public void paceSecondsPer500MetersStaysMetric() {
        // rowing 500 m pace is conventionally kept metric regardless of the imperial setting
        assertEquals("2:00 min/500m", metric(120, UNIT_SECONDS_PER_500_METERS));
        assertEquals("2:00 min/500m", imperial(120, UNIT_SECONDS_PER_500_METERS));
    }

    @Test
    public void temperatureRespectsUnitPreference() {
        final WorkoutValueFormatter fahrenheit =
                new WorkoutValueFormatter(ActivityKind.UNKNOWN, DistanceUnit.METRIC, WeightUnit.KILOGRAM, false, true);
        assertEquals("20 ℃", metric(20, UNIT_CELSIUS));
        assertEquals("68 ℉", fahrenheit.formatValue(20, UNIT_CELSIUS, true));
        assertEquals("20 ℃", metric(68, UNIT_FAHRENHEIT));
        assertEquals("68 ℉", fahrenheit.formatValue(68, UNIT_FAHRENHEIT, true));
    }

    @Test
    public void tileValuesSkipTheUnitForCounts() {
        assertEquals("8432", metric.formatTileValue(8432, UNIT_STEPS));
        assertEquals("1208", metric.formatTileValue(1208, UNIT_STROKES));
        assertEquals("30", metric.formatTileValue(30, UNIT_JUMPS));
        assertEquals("12", metric.formatTileValue(12, UNIT_REPS));
        assertEquals("55", metric.formatTileValue(55, UNIT_REVS));
        // rates and everything else keep their unit
        assertEquals("170 steps/min", metric.formatTileValue(170, UNIT_SPM));
        assertEquals("10 km", metric.formatTileValue(10, UNIT_KILOMETERS));
    }

    @Test
    public void plainFormatValueStillShowsCountUnits() {
        // only tiles drop the unit, e.g. the Details tab rows keep it
        assertEquals("8432 steps", metric(8432, UNIT_STEPS));
    }

    // --- Speed ---

    @Test
    public void speedMetersPerSecond() {
        assertEquals("36 km/h", metric(10, UNIT_METERS_PER_SECOND));
        assertEquals("22.37 mi/h", imperial(10, UNIT_METERS_PER_SECOND));
    }

    @Test
    public void speedKilometersPerHour() {
        assertEquals("10 km/h", metric(10, UNIT_KMPH));
        assertEquals("6.21 mi/h", imperial(10, UNIT_KMPH));
    }

    // --- Distance ---

    @Test
    public void distanceKilometers() {
        assertEquals("10 km", metric(10, UNIT_KILOMETERS));
        assertEquals("6.21 mi", imperial(10, UNIT_KILOMETERS));
    }

    @Test
    public void distanceMetersShort() {
        assertEquals("100 m", metric(100, UNIT_METERS));
        assertEquals("328.08 ft", imperial(100, UNIT_METERS));
    }

    @Test
    public void distanceMetersLong() {
        // metric rolls up to km above 2 km; imperial rolls up to miles above 6000 ft
        assertEquals("5 km", metric(5000, UNIT_METERS));
        assertEquals("3.11 mi", imperial(5000, UNIT_METERS));
    }

    @Test
    public void stepLengthMillimeters() {
        assertEquals("50 mm", metric(50, UNIT_MM));
        // imperial now shows inches (also fixes the summary step-length gap in Codeberg #6185)
        assertEquals("1.97 in", imperial(50, UNIT_MM));
    }

    // --- convert(): the shared authority also used by the charts ---

    @Test
    public void convertFixedUnitSkipsMagnitudeRollups() {
        // non-fixed rolls a long distance up to miles; fixed keeps feet for a stable chart axis
        WorkoutValueFormatter.Converted auto = imperial.convert(5000, UNIT_METERS, false);
        assertEquals(UNIT_MILE, auto.unit);
        assertEquals(3.106855, auto.value, 1e-4);

        WorkoutValueFormatter.Converted fixed = imperial.convert(5000, UNIT_METERS, true);
        assertEquals(UNIT_FOOT, fixed.unit);
        assertEquals(16404.2, fixed.value, 1e-1);

        // metric fixed keeps meters instead of rolling up to km
        assertEquals(UNIT_METERS, metric.convert(5000, UNIT_METERS, true).unit);
        assertEquals(5000.0, metric.convert(5000, UNIT_METERS, true).value, 1e-6);
    }

    @Test
    public void convertNauticalSpeedYieldsKnots() {
        final WorkoutValueFormatter sailing = new WorkoutValueFormatter(
                ActivityKind.SAILING, DistanceUnit.METRIC, WeightUnit.KILOGRAM, true);
        WorkoutValueFormatter.Converted knots = sailing.convert(10, UNIT_METERS_PER_SECOND, true);
        assertEquals(UNIT_KNOTS, knots.unit);
        assertEquals(19.43844, knots.value, 1e-4);
    }
}
