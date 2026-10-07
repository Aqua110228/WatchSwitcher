/*  Copyright (C) 2026 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.test.activity;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;

/**
 * Builds the summary data of a recorded activity from its start time, end time and kind. The
 * values of each activity stay the same between two runs.
 */
public class TestActivitySummaryParser implements ActivitySummaryParser {
    @Override
    public BaseActivitySummary parseBinaryData(final BaseActivitySummary summary, final boolean forDetails) {
        summary.setSummaryData(buildSummaryData(summary).toJson());
        return summary;
    }

    public static ActivitySummaryData buildSummaryData(final BaseActivitySummary summary) {
        final ActivityKind activityKind = ActivityKind.fromCode(summary.getActivityKind());
        final long startTime = summary.getStartTime().getTime();
        final int durationSeconds = (int) ((summary.getEndTime().getTime() - startTime) / 1000L);

        final ActivitySummaryData data = new ActivitySummaryData();

        data.add(ActivitySummaryEntries.ACTIVE_SECONDS, durationSeconds, ActivitySummaryEntries.UNIT_SECONDS);

        final int hrAvg = TestDeviceRand.randInt(startTime, 110, 160);
        data.add(ActivitySummaryEntries.HR_AVG, hrAvg, ActivitySummaryEntries.UNIT_BPM);
        data.add(ActivitySummaryEntries.HR_MAX, hrAvg + TestDeviceRand.randInt(startTime + 1, 5, 30), ActivitySummaryEntries.UNIT_BPM);
        data.add(ActivitySummaryEntries.HR_MIN, hrAvg - TestDeviceRand.randInt(startTime + 2, 20, 45), ActivitySummaryEntries.UNIT_BPM);

        data.add(ActivitySummaryEntries.CALORIES_BURNT, Math.round(durationSeconds * TestDeviceRand.randFloat(startTime, 0.08f, 0.2f)), ActivitySummaryEntries.UNIT_KCAL);
        data.add(ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC, TestDeviceRand.randFloat(startTime, 1f, 5f), ActivitySummaryEntries.UNIT_NONE);
        data.add(ActivitySummaryEntries.TRAINING_EFFECT_ANAEROBIC, TestDeviceRand.randFloat(startTime + 1, 0f, 3f), ActivitySummaryEntries.UNIT_NONE);
        data.add(ActivitySummaryEntries.RECOVERY_TIME, TestDeviceRand.randInt(startTime, 4, 48) * 3600, ActivitySummaryEntries.UNIT_SECONDS);
        data.add(ActivitySummaryEntries.STRESS_AVG, TestDeviceRand.randInt(startTime, 20, 80), ActivitySummaryEntries.UNIT_NONE);
        data.add(ActivitySummaryEntries.SPO2_AVG, TestDeviceRand.randInt(startTime, 92, 99), ActivitySummaryEntries.UNIT_PERCENTAGE);
        data.add(ActivitySummaryEntries.RESPIRATION_AVG, TestDeviceRand.randInt(startTime, 15, 30), ActivitySummaryEntries.UNIT_BREATHS_PER_MIN);
        data.add(ActivitySummaryEntries.TEMPERATURE_AVG, TestDeviceRand.randFloat(startTime, 10f, 30f), ActivitySummaryEntries.UNIT_CELSIUS);

        if (hasDistance(activityKind)) {
            final float speed = averageSpeed(activityKind, startTime);
            final int distance = Math.round(speed * durationSeconds);

            data.add(ActivitySummaryEntries.DISTANCE_METERS, distance, ActivitySummaryEntries.UNIT_METERS);
            data.add(ActivitySummaryEntries.SPEED_AVG, speed, ActivitySummaryEntries.UNIT_METERS_PER_SECOND);
            data.add(ActivitySummaryEntries.SPEED_MAX, speed * TestDeviceRand.randFloat(startTime, 1.1f, 1.5f), ActivitySummaryEntries.UNIT_METERS_PER_SECOND);
            data.add(ActivitySummaryEntries.PACE_AVG_SECONDS_KM, Math.round(1000 / speed), ActivitySummaryEntries.UNIT_SECONDS_PER_KM);

            if (hasGps(activityKind)) {
                data.setHasGps(true);
                final int ascent = TestDeviceRand.randInt(startTime, 10, 300);
                data.add(ActivitySummaryEntries.TOTAL_ASCENT, ascent, ActivitySummaryEntries.UNIT_METERS);
                data.add(ActivitySummaryEntries.TOTAL_DESCENT, ascent + TestDeviceRand.randInt(startTime + 1, -20, 20), ActivitySummaryEntries.UNIT_METERS);
                data.add(ActivitySummaryEntries.ALTITUDE_MIN, TestDeviceRand.randInt(startTime, 0, 100), ActivitySummaryEntries.UNIT_METERS);
                data.add(ActivitySummaryEntries.ALTITUDE_MAX, TestDeviceRand.randInt(startTime + 1, 100, 400), ActivitySummaryEntries.UNIT_METERS);
            }
        }

        switch (ActivityKind.getCycleUnit(activityKind)) {
            case STEPS:
                final int steps = Math.round(durationSeconds * TestDeviceRand.randFloat(startTime, 1.2f, 3f));
                data.add(ActivitySummaryEntries.STEPS, steps, ActivitySummaryEntries.UNIT_STEPS);
                data.add(ActivitySummaryEntries.CADENCE_AVG, Math.round(steps / (durationSeconds / 60f)), ActivitySummaryEntries.UNIT_SPM);
                break;
            case STROKES:
                final int strokes = Math.round(durationSeconds * TestDeviceRand.randFloat(startTime, 0.4f, 0.8f));
                data.add(ActivitySummaryEntries.STROKES, strokes, ActivitySummaryEntries.UNIT_STROKES);
                data.add(ActivitySummaryEntries.STROKE_RATE_AVG, Math.round(strokes / (durationSeconds / 60f)), ActivitySummaryEntries.UNIT_STROKES_PER_MINUTE);
                data.add(ActivitySummaryEntries.SWOLF_AVG, TestDeviceRand.randInt(startTime, 30, 60), ActivitySummaryEntries.UNIT_NONE);
                break;
            case JUMPS:
                final int jumps = Math.round(durationSeconds * TestDeviceRand.randFloat(startTime, 1f, 2.5f));
                data.add(ActivitySummaryEntries.JUMPS, jumps, ActivitySummaryEntries.UNIT_JUMPS);
                data.add(ActivitySummaryEntries.JUMP_RATE_AVG, Math.round(jumps / (durationSeconds / 60f)), ActivitySummaryEntries.UNIT_JUMPS_PER_MINUTE);
                break;
            case REPS:
                data.add(ActivitySummaryEntries.SETS, TestDeviceRand.randInt(startTime, 3, 8), ActivitySummaryEntries.UNIT_NONE);
                data.add(ActivitySummaryEntries.REPETITIONS, TestDeviceRand.randInt(startTime, 20, 100), ActivitySummaryEntries.UNIT_REPS);
                break;
            case REVOLUTIONS:
                data.add(ActivitySummaryEntries.REVOLUTIONS, TestDeviceRand.randInt(startTime, 200, 2000), ActivitySummaryEntries.UNIT_REVS);
                break;
            default:
                break;
        }

        if (ActivityKind.isSwimActivity(activityKind)) {
            data.add(ActivitySummaryEntries.LAPS, TestDeviceRand.randInt(startTime, 4, 40), ActivitySummaryEntries.UNIT_LAPS);
            data.add(ActivitySummaryEntries.LANE_LENGTH, 25, ActivitySummaryEntries.UNIT_METERS);
        }

        return data;
    }

