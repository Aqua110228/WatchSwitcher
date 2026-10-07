/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.model.heartratezones;

import android.content.Context;

import androidx.core.content.ContextCompat;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;

public class HeartRateZonesResolver {

    private static final int DEFAULT_AGE = 30;
    private static final int MIN_AGE = 10;
    private static final int MAX_AGE = 100;

    private HeartRateZonesResolver() {
    }

    /**
     * Resolves zones for a specific workout. Precedence:
     * per-activity thresholds reported by the device (Garmin FIT, Zepp OS) → device config
     * ({@link HeartRateZonesSpec}, Huawei only) → {@code 220 − age} default. The per-activity path
     * keeps the displayed bands consistent with what the watch actually used for the session.
     */
    public static HeartRateZones resolve(ActivitySummaryData summary, GBDevice device, ActivityUser user) {
        final HeartRateZones fromActivity = fromActivitySummary(summary);
        if (fromActivity != null) {
            return fromActivity;
        }
        return resolve(device, user);
    }

    private static HeartRateZones fromActivitySummary(ActivitySummaryData summary) {
        if (summary == null) {
            return null;
        }
        final int z1 = summary.getNumber(ActivitySummaryEntries.HR_ZONE_BOUND_1, 0).intValue();
        final int z2 = summary.getNumber(ActivitySummaryEntries.HR_ZONE_BOUND_2, 0).intValue();
        final int z3 = summary.getNumber(ActivitySummaryEntries.HR_ZONE_BOUND_3, 0).intValue();
        final int z4 = summary.getNumber(ActivitySummaryEntries.HR_ZONE_BOUND_4, 0).intValue();
        final int z5 = summary.getNumber(ActivitySummaryEntries.HR_ZONE_BOUND_5, 0).intValue();
        // Require all five present and strictly increasing; otherwise fall through to spec/default.
        if (z1 > 0 && z1 < z2 && z2 < z3 && z3 < z4 && z4 < z5) {
            return DefaultHeartRateZones.fromBoundaries(z1, z2, z3, z4, z5);
        }
        return null;
    }

    public static HeartRateZones resolve(GBDevice device, ActivityUser user) {
        if (device != null) {
            DeviceCoordinator coordinator = device.getDeviceCoordinator();
            HeartRateZonesSpec spec = coordinator != null ? coordinator.getHeartRateZonesSpec(device) : null;
            if (spec != null) {
                List<HeartRateZonesConfig> configs = spec.getDeviceConfig();
                if (configs != null) {
                    for (HeartRateZonesConfig cfg : configs) {
                        if (cfg.getType() != HeartRateZonesSpec.PostureType.UPRIGHT) {
                            continue;
                        }
                        HeartRateZones.CalculationMethod method = cfg.getCurrentCalculationMethod();
                        for (HeartRateZones zn : cfg.getConfigByMethods()) {
                            if (zn.getMethod() == method && zn.hasValidData()) {
                                return zn;
                            }
                        }
                    }
                }
            }
        }
        return buildDefault(user);
    }

    private static HeartRateZones buildDefault(ActivityUser user) {
        int age = DEFAULT_AGE;
        if (user != null) {
            try {
                int reported = user.getAge();
                if (reported >= MIN_AGE && reported <= MAX_AGE) {
                    age = reported;
                }
            } catch (Exception e) {
                // DOB unset or unparseable, fall back to the default age
            }
        }
        int hrMax = 220 - age;
        return new DefaultHeartRateZones(HeartRateZones.CalculationMethod.MHR, hrMax);
    }

    /**
     * Returns 0 if hr is below zone1, 1..5 for the matching zone, with zone 5 unbounded above.
     */
    public static int zoneOf(int hr, HeartRateZones zones) {
        if (zones == null) {
            return 0;
        }
        if (hr >= zones.getZone5()) return 5;
        if (hr >= zones.getZone4()) return 4;
        if (hr >= zones.getZone3()) return 3;
        if (hr >= zones.getZone2()) return 2;
        if (hr >= zones.getZone1()) return 1;
        return 0;
    }

    public static int lowerBoundOf(int zoneIdx, HeartRateZones zones) {
        return switch (zoneIdx) {
            case 1 -> zones.getZone1();
            case 2 -> zones.getZone2();
            case 3 -> zones.getZone3();
            case 4 -> zones.getZone4();
            case 5 -> zones.getZone5();
            default -> 0;
        };
    }

    public static int upperBoundOf(int zoneIdx, HeartRateZones zones, int chartMax) {
        return switch (zoneIdx) {
            case 0 -> zones.getZone1();
            case 1 -> zones.getZone2();
            case 2 -> zones.getZone3();
            case 3 -> zones.getZone4();
            case 4 -> zones.getZone5();
            default -> chartMax;
        };
    }

    public static int colorForZone(Context ctx, int zoneIdx) {
        return switch (zoneIdx) {
            case 1 -> ContextCompat.getColor(ctx, R.color.hr_zone_warm_up_color);
            case 2 -> ContextCompat.getColor(ctx, R.color.hr_zone_easy_color);
            case 3 -> ContextCompat.getColor(ctx, R.color.hr_zone_aerobic_color);
            case 4 -> ContextCompat.getColor(ctx, R.color.hr_zone_threshold_color);
            case 5 -> ContextCompat.getColor(ctx, R.color.hr_zone_maximum_color);
            default -> ContextCompat.getColor(ctx, R.color.chart_heartrate);
        };
    }

    public static String labelForZone(Context ctx, int zoneIdx) {
        return switch (zoneIdx) {
            case 1 -> ctx.getString(R.string.hrZoneWarmUp);
            case 2 -> ctx.getString(R.string.hrZoneFatBurn);
            case 3 -> ctx.getString(R.string.hrZoneAerobic);
            case 4 -> ctx.getString(R.string.hrZoneAnaerobic);
            case 5 -> ctx.getString(R.string.hrZoneExtreme);
            default -> "";
        };
    }
}
