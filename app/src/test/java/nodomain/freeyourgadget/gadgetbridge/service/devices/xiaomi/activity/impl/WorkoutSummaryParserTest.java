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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums;

/**
 * Tests for {@link WorkoutSummaryParser} using captured device data. Each base64 blob
 * is the verbatim contents of a SUMMARY .bin file pulled from a real workout, so the
 * tests pin the parser against actual byte layouts rather than fabricated examples.
 */
public class WorkoutSummaryParserTest {

    /** Treadmill v11, 24 Apr 2026, 4.48 km run (calibrated to 4.5 km), goal 4 km. */
    private static final String TREADMILL_V11_24APR =
            "ErHraQgLjQD7/+/vkf/AAD8SsetpMLjraRgHAAB/EQAAiQCVAQAAWAEAABMDAAAnEwAAWACj"
                    + "AKgAp7NeZmaGQAAAAAAoAOkBAADHBAAAPQAAAA4AAAAQAAAAugAXBwAAZmbmPwADAAAA"
                    + "AAAAAAAAoA8AAAAAAAAAAAAAAACUEQAAaAADAAAAAAAAAAAAAAAAAAAMAAAAAAAAAAAA"
                    + "AAAAAAAAAAAAAAAAAAAAAAAAAADyuEua";

    /** Treadmill v9, 21 Jun 2026 — 192 cal, 135 avg HR, 28 training load, +15 vitality. */
    private static final String TREADMILL_V9_21JUN =
            "WCA4aggJjQD//H+QDiBYIDhqhyY4aigGAACgCgAAwAByAQAAlAUAAJAMAAC0AIelZDMz8z8C"
                    + "AAAAIAAAAAAAcAAAAPwCAAB0AQAAOgEAAOgAKAYAAAAAAAABAwAAAFgCAAAsAYgTAABK"
                    + "AQAAAADIQQAAoAoAABwAAQAAAAAAAA8AAAAAAAAAAAAAAAAAAwAAh6VkA1B6ALQAhdLG"
                    + "QAisHEFYIDhqhyY4agAAAAAoBgAAKAYAAAAAAAAAAAAAwAAAAKAKAACQDAAAQwIAAHIB"
                    + "AAAAAAAAAAAAAAAAAAADAAAAAAAAAAAAAAAAAAAAAAAAAHAAAAD8AgAAdAEAADoBAAAA"
                    + "AAIBMzPzPwAAAAAAAAAADwAcACAAAAAAAAAAAAAAAAAAAAAAAaiMNkSR";

    /** Rowing v7, 20 Apr 2026 — 0.4 anaerobic effect, 71 max stroke rate. */
    private static final String ROWING_V7_20APR =
            "+l3maQgHtQD/v/N4//pd5mnwZ+Zp9QkAANYAjqRcmplZQAAAFwAAAAAAoQIAAGoFAACwAAA"
                    + "AFAEAABsBuAQAABwAAABHAAAAAAAAAAAAAABnAQAABfUJAADNzMw+AAAAAAAAAAAAAABF"
                    + "AAIAAJGg1DA=";

    /** Rowing v7, 15 Apr 2026 — 1.1 anaerobic effect. */
    private static final String ROWING_V7_15APR =
            "8srfaQgHtQD/v/N4//LK32nQ1N9p3QkAANQAkapfAABgQAAAHAAAAAAAGwUAAKwCAAD+AAA"
                    + "AAwEAABkBTAQAABoAAAApAAAAAAAAAAAAAAAAAAAAAd0JAADNzIw/AAAAAAAAAAAAAABN"
                    + "AAMAAAusxH0=";

    /** Rowing v8, 22 Aug 2026 — 46 strokes at 18 average, 27 maximum. */
    private static final String ROWING_V8_22AUG =
            "JaCJaggItQD/v/+b8X8loIlquaCJapMAAAAOAIGRapqZmT4BAAAAAAAAAAAAAAAzAAAAOwA"
                    + "AACUAAAAAAAAAABMALgAAABIAAAAbAAAAAAAAAAAAAAAAAAAAAZMAAAAAAAAAAQAAAAAAAA"
                    + "AAAAAAAAAAAAIAAAAAAAB+WRxL";

