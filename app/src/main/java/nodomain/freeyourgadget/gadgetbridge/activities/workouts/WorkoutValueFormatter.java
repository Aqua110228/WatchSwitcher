/*  Copyright (C) 2024-2026 José Rebelo, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_CELSIUS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_CM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_EPOC_TIME;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_FAHRENHEIT;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_FOOT;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_FOOT_PER_HOUR;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_HOURS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_JOULE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KG;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KILOJOULE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KILOMETERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KMPH;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_KNOTS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_LB;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_METERS_PER_HOUR;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_METERS_PER_SECOND;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MILE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MILE_PER_HOUR;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES_PER_100_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES_PER_100_YARDS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES_PER_500_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES_PER_KM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES_PER_MILE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_ML;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_ML_KG_MIN;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_INCH;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_NAUTICAL_MILES;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_PERCENTAGE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_RAW_STRING;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_100_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_100_YARDS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_500_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_KM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_M;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_SPORT;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import lineageos.weather.util.TemperatureUtils;
import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.DistanceUnit;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit;
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class WorkoutValueFormatter {
    private static final Logger LOG = LoggerFactory.getLogger(WorkoutValueFormatter.class);

    private boolean show_raw_data = false;

    private ActivityKind activityKind;
    private final DistanceUnit distanceUnit;
    private final WeightUnit weightUnit;
    private final boolean useNauticalUnits;
    private final boolean useFahrenheit;
    private final DecimalFormat df2 = new DecimalFormat("#.##");
    private final DecimalFormat df1 = new DecimalFormat("#.#");

    public WorkoutValueFormatter() {
        this(ActivityKind.UNKNOWN);
    }

    public WorkoutValueFormatter(final ActivityKind activityKind) {
        this(activityKind,
                GBApplication.getPrefs().getDistanceUnit(),
                GBApplication.getPrefs().getWeightUnit(),
                GBApplication.getPrefs().getBoolean("units_nautical", true),
                GBApplication.getPrefs().getTemperatureUnit() == TemperatureUnit.FAHRENHEIT);
    }

    /**
     * Constructor with explicit units, mostly for testing without touching the shared preferences.
     */
    public WorkoutValueFormatter(final ActivityKind activityKind,
                                 final DistanceUnit distanceUnit,
                                 final WeightUnit weightUnit,
                                 final boolean useNauticalUnits) {
        this(activityKind, distanceUnit, weightUnit, useNauticalUnits, false);
    }

    public WorkoutValueFormatter(final ActivityKind activityKind,
                                 final DistanceUnit distanceUnit,
                                 final WeightUnit weightUnit,
                                 final boolean useNauticalUnits,
                                 final boolean useFahrenheit) {
        this.activityKind = activityKind;
        this.distanceUnit = distanceUnit;
        this.weightUnit = weightUnit;
        this.useNauticalUnits = useNauticalUnits;
        this.useFahrenheit = useFahrenheit;
    }

    public void setActivityKind(final ActivityKind activityKind) {
        this.activityKind = activityKind;
    }

    public void setRawData(final boolean showRawData) {
        this.show_raw_data = showRawData;
    }

    public String formatValue(final Object rawValue, String unit, boolean showUnit) {
        if (rawValue == null) {
            return GBApplication.getContext().getString(R.string.stats_empty_value);
        }

        if (UNIT_RAW_STRING.equals(unit)) {
            return String.valueOf(rawValue);
        }

        if (rawValue instanceof CharSequence || ActivitySummaryEntries.UNIT_STRING.equals(unit)) {
            // we could optimize here a bit and only do this for particular activities (swim at the moment...)
            try {
                return getStringResourceByName(String.valueOf(rawValue));
            } catch (final Exception e) {
                LOG.error("Failed to get string resource by name for {}", rawValue);
                return String.valueOf(rawValue);
            }
        }

        if (!(rawValue instanceof Number)) {
            return String.valueOf(rawValue);
        }

        double value = ((Number) rawValue).doubleValue();

        if (!show_raw_data) {
            // weight has its own locale-aware formatter and returns a finished string directly
            if (UNIT_KG.equals(unit)) {
                return WeightUnit.Companion.formatWeight(GBApplication.getContext(), value, weightUnit);
            }
            if (UNIT_LB.equals(unit)) {
                return WeightUnit.Companion.formatWeight(GBApplication.getContext(), value / 2.2046226f, weightUnit);
            }
            final Converted converted = convert(value, unit, false);
            value = converted.value;
            unit = converted.unit;
        }

        if (unit.equals(UNIT_SECONDS) && !show_raw_data && showUnit) { //rather than plain seconds, show formatted duration
            return DateTimeUtils.formatDurationHoursMinutes((long) value, TimeUnit.SECONDS);
        } else if (unit.equals(UNIT_SECONDS_SPORT) && !show_raw_data && showUnit) {
            return DateTimeUtils.formatSportsDuration(Math.round(1000L * value), TimeUnit.MILLISECONDS);
        } else if (UNIT_EPOC_TIME.equals(unit) && !show_raw_data) {
            long epoc = ((Number) rawValue).longValue();
            return DateTimeUtils.formatLocalTime(epoc * 1000L);
        } else if (unit.equals(UNIT_MINUTES_PER_KM) || unit.equals(UNIT_MINUTES_PER_MILE) || unit.equals(UNIT_MINUTES_PER_100_METERS) || unit.equals(UNIT_MINUTES_PER_100_YARDS) || unit.equals(UNIT_MINUTES_PER_500_METERS)) {
            // Format pace
            final long totalSeconds = Math.round(value * 60);
            String format = showUnit ? "%d:%02d %s" : "%d:%02d";
            return String.format(
                    Locale.getDefault(),
                    format,
                    totalSeconds / 60, totalSeconds % 60,
                    getUnitString(unit)
            );
        } else {
            String format = showUnit ? "%s %s" : "%s";
            // Reduce precision for certain measurements
            final String formattedValue = switch (unit) {
                case ActivitySummaryEntries.UNIT_ML_KG_MIN -> df1.format(value);
                case ActivitySummaryEntries.UNIT_BREATHS_PER_MIN -> String.valueOf(Math.round(value));
                default -> df2.format(value);
            };
            return String.format(format, formattedValue, getUnitString(unit));
        }
    }

    public String formatValue(final Object rawValue, String unit) {
        return formatValue(rawValue, unit, true);
    }

    /**
     * Formats a value for a stat tile, which always shows a label next to it. Plain counts
     * (steps, strokes...) skip the unit, since it would only repeat that label.
     */
    public String formatTileValue(final Object rawValue, final String unit) {
        return formatValue(rawValue, unit, !isCountUnit(unit));
    }

    private static boolean isCountUnit(final String unit) {
        return switch (unit) {
            case ActivitySummaryEntries.UNIT_STEPS,
                 ActivitySummaryEntries.UNIT_STROKES,
                 ActivitySummaryEntries.UNIT_JUMPS,
                 ActivitySummaryEntries.UNIT_REPS,
                 ActivitySummaryEntries.UNIT_REVS -> true;
            default -> false;
        };
    }

    /** A value converted to its display unit: the numeric value plus the display unit token. */
    public static final class Converted {
        public final double value;
        public final String unit;

        public Converted(final double value, final String unit) {
            this.value = value;
            this.unit = unit;
        }
    }

    /**
     * The single source of truth for metric→display unit conversion. Given a value and its
     * metric unit token, returns the value converted to the configured distance/nautical unit
     * system plus the matching display unit token. Final string formatting (pace min:sec,
     * durations, decimals, weight) stays in {@link #formatValue}; weight tokens are not handled
     * here.
     *
     * @param fixedUnit when {@code true}, the magnitude-based roll-ups (m→km, ft→mi,
     *                  mm→m/cm, J→kJ) are skipped so a quantity maps to one stable unit
     *                  across its whole range – required for continuous chart axes. Summary
     *                  rows pass {@code false}.
     */
    public Converted convert(double value, String unit, final boolean fixedUnit) {
        if (!fixedUnit && UNIT_MM.equals(unit)) {
            if (value > 1000) {
                unit = UNIT_METERS;
                value /= 1000d;
            } else if (value > 100) {
                unit = UNIT_CM;
                value /= 10d;
            }
        }

        switch (unit) {
            case UNIT_MM:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value / 25.4d;
                    unit = UNIT_INCH;
                }
                break;
            case UNIT_CM:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value * 0.0328084;
                    unit = UNIT_FOOT;
                }
                break;
            case UNIT_METERS_PER_SECOND:
                if (useNauticalUnits && ActivityKind.isNauticalActivity(activityKind)) {
                    value = value * 1.943844D;
                    unit = UNIT_KNOTS;
                } else if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value * 2.236936D;
                    unit = UNIT_MILE_PER_HOUR;
                } else { //metric
                    value = value * 3.6;
                    unit = UNIT_KMPH;
                }
                break;
            case UNIT_METERS_PER_HOUR:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value * 3.28084D;
                    unit = UNIT_FOOT_PER_HOUR;
                }
                break;
            case UNIT_SECONDS_PER_M:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value * (1609.344 / 60D);
                    unit = UNIT_MINUTES_PER_MILE;
                } else { //metric
                    value = value * (1000 / 60D);
                    unit = UNIT_MINUTES_PER_KM;
                }
                break;
            case UNIT_SECONDS_PER_KM:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value / 60D * 1.609344;
                    unit = UNIT_MINUTES_PER_MILE;
                } else { //metric
                    value = value / 60D;
                    unit = UNIT_MINUTES_PER_KM;
                }
                break;
            case UNIT_KILOMETERS:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value * 0.621371D;
                    unit = UNIT_MILE;
                }
                break;
            case UNIT_METERS:
                if (useNauticalUnits && ActivityKind.isNauticalActivity(activityKind)) {
                    value = value / 1852D;
                    unit = UNIT_NAUTICAL_MILES;
                } else if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value * 3.28084D;
                    unit = UNIT_FOOT;
                    if (!fixedUnit && value > 6000) {
                        value = value * 0.0001893939D;
                        unit = UNIT_MILE;
                    }
                } else { //metric
                    if (!fixedUnit && value > 2000) {
                        value = value / 1000;
                        unit = UNIT_KILOMETERS;
                    }
                }
                break;
            case UNIT_KMPH:
                if (useNauticalUnits && ActivityKind.isNauticalActivity(activityKind)) {
                    value = value / 1.852D;
                    unit = UNIT_KNOTS;
                } else if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value * 0.621371D;
                    unit = UNIT_MILE_PER_HOUR;
                }
                break;
            case UNIT_SECONDS_PER_100_METERS:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = (value * 0.9144) / 60D;
                    unit = UNIT_MINUTES_PER_100_YARDS;
                } else { //metric
                    value = value / 60D;
                    unit = UNIT_MINUTES_PER_100_METERS;
                }
                break;
            case UNIT_SECONDS_PER_100_YARDS:
                if (distanceUnit == DistanceUnit.IMPERIAL) {
                    value = value / 60D;
                    unit = UNIT_MINUTES_PER_100_YARDS;
                } else { //metric
                    value = (value * 1.0936133D) / 60D;
                    unit = UNIT_MINUTES_PER_100_METERS;
                }
                break;
            case UNIT_SECONDS_PER_500_METERS:
                value = value / 60D;
                unit = UNIT_MINUTES_PER_500_METERS;
                break;
            case UNIT_CELSIUS:
                if (useFahrenheit) {
                    value = TemperatureUtils.celsiusToFahrenheit(value);
                    unit = UNIT_FAHRENHEIT;
                }
                break;
            case UNIT_FAHRENHEIT:
                if (!useFahrenheit) {
                    value = TemperatureUtils.fahrenheitToCelsius(value);
                    unit = UNIT_CELSIUS;
                }
                break;
            case UNIT_JOULE:
                if (!fixedUnit && value > 10000) {
                    value = value / 1000D;
                    unit = UNIT_KILOJOULE;
                }
                break;
        }
        return new Converted(value, unit);
    }

    public String getStringResourceByName(String aString) {
        return getStringResourceByName(GBApplication.getContext(), aString);
    }

    private static String getStringResourceByName(@NonNull final Context context, final String aString) {
        String packageName = BuildConfig.APPLICATION_ID;
        int resId = context.getResources().getIdentifier(aString, "string", packageName);
        if (resId == 0) {
            //LOG.warn("SportsActivity " + "Missing string in strings:" + aString);
            return aString;
        } else {
            return context.getString(resId);
        }
    }

    /**
     * The label a value with this unit is shown with, e.g. "min/mi" for a pace when imperial units
     * are on, or the stored unit as is when raw data is shown. For units whose conversion doesn't
     * depend on the value (pace, heart rate...), which is what a table header needs.
     */
    public String getDisplayUnitLabel(@NonNull final String unit) {
        final String displayUnit = show_raw_data ? unit : convert(0d, unit, false).unit;
        return getUnitString(displayUnit);
    }

    public static String getUnitString(@NonNull final Context context, @Nullable String unit) {
        if (unit == null || unit.isEmpty()) {
            return unit;
        }

        // compatibility mappings
        // removal would require updates to stored activity summaries
        unit = switch (unit) {
            case "%" -> UNIT_PERCENTAGE;
            case "ml/kg/min" -> UNIT_ML_KG_MIN;
            case "hours" -> UNIT_HOURS;
            case "kg" -> UNIT_KG;
            case "lb" -> UNIT_LB;
            case "ml" -> UNIT_ML;
            default -> unit;
        };

        return getStringResourceByName(context, unit);
    }

    public String getUnitString(@Nullable String unit) {
        return getUnitString(GBApplication.getContext(), unit);
    }
}
