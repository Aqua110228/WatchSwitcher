/*  Copyright (C) 2026 a0z

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
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;

/**
 * Lux-hours/battery-gain math shared between the daily chart and the week/month
 * per-day aggregation.
 */
final class SolarChargingStats {
    // Garmin's own documented reference: 100% solar intensity corresponds to 50,000 lux
    // (direct sunlight on a clear day). Not in any FIT profile - from Garmin's published
    // solar charging specs/manuals.
    static final float REFERENCE_LUX_AT_100_PERCENT = 50000f;

    // Skip integrating across gaps larger than this (e.g. a sync boundary) so a single
    // sample after a long silence doesn't contribute a wildly overstated lux-hours amount.
    static final long MAX_INTEGRATION_GAP_MILLIS = 5 * 60 * 1000L;

    private SolarChargingStats() {
    }

    static double computeLuxHours(final List<? extends SolarChargeSample> samples) {
        double totalLuxHours = 0;
        for (int i = 0; i + 1 < samples.size(); i++) {
            final SolarChargeSample sample = samples.get(i);
            final long gapMillis = samples.get(i + 1).getTimestamp() - sample.getTimestamp();
            if (gapMillis > 0 && gapMillis <= MAX_INTEGRATION_GAP_MILLIS) {
                final double lux = (sample.getPercent() / 100.0) * REFERENCE_LUX_AT_100_PERCENT;
                final double gapHours = gapMillis / 3600000.0;
                totalLuxHours += lux * gapHours;
            }
        }
        return totalLuxHours;
    }

    static long computeGainMillis(final List<? extends SolarChargeSample> samples) {
        long total = 0;
        for (final SolarChargeSample sample : samples) {
            total += sample.getGain();
        }
        return total;
    }
}