    /** Freestyle v10, 17 Mar 2026 — frisbee workout, 105/93/7 throws low/medium/high. */
    private static final String FREESTYLE_V10_FRISBEE =
            "HIK5aQQKoQD+Bv7/wHccgrlpcJO5aVMRAABwAZG8WgAAAAAAAAAAgEAAACMAWgEAAEIEAAA"
                    + "FBwAAtAMAANUAAADoAVMRAABmZiZAACcDAAAAAAAAAACIAAMAAAAAAAAAAAAAAAAAAAAA"
                    + "aQBdAAcAAEblwNo=";

    private static ActivitySummaryData parse(final String base64) {
        final byte[] bytes = Base64.getDecoder().decode(base64);
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setRawSummaryData(bytes);
        new WorkoutSummaryParser().parseBinaryData(summary, true);
        final String json = summary.getSummaryData();
        assertNotNull("parser should populate summaryData", json);
        return ActivitySummaryData.fromJson(json);
    }

    private static double num(final ActivitySummaryData data, final String key) {
        final Number n = data.getNumber(key, null);
        assertNotNull("missing entry: " + key, n);
        return n.doubleValue();
    }

    @Test
    public void treadmillV11_extractsRecoveryDistanceGoalCalibratedLoadVitality() {
        final ActivitySummaryData data = parse(TREADMILL_V11_24APR);

        // Pre-existing fields (baseline sanity check)
        assertEquals(4479d, num(data, ActivitySummaryEntries.DISTANCE_METERS), 0.001);
        assertEquals(4.2d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC), 0.01);
        assertEquals(167d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
        assertEquals(163d, num(data, ActivitySummaryEntries.CADENCE_AVG), 0.001);

        // New trailing-zone fields verified against the device UI.
        // The float at the prior "recoveryValue" offset is the anaerobic training effect.
        assertEquals(1.8d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_ANAEROBIC), 0.01);
        assertEquals(4000d, num(data, ActivitySummaryEntries.DISTANCE_GOAL), 0.001);
        assertEquals(4500d, num(data, ActivitySummaryEntries.DISTANCE_METERS_CALIBRATED), 0.001);
        assertEquals(104d, num(data, ActivitySummaryEntries.WORKOUT_LOAD), 0.001);
        assertEquals(12d, num(data, ActivitySummaryEntries.VITALITY_GAIN), 0.001);
    }

    @Test
    public void treadmillV9_extractsHrStepsRecoveryLoadVitality() {
        final ActivitySummaryData data = parse(TREADMILL_V9_21JUN);

        // All values verified against the on-watch UI for this workout.
        assertEquals(192d, num(data, ActivitySummaryEntries.CALORIES_BURNT), 0.001);
        assertEquals(2720d, num(data, ActivitySummaryEntries.DISTANCE_METERS), 0.001);
        assertEquals(3216d, num(data, ActivitySummaryEntries.STEPS), 0.001);
        assertEquals(135d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
        assertEquals(180d, num(data, ActivitySummaryEntries.CADENCE_MAX), 0.001);
        assertEquals(1.9d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC), 0.01);
        assertEquals(32d, num(data, ActivitySummaryEntries.RECOVERY_TIME), 0.001);
        assertEquals(28d, num(data, ActivitySummaryEntries.WORKOUT_LOAD), 0.001);
        assertEquals(15d, num(data, ActivitySummaryEntries.VITALITY_GAIN), 0.001);
    }

    @Test
    public void rowingV7_extractsAerobicAnaerobicStrokeMaxLoad() {
        final ActivitySummaryData data = parse(ROWING_V7_20APR);

        // Sanity checks
        assertEquals(142d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
        assertEquals(2549d, num(data, ActivitySummaryEntries.ACTIVE_SECONDS), 0.001);
        assertEquals(1208d, num(data, ActivitySummaryEntries.STROKES), 0.001);
        assertEquals(28d, num(data, ActivitySummaryEntries.STROKE_RATE_AVG), 0.001);

        // New fields verified against the device UI
        assertEquals(3.4d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC), 0.01);
        assertEquals(0.4d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_ANAEROBIC), 0.01);
        assertEquals(71d, num(data, ActivitySummaryEntries.STROKE_RATE_MAX), 0.001);
        assertEquals(23d, num(data, ActivitySummaryEntries.RECOVERY_TIME), 0.001);
        assertEquals(69d, num(data, ActivitySummaryEntries.WORKOUT_LOAD), 0.001);
        // Vitality_gain = 0 in this workout; XiaomiSimpleActivityParser force-displays it.
        assertEquals(0d, num(data, ActivitySummaryEntries.VITALITY_GAIN), 0.001);
    }

    @Test
    public void rowingV7_15Apr_extractsAnaerobic() {
        final ActivitySummaryData data = parse(ROWING_V7_15APR);

        assertEquals(1.1d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_ANAEROBIC), 0.01);
        assertEquals(3.5d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC), 0.01);
        assertEquals(41d, num(data, ActivitySummaryEntries.STROKE_RATE_MAX), 0.001);
    }

    @Test
    public void freestyleV10_extractsFrisbeeThrows() {
        final ActivitySummaryData data = parse(FREESTYLE_V10_FRISBEE);

        // Sanity checks
        assertEquals(2.6d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_ANAEROBIC), 0.01);
        assertEquals(4.0d, num(data, ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC), 0.01);

        // New frisbee throw-force buckets
        assertEquals(105d, num(data, ActivitySummaryEntries.THROWS_LOW), 0.001);
        assertEquals(93d, num(data, ActivitySummaryEntries.THROWS_MEDIUM), 0.001);
        assertEquals(7d, num(data, ActivitySummaryEntries.THROWS_HIGH), 0.001);

        // ActivityKind override via XIAOMI_WORKOUT_TYPE = 807 → FRISBEE
        // (sample's BaseActivitySummary doesn't expose summary.getActivityKind() through
        // ActivitySummaryData; the override is verified indirectly by the workout-type
        // mapping in XiaomiWorkoutType.fromCode). Throws-force values are the parser-level
        // assertion.
    }

    /**
     * Rowing v8 inserts five bytes after the heart rate zones compared to v7. Without that
     * padding the stroke fields decode to nonsense, so assert the ones that can be checked
     * against each other: the zones must add up to the active seconds, and the stroke count
     * must be consistent with the average rate over that time.
     */
    @Test
    public void rowingV8_extractsStrokesAndZones() {
        final ActivitySummaryData data = parse(ROWING_V8_22AUG);

        assertEquals(147, num(data, ActivitySummaryEntries.ACTIVE_SECONDS), 0.001);
        assertEquals(129, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
        assertEquals(145, num(data, ActivitySummaryEntries.HR_MAX), 0.001);
        assertEquals(106, num(data, ActivitySummaryEntries.HR_MIN), 0.001);

        assertEquals(46, num(data, ActivitySummaryEntries.STROKES), 0.001);
        assertEquals(18, num(data, ActivitySummaryEntries.STROKE_RATE_AVG), 0.001);
        assertEquals(27, num(data, ActivitySummaryEntries.STROKE_RATE_MAX), 0.001);

        final double zones = num(data, ActivitySummaryEntries.HR_ZONE_EXTREME)
                + num(data, ActivitySummaryEntries.HR_ZONE_ANAEROBIC)
                + num(data, ActivitySummaryEntries.HR_ZONE_AEROBIC)
                + num(data, ActivitySummaryEntries.HR_ZONE_FAT_BURN)
                + num(data, ActivitySummaryEntries.HR_ZONE_WARM_UP);
        assertEquals("heart rate zones should add up to the active seconds",
                num(data, ActivitySummaryEntries.ACTIVE_SECONDS), zones, 0.001);
    }

    /** Outdoor cycling v8 (Smart Band 10 Pro), 23 Sep 2026, 5.28 km ride, no route. */
    private static final String OUTDOOR_CYCLING_V8_23SEP =
            "doGzaggI3QD3/n/X/4chPwYAdoGzaqGHs2orBgAAKwYAAKIUAACmAH8ATAAAAAwEAABRrkBBnbQ7QoWo"
                    + "UAAAAAAAAAAAAAAAAAAAAAAAAAAAMzMTQADNzEw/AAAAAAAAAAAADQAAAAAAAF8AAAAuAwAAVAEA"
                    + "ABsBAAAAAAAAAAAAAAAAAAAAAAAAAAAAABwAAAAAAAAAAQAAAAAAAAAABwAAAAAAAAAAy+id1Q==";

    @Test
    public void outdoorCyclingV8_extractsStatsZonesLoadVitality() {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setRawSummaryData(Base64.getDecoder().decode(OUTDOOR_CYCLING_V8_23SEP));
        new WorkoutSummaryParser().parseBinaryData(summary, true);
        assertEquals(ActivityKind.OUTDOOR_CYCLING.getCode(), summary.getActivityKind());

        final ActivitySummaryData data = ActivitySummaryData.fromJson(summary.getSummaryData());
        assertEquals(1579, num(data, ActivitySummaryEntries.ACTIVE_SECONDS), 0.001);
        assertEquals(5282, num(data, ActivitySummaryEntries.DISTANCE_METERS), 0.001);
        assertEquals(166, num(data, ActivitySummaryEntries.CALORIES_TOTAL), 0.001);
        assertEquals(127, num(data, ActivitySummaryEntries.CALORIES_BURNT), 0.001);
        assertEquals(76, num(data, ActivitySummaryEntries.PACE_MAX), 0.001);
        assertEquals(1036, num(data, ActivitySummaryEntries.PACE_MIN), 0.001);
        // Average speed must agree with distance over time: 5282 m / 1579 s = 12.04 km/h
        assertEquals(12.04, num(data, ActivitySummaryEntries.SPEED_AVG), 0.01);
        assertEquals(46.93, num(data, ActivitySummaryEntries.SPEED_MAX), 0.01);
        assertEquals(133, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
        assertEquals(168, num(data, ActivitySummaryEntries.HR_MAX), 0.001);
        assertEquals(80, num(data, ActivitySummaryEntries.HR_MIN), 0.001);
        assertEquals(2.3, num(data, ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC), 0.01);
        assertEquals(0.8, num(data, ActivitySummaryEntries.TRAINING_EFFECT_ANAEROBIC), 0.01);
        assertEquals(13, num(data, ActivitySummaryEntries.RECOVERY_TIME), 0.001);
        assertEquals(95, num(data, ActivitySummaryEntries.HR_ZONE_ANAEROBIC), 0.001);
        assertEquals(814, num(data, ActivitySummaryEntries.HR_ZONE_AEROBIC), 0.001);
        assertEquals(340, num(data, ActivitySummaryEntries.HR_ZONE_FAT_BURN), 0.001);
        assertEquals(283, num(data, ActivitySummaryEntries.HR_ZONE_WARM_UP), 0.001);
        assertEquals(28, num(data, ActivitySummaryEntries.WORKOUT_LOAD), 0.001);
        assertEquals(7, num(data, ActivitySummaryEntries.VITALITY_GAIN), 0.001);
    }

    /** Outdoor cycling v6 (Smart Band 10), 23 May 2026, 11.98 km ride, no route. */
    private static final String OUTDOOR_CYCLING_V6_23MAY =
            "V7kRaggG3QD3//9fw5CPBgBXuRFqJsoRas8QAADPEAAAzi4AABoCtwFoAAAApCYAACNkIEG56wlC"
                    + "W41IAAAAAAAAAAAAAAAAAAAAAAAAAACamRk/AAAAAAAAAAAAAAAAAAAAAAAAAAAKAAAAYQAAAFYE"
                    + "AAAAAAAAAAAAAAAAAAAAAAAFAAEAAAAAAAAAAAAAAAAAAAAAAAAAANY0wh8=";

    @Test
    public void outdoorCyclingV6_extractsCaloriesPaceAndLoad() {
        final ActivitySummaryData data = parse(OUTDOOR_CYCLING_V6_23MAY);

        assertEquals(11982, num(data, ActivitySummaryEntries.DISTANCE_METERS), 0.001);
        assertEquals(538, num(data, ActivitySummaryEntries.CALORIES_TOTAL), 0.001);
        assertEquals(439, num(data, ActivitySummaryEntries.CALORIES_BURNT), 0.001);
        // Fastest pace 104 s/km is 34.6 km/h, the maximum speed of the ride
        assertEquals(104, num(data, ActivitySummaryEntries.PACE_MAX), 0.001);
        assertEquals(34.48, num(data, ActivitySummaryEntries.SPEED_MAX), 0.01);
        assertEquals(10, num(data, ActivitySummaryEntries.HR_ZONE_AEROBIC), 0.001);
        assertEquals(97, num(data, ActivitySummaryEntries.HR_ZONE_FAT_BURN), 0.001);
        assertEquals(1110, num(data, ActivitySummaryEntries.HR_ZONE_WARM_UP), 0.001);
        assertEquals(5, num(data, ActivitySummaryEntries.WORKOUT_LOAD), 0.001);
    }

    /** The route byte sits at offset 0x8D of a v8 cycling summary; a route ride carries
     *  8 more bytes after it. */
    @Test
    public void outdoorCyclingV8_routeRide_keepsLoadAndVitalityAligned() {
        final byte[] plain = Base64.getDecoder().decode(OUTDOOR_CYCLING_V8_23SEP);
        final int routeOffset = 0x8D;
        final byte[] route = new byte[plain.length + 8];
        System.arraycopy(plain, 0, route, 0, routeOffset + 1);
        route[routeOffset] = (byte) 0xFF;
        Arrays.fill(route, routeOffset + 1, routeOffset + 9, (byte) 0x5A);
        System.arraycopy(plain, routeOffset + 1, route, routeOffset + 9, plain.length - routeOffset - 1);
        final ByteBuffer crc = ByteBuffer.wrap(route).order(ByteOrder.LITTLE_ENDIAN);
        crc.putInt(route.length - 4, CheckSums.getCRC32(route, 0, route.length - 4));

        final ActivitySummaryData data = parse(Base64.getEncoder().encodeToString(route));
        assertEquals(28, num(data, ActivitySummaryEntries.WORKOUT_LOAD), 0.001);
        assertEquals(7, num(data, ActivitySummaryEntries.VITALITY_GAIN), 0.001);
    }

    @Test
    public void outdoorCyclingUnsupportedVersion_isStillCycling() {
        final byte[] bytes = Base64.getDecoder().decode(OUTDOOR_CYCLING_V8_23SEP);
        bytes[5] = 99; // fileId version byte
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setRawSummaryData(bytes);
        new WorkoutSummaryParser().parseBinaryData(summary, true);
        assertEquals(ActivityKind.OUTDOOR_CYCLING.getCode(), summary.getActivityKind());
    }

    /** Outdoor run v5, Mi Band 9 Active, 30 Sep 2026 (#6875). The paired DETAILS file has 1704
     *  records whose HR spans 119-200 (mean 162), pace 395-782 s/km and cadence up to 178. */
    private static final String OUTDOOR_RUN_V5_30SEP =
            "wpe9avQF2QD///////8AAAABAMKXvWprnr1qqAYAAKgGAABdEAAACwHkAJYBAACLAQAADgMA"
                    + "AJmZDUEbkhFBThAAAGQAkgCyAKLIdwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    + "AAAAAB8CAABEAQAAOQIAAAUBAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADqDW67";

    @Test
    public void outdoorRunV5_matchesDetails() {
        final byte[] bytes = Base64.getDecoder().decode(OUTDOOR_RUN_V5_30SEP);
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setRawSummaryData(bytes);
        new WorkoutSummaryParser().parseBinaryData(summary, true);
        assertEquals(ActivityKind.OUTDOOR_RUNNING.getCode(), summary.getActivityKind());

        final ActivitySummaryData data = parse(OUTDOOR_RUN_V5_30SEP);
        assertEquals(1704d, num(data, ActivitySummaryEntries.ACTIVE_SECONDS), 0.001);
        assertEquals(4189d, num(data, ActivitySummaryEntries.DISTANCE_METERS), 0.001);
        assertEquals(119d, num(data, ActivitySummaryEntries.HR_MIN), 0.001);
        assertEquals(200d, num(data, ActivitySummaryEntries.HR_MAX), 0.001);
        assertEquals(162d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
        assertEquals(395d, num(data, ActivitySummaryEntries.PACE_MAX), 0.001);
        assertEquals(782d, num(data, ActivitySummaryEntries.PACE_MIN), 0.001);
        assertEquals(178d, num(data, ActivitySummaryEntries.STEP_RATE_MAX), 0.001);
    }

    /** Walk v6, 29 May 2026. The paired DETAILS file has 1912 records whose HR spans 131-192
     *  (mean 149.8). */
    private static final String WALKING_V6_29MAY =
            "lrAZaggG2QD3///4/gf/AAACAJawGWoPuBlqeAcAAHgHAABzDQAAHQHpACsCAAB7AQAAdAMA"
                    + "AMFxz0B2phdB9g4AAE8AfQCtAJXAgwAAAAAAAAAAAAAAAAAAAAAAAAAAAABAQAAzM/M/AAAA"
                    + "ABMAAHEAAAAYAgAAagQAAHMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPQACAAAAAAAA"
                    + "AAAAAAAAAAAbAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUDnru";

    @Test
    public void walkingV6_matchesDetails() {
        final ActivitySummaryData data = parse(WALKING_V6_29MAY);
        assertEquals(1912d, num(data, ActivitySummaryEntries.ACTIVE_SECONDS), 0.001);
        assertEquals(131d, num(data, ActivitySummaryEntries.HR_MIN), 0.001);
        assertEquals(192d, num(data, ActivitySummaryEntries.HR_MAX), 0.001);
        assertEquals(149d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
    }

    /** Walk v9, 4 Jan 2026. The paired DETAILS file has HR spanning 99-163
     *  (mean 135.5). */
    private static final String WALKING_V9_04JAN =
            "glJaaQQJ2QD3///4P4AAf/AAAAAfAgCDUlppIVhaaZ0FAACdBQAACAoAAM8ArwAjAgAA1gEA"
                    + "AFYDAABv3s1ANA31QKYLAABVAH8AhwCHo2MAAAAAAAAAAAAAAAAAAAAAAAAAAGZmJkAAAAAA"
                    + "AAAAAAAAAAAAABAAAAAAAADVAAAAbQIAAO8BAABQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABwAAQAAAAAAAAAAAAAAAAAACgAAAAAAAAAAAAAA"
                    + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    + "AAAAAAALE479";

    @Test
    public void walkingV9_matchesDetails() {
        final ActivitySummaryData data = parse(WALKING_V9_04JAN);
        assertEquals(99d, num(data, ActivitySummaryEntries.HR_MIN), 0.001);
        assertEquals(163d, num(data, ActivitySummaryEntries.HR_MAX), 0.001);
        assertEquals(135d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
    }

    /** Indoor cycling v9, 22 Feb 2025. The paired DETAILS file has HR spanning 94-150
     *  (mean 135.6). */
    private static final String INDOOR_CYCLING_V9_22FEB =
            "UB+6ZwQJnQD/7++f/kOAf1Afume6KLpnagkAAAAAAACIAgAAAACHll6amTlAAABIAAAAAAAA"
                    + "AAAAbQQAAMQEAAAjAAAARwNqCQAAAAAAAAAAAAgHAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    + "AAAAAAAAAAAAAFUAAgAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    + "AAAAAAAAAAAAGaZY0A==";

    @Test
    public void indoorCyclingV9_matchesDetails() {
        final ActivitySummaryData data = parse(INDOOR_CYCLING_V9_22FEB);
        assertEquals(94d, num(data, ActivitySummaryEntries.HR_MIN), 0.001);
        assertEquals(150d, num(data, ActivitySummaryEntries.HR_MAX), 0.001);
        assertEquals(135d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
    }

    /** Elliptical v6, 10 Feb 2025. The paired DETAILS file has HR spanning 118-156
     *  (mean 147.6). */
    private static final String ELLIPTICAL_V6_10FEB =
            "6FuqZwQGrQD/9/+P6FuqZ+tiqmcDBwAApgJoAQAADACuAJOcdgAAYEAAAEgAAAAAABUAAAC8"
                    + "BgAAHQAAAAAAAAAGAwMHAACamZk+AAAAAAAAAAAAAABgAAIDAC12aDs=";

    @Test
    public void ellipticalV6_matchesDetails() {
        final ActivitySummaryData data = parse(ELLIPTICAL_V6_10FEB);
        assertEquals(118d, num(data, ActivitySummaryEntries.HR_MIN), 0.001);
        assertEquals(156d, num(data, ActivitySummaryEntries.HR_MAX), 0.001);
        assertEquals(147d, num(data, ActivitySummaryEntries.HR_AVG), 0.001);
    }
}
