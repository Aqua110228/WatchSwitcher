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

public class DefaultHeartRateZones extends HeartRateZones {

    private boolean reportedByDevice;

    public DefaultHeartRateZones(CalculationMethod method, int HRThreshold) {
        super(method, HRThreshold);
    }

    /**
     * Builds zones straight from explicit per-zone lower-bound thresholds (bpm), bypassing the
     * MHR/HRR/LTHR formula. Used when a device reports the user's configured zones for a workout
     * (e.g. Garmin FIT, Zepp OS), so the displayed bands match the device instead of a 220−age estimate.
     */
    public static DefaultHeartRateZones fromBoundaries(int zone1, int zone2, int zone3, int zone4, int zone5) {
        final DefaultHeartRateZones zones = new DefaultHeartRateZones(CalculationMethod.MHR, zone5);
        zones.setZone1(zone1);
        zones.setZone2(zone2);
        zones.setZone3(zone3);
        zones.setZone4(zone4);
        zones.setZone5(zone5);
        zones.setHRThreshold(zone5);
        zones.reportedByDevice = true;
        return zones;
    }

    /**
     * True when the thresholds came verbatim from the device, so {@link #getMethod()} describes how
     * the zones are evaluated here, not how the device arrived at them.
     */
    public boolean isReportedByDevice() {
        return reportedByDevice;
    }

    @Override
    public void reset() {
        switch (this.method) {
            case MHR -> calculateMHRZonesConfig();
            case HRR -> defaultHRRZonesConfig();
            case LTHR -> defaultLTHRZonesConfig();
        }
    }

    @Override
    public long getPercentage(int zone) {
        return switch (this.method) {
            case MHR, LTHR -> Math.round((float) zone * 100.0 / (float) HRThreshold);
            case HRR -> Math.round((float) (zone - HRResting) * 100.0 / (float) (HRThreshold - HRResting));
        };
    }

    private void calculateMHRZonesConfig() {
        zone5 = Math.round(((float) (HRThreshold * 90)) / 100.0f);
        zone4 = Math.round(((float) (HRThreshold * 80)) / 100.0f);
        zone3 = Math.round(((float) (HRThreshold * 70)) / 100.0f);
        zone2 = Math.round(((float) (HRThreshold * 60)) / 100.0f);
        zone1 = Math.round(((float) (HRThreshold * 50)) / 100.0f);
    }

    private void defaultHRRZonesConfig() {
        int calcHR = HRThreshold - HRResting;
        zone5 = Math.round(((float) (calcHR * 95)) / 100.0f) + HRResting;
        zone4 = Math.round(((float) (calcHR * 88)) / 100.0f) + HRResting;
        zone3 = Math.round(((float) (calcHR * 84)) / 100.0f) + HRResting;
        zone2 = Math.round(((float) (calcHR * 74)) / 100.0f) + HRResting;
        zone1 = Math.round(((float) (calcHR * 59)) / 100.0f) + HRResting;
    }

    private void defaultLTHRZonesConfig() {
        HRThreshold = Math.round(((float) HRResting) + (((float) ((HRThreshold - HRResting) * 85)) / 100.0f));
        zone5 = Math.round(((float) (HRThreshold * 102)) / 100.0f);
        zone4 = Math.round(((float) (HRThreshold * 97)) / 100.0f);
        zone3 = Math.round(((float) (HRThreshold * 89)) / 100.0f);
        zone2 = Math.round(((float) (HRThreshold * 80)) / 100.0f);
        zone1 = Math.round(((float) (HRThreshold * 67)) / 100.0f);
    }
}
