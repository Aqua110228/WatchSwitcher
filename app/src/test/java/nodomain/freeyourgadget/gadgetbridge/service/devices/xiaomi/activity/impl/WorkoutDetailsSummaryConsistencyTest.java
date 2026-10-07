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

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.impl.WorkoutDetailsParser.WorkoutDetailRecord;
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums;

/**
 * Checks DETAILS decoding against the paired SUMMARY of the same workout, which the band
 * computes itself: summed steps, distance integrated from the per-second records, and peak
 * cadence must agree with it. A field read from the wrong byte or in the wrong unit breaks
 * these totals even when a synthetic record round-trips.
 */
public class WorkoutDetailsSummaryConsistencyTest {
    /** Relative tolerance for summed steps and integrated distance. The summary counts a few
     *  seconds the DETAILS file does not record. */
    private static final double TOTAL_TOLERANCE = 0.03;

    private static final String RAW_DIR_ENV = "GB_XIAOMI_RAW_DIR";

    /** Treadmill v6, 11 Feb 2025, 103 s at walking pace: 162 steps, 149 m, max cadence 171.
     *  Its stride byte is 35, which once passed for half the cadence. */
    @Test
    public void treadmillV6() {
        assertConsistent("treadmill v6",
                "69B9AB6704068C", "FF FB 8B FF",
                new String[]{"67 00 00 00 69 B9 AB 67 7F 95 00 00 00"},
                new String[]{
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                                + "AGcAAAAAAAAAAAAAAGsAAAAAAAAAAAAAAG4AAAAAAAAAAAAAAHEAAAAAAAAAAAAAGHQAIwAAAAAAYzcKAnYAIwAAAAAAZBwK"
                                + "AngRIwAAAAAAa1sJEnkSIwAAAAAAZeUJAnsSIwAAAAAAZeMJAnwPIwAAAAAAaXgJE34fIwAAAAAAa0EJA34gIwAAAAAAbCIJ"
                                + "An8SIwAAAAAAbBoJE38fIwAAAAAAcKcIAH8AIwAAAAAAcKcIA4AaIwAAAAAAcJcIE4AgIwAAAAAAdRYIA4IfIwAAAAAAgBAH"
                                + "A4MgIwAAAAAAhbIGE4QfIwAAAAAAilwGA4YsIwAAAAAAlL4FA4ogIwAAAAAAmXsFE4weIwAAAAAAp74EA4wfIwAAAAAAp7gE"
                                + "A40gIwAAAAAAqKkEE40aIwAAAAAAqZsEA48fIwAAAAAAqo0EApASIwAAAAAAqocEE48gIwAAAAAAqoEEA5AfIwAAAAAAqYEE"
                                + "A5AaIwAAAAAAqIEEE5EfIwAAAAAAp4EEA5EgIwAAAAAAp3sEA5IfIwAAAAAAp3YEE5MgIwAAAAAAqGgEA5MZIwAAAAAAqVsE"
                                + "E5QgIwAAAAAAqk8EA5UgIwAAAAAAqkkEAJYAIwAAAAAAqkkEE5YfIwAAAAAAqz0EA5ckIwAAAAAAqzgEE5csIwAAAAAAqzME"
                                + "A5ggIwAAAAAAqy4EA5kfIwAAAAAAqi4EE5kgIwAAAAAAqioEA5kfIwAAAAAAqx4EE5oaIwAAAAAAqh4EA5oeIwAAAAAAqhoE"
                                + "ApsSIwAAAAAAqRsEE5oeIwAAAAAAqRYEA5ofIwAAAAAAqBcEA5oaIwAAAAAApxgEEpoRIwAAAAAApCQEApoSIwAAAAAApCAE"
                                + "EpoSIwAAAAAApBsEAZoHIwAAAAAApBcEApsOIwAAAAAApBMEEpwSIwAAAAAApA8EAZsHIwAAAAAApAoEEpsRIwAAAAAApAYE"
                                + "ApsSIwAAAAAApAIEAZsHIwAAAAAApP4DEZsHIwAAAAAApPoDAZsGJAAAAAAApPYDEpoSJAAAAAAApPIDApoSJAAAAAAApO8D"
                                + "AJoAJAAAAAAApO8DEZoHJAAAAAAApOsDApoRJAAAAAAApOcDEZkHJAAAAAAApOMDAJcAJAAAAAAApOMDAJYAJAAAAAAApOMD"
                                + "EJcAJAAAAAAApOMDAJQAJAAAAAAApOMDEJMAJAAAAAAApOMDAJIAJAAAAAAApOMDAJIAJAAAAAAApOMDEJEAJAAAAAAApOMD"
                                + "AJEAJAAAAAAApOMDAJEAJAAAAAAAAAAAEJEAJAAAAAAAAAAAAI8AJAAAAAAAAAAAAJEAJAAAAAAAAAAAEJEAJAAAAAAAAAAA"
                                + "AJEAJAAAAAAAAAAAEI8AJAAAAAAAAAAAAIwAJAAAAAAAAAAAAIoAJAAAAAAAAAAAAIgAJAAAAAAAAAAAEIcAJAAAAAAAAAAA"
                                + "AIUAJAAAAAAAAAAAAIQAJAAAAAAAAAAAEIMAJAAAAAAAAAAAAIMAJAAAAAAAAAAAAIMAJAAAAAAAAAAAAIMAJAAAAAAAAAAA"
                                + "EIMAJAAAAAAAAAAA"
                },
                "abmrZwQLjQD7/4/vkf/AAH9puatn0rmrZ2cAAACVAAAAIgCzAgAA4wMAADcKAACiAAAAWwBeAKsAjJxnzcxMPwAAAABIAAAA"
                        + "AAAJAAAAOwAAABkAAAAEAAAAKABnAAAAAAAAAAADAAAAAAAAAAAAoA8AAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAA"
                        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABuV+WJ");
    }

    /** Rowing v4, 31 Jan 2025, 63 s: max stroke rate 42. */
    @Test
    public void rowingV4() {
        assertConsistent("rowing v4",
                "2ECE9C670404B4", "FF FF",
                new String[]{"3F 00 00 00 2E CE 9C 67 7F 1A 00 00 00"},
                new String[]{
                        "AAAAAAAAAAAAAAAATgAATgAATgAATgAAUwAhUwAhWgAhWgAVYAAVYAAVZAAUZAAUZgAUZgAqaAAqaQAqagEUagAUagAUbAAT"
                                + "bAATbQATbQAVbgAVbgAVbgAnbwAnbwAnbwAVbwEVbwAVbgATbgATbgATbgATbgATbgAVbQAVbQAVbQATbQATbAATbQAqbgEq"
                                + "bgAqbwATbwATbwATcAAUbwAUbwAUcAAVcAAVcQAVcQATcQATcAETcAAVcAAV"
                },
                "Ls6cZwQHtQD/v/N4/y7OnGduzpxnPwAAAAQAaXFOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMQAAAAUAGgAAABgAAAAqAAAA"
                        + "AAAAAAAAAAAAAAAAAT8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAANzUBY0=");
    }

    /** Outdoor run v5, 16 Jun 2025, 63 s: 146 steps, 155 m, max cadence 162. Its pace field
     *  adds up to 179 m, so a speed taken from pace fails the distance check. */
    @Test
    public void outdoorRunV5() {
        assertConsistent("outdoor run v5",
                "987C50680805D8", "FF CF B8 BF FF",
                new String[]{"00 00 00 00 3F 00 00 00 98 7C 50 68 7F 9B 00 00 00"},
                new String[]{
                        "AIwAAAAAAAAAAAAAAACMAAAAAAAAAAAAAAAAjQAAAAAAAAAAAAAAAIsAAAAAAAAAAAAAAACKAAAAAAAAAAAAAAAAiwAAAAAA"
                                + "AAAAAAAAGIsAPQAAAAAAAAAAAAKLABAAAAAAAAAAAAAAjAAAAAAAAAAAAAAAA4wAGQAAAAAAAAAAAAKMAA8AAAAAAAAAAAAT"
                                + "jQAYigAAAAAAnBYBA40AF4YAAAAAAJ0UAQONABeDAAAAAACdHAEDjQAXgwAAAAAAmSkBEo0ADIIAAAAAAJkrAQOOABiDAAAA"
                                + "AACZKgEDkgATgwAAAAAAnSIBA5UAF4MAAAAAAJ4gAQKWAA+EAAAAAACeIAEQlgAAhAAAAAAAnh8BA5gAJYUAAAAAAJ4eAQOZ"
                                + "ACWFAAAAAACfGwEDlwAmhgAAAAAAnxkBE5gAJoYAAAAAAJ8YAQKXAAaHAAAAAACeGQEDmAAkhgAAAAAAnxcBA5gAL4YAAAAA"
                                + "AJ8XAROaABeFAAAAAACeGgECmgAZhQAAAAAAnhsBApsAAIQAAAAAAJ4cAQObAB6EAAAAAACeHgETnAAYgwAAAAAAnh8BApwA"
                                + "D4MAAAAAAJ4hAQOdACGCAAAAAACfHwEDnQAgggAAAAAAnyABE50AGIEAAAAAAJ8iAQOdAC6BAAAAAACfIgECngAQgAAAAAAA"
                                + "oCMBA58AL4AAAAAAAKAjAROgACOAAAAAAACgIwEDoAAPfwAAAAAAoCUBA6EAO38AAAAAAKAlAQKhABB/AAAAAACgJwETogAw"
                                + "fgAAAAAAoCgBAqIAD34AAAAAAKAoAQCiABJ9AAAAAACgKQEDowAifQAAAAAAoCkBE6MAKn4AAAAAAKApAQKiACB+AAAAAACg"
                                + "KAEDogAqfgAAAAAAoCgBA6MAHH8AAAAAAKAnAROjACR/AAAAAACgJgEDowAlfwAAAAAAoSQBA6QAI38AAAAAAKEjAQKlAB1/"
                                + "AAAAAAChIwETpQAlfwAAAAAAoiIBAqYAGn4AAAAAAKIjAQKmACF9AAAAAACiJQECqAAafAAAAAAAoicBEqkAFnoAAAAAAKIq"
                                + "AQKpABJ4AAAAAACiLgEAqgAMdQAAAAAAojQB"
                },
                "mHxQaAgG2QD3///+/kf/AAEBAJh8UGjefFBoPwAAAD8AAACbAAAAEgAOAJYBAAAMAQAAOwEAANu2DUHzf1ZBkgAAAGoAiwCi"
                        + "AJmqigAAAAAAAAAAAAAAAAAAAAAAAAAAZmZmPwAAAAAAAAADABsAAAAAAAAhAAAAHgAAAAAAAAAAAAAAAAAAAAAAuAsAAAAA"
                        + "AAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABCAiho");
    }

    /**
     * Runs the same checks over every DETAILS/SUMMARY pair below the directory named by the
     * {@value #RAW_DIR_ENV} environment variable, or {@code ../rawDetails} relative to the
     * module. Files are matched on their {@code <timestamp>_01_<subtype>} prefix. Skipped when
     * neither exists, since captures are not committed.
     */
    @Test
    public void localCapturesMatchTheirSummaries() throws IOException {
        final String env = System.getenv(RAW_DIR_ENV);
        final File root = new File(env != null ? env : "../rawDetails");
        Assume.assumeTrue("no capture directory at " + root.getAbsolutePath(), root.isDirectory());

        final List<File> details = new ArrayList<>();
        collect(root, details);
        final List<String> failures = new ArrayList<>();
        int checked = 0;
        for (final File file : details) {
            final String prefix = file.getName().substring(0, 21); // 20250211T215609_01_03
            final File[] summaries = file.getParentFile().listFiles(
                    (dir, name) -> name.startsWith(prefix + "_01_v") && name.endsWith(".bin"));
            if (summaries == null || summaries.length == 0) {
                continue;
            }
            final byte[] detailBytes = Files.readAllBytes(file.toPath());
            final byte[] summaryBytes = Files.readAllBytes(summaries[0].toPath());
            failures.addAll(check(file.getName(), detailBytes, summaryBytes));
            checked++;
        }
        assertTrue("no DETAILS/SUMMARY pair below " + root, checked > 0);
        assertTrue(checked + " workouts checked, " + failures.size() + " failed:\n" + String.join("\n", failures),
                failures.isEmpty());
    }

    private static void collect(final File dir, final List<File> out) {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (final File f : files) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.getName().matches("\\d{8}T\\d{6}_01_[0-9A-Fa-f]{2}_00_v\\d+(_\\w+)?\\.bin")) {
                out.add(f);
            }
        }
    }

    private static void assertConsistent(final String label, final String fileIdHex, final String bitmapHex,
                                         final String[] segmentHeadersHex, final String[] segmentRecordsBase64,
                                         final String summaryBase64) {
        final byte[] fileId = hex(fileIdHex);
        final byte[] bitmap = hex(bitmapHex);
        int size = fileId.length + 1 + bitmap.length + 4;
        final byte[][] headers = new byte[segmentHeadersHex.length][];
        final byte[][] records = new byte[segmentRecordsBase64.length][];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = hex(segmentHeadersHex[i]);
            records[i] = Base64.getDecoder().decode(segmentRecordsBase64[i]);
            size += headers[i].length + records[i].length;
        }
        final ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(fileId).put((byte) 0).put(bitmap);
        for (int i = 0; i < headers.length; i++) {
            buf.put(headers[i]).put(records[i]);
        }
        buf.putInt(CheckSums.getCRC32(buf.array(), 0, size - 4));

        final List<String> failures = check(label, buf.array(), Base64.getDecoder().decode(summaryBase64));
        assertEquals(String.join("\n", failures), 0, failures.size());
    }

    /** Returns one message per summary metric the decoded DETAILS disagree with. */
    private static List<String> check(final String label, final byte[] detailBytes, final byte[] summaryBytes) {
        final List<String> failures = new ArrayList<>();
        final XiaomiActivityFileId fileId = XiaomiActivityFileId.from(detailBytes);
        final List<WorkoutDetailRecord> records = WorkoutDetailsParser.parseBytes(fileId, detailBytes);
        if (records == null || records.isEmpty()) {
            failures.add(label + ": DETAILS not decoded");
            return failures;
        }

        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setRawSummaryData(summaryBytes);
        new WorkoutSummaryParser().parseBinaryData(summary, true);
        assertNotNull(label + ": summary not decoded", summary.getSummaryData());
        final ActivitySummaryData data = ActivitySummaryData.fromJson(summary.getSummaryData());

        int steps = 0;
        boolean hasSteps = false;
        int maxCadence = 0;
        boolean hasCadence = false;
        double speedDistance = 0;
        boolean hasDistanceSpeed = false;
        for (final WorkoutDetailRecord r : records) {
            if (r.steps != null) {
                steps += r.steps;
                hasSteps = true;
            }
            if (r.cadence != null) {
                maxCadence = Math.max(maxCadence, r.cadence);
                hasCadence = true;
            }
            if (r.distanceDm != null && r.speedMps != null) {
                speedDistance += r.speedMps; // one record per second
                hasDistanceSpeed = true;
            }
        }

        final Number summarySteps = data.getNumber("steps", null);
        if (hasSteps && summarySteps != null && !within(steps, summarySteps.doubleValue())) {
            failures.add(label + ": steps " + steps + " vs summary " + summarySteps);
        }
        final Number summaryDistance = data.getNumber("distanceMeters", null);
        if (hasDistanceSpeed && summaryDistance != null && !within(speedDistance, summaryDistance.doubleValue())) {
            failures.add(String.format("%s: distance from speed %.0f m vs summary %s m",
                    label, speedDistance, summaryDistance));
        }
        Number summaryMaxCadence = data.getNumber("maxCadence", null);
        if (summaryMaxCadence == null) summaryMaxCadence = data.getNumber("stepRateMax", null);
        if (summaryMaxCadence == null) summaryMaxCadence = data.getNumber("maxStrokeRate", null);
        if (summaryMaxCadence != null && summaryMaxCadence.intValue() > 0) {
            if (!hasCadence) {
                failures.add(label + ": no cadence, summary max " + summaryMaxCadence);
            } else if (Math.abs(maxCadence - summaryMaxCadence.intValue()) > 1) {
                failures.add(label + ": max cadence " + maxCadence + " vs summary " + summaryMaxCadence);
            }
        }
        return failures;
    }

    private static boolean within(final double actual, final double expected) {
        return Math.abs(actual - expected) <= Math.max(2, expected * TOTAL_TOLERANCE);
    }

    private static byte[] hex(final String s) {
        final String[] parts = s.trim().split("\\s+");
        if (parts.length == 1 && parts[0].length() > 2) {
            final byte[] out = new byte[parts[0].length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(parts[0].substring(2 * i, 2 * i + 2), 16);
            }
            return out;
        }
        final byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }
}