    /**
     * Whether the activity moves over a distance.
     */
    public static boolean hasDistance(final ActivityKind activityKind) {
        if (ActivityKind.isDiving(activityKind)) {
            return false;
        }

        return ActivityKind.isPaceActivity(activityKind)
            || ActivityKind.isRowingActivity(activityKind)
            || ActivityKind.isNauticalActivity(activityKind)
            || ActivityKind.getCycleUnit(activityKind) == ActivityKind.CycleUnit.NONE
            || activityKind == ActivityKind.HIKING
            || activityKind == ActivityKind.CLIMBING
            || activityKind == ActivityKind.SKIING
            || activityKind == ActivityKind.SNOWBOARDING;
    }

    /**
     * Whether the activity is outdoors, and therefore has a track.
     */
    public static boolean hasGps(final ActivityKind activityKind) {
        if (!hasDistance(activityKind)) {
            return false;
        }

        final String name = activityKind.name();
        return !name.contains("INDOOR")
            && !name.contains("TREADMILL")
            && !name.contains("MACHINE")
            && !name.contains("POOL");
    }

    /**
     * The average speed of the activity, in meters per second.
     */
    public static float averageSpeed(final ActivityKind activityKind, final long startTime) {
        if (ActivityKind.isSwimActivity(activityKind)) {
            return TestDeviceRand.randFloat(startTime, 0.8f, 1.4f);
        }
        if (ActivityKind.getCycleUnit(activityKind) == ActivityKind.CycleUnit.NONE) {
            return TestDeviceRand.randFloat(startTime, 4f, 9f);
        }
        if (ActivityKind.isRowingActivity(activityKind) || ActivityKind.isNauticalActivity(activityKind)) {
            return TestDeviceRand.randFloat(startTime, 1.5f, 3.5f);
        }
        return TestDeviceRand.randFloat(startTime, 1.2f, 3.5f);
    }
}
