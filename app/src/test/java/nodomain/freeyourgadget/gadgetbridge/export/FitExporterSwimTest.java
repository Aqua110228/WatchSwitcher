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
package nodomain.freeyourgadget.gadgetbridge.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitLap;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitLength;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSession;

/**
 * Covers the lap-swimming export path as fed by a non-Garmin parser: per-length records
 * from {@link ActivityTrack#getLengths()}, and the session swim fields for parsers that
 * spell the pool size and SWOLF differently from Xiaomi/Garmin (ZeppOS writes laneLength
 * and swolfIndex, and stores the stroke style as a label rather than a code).
 */
public class FitExporterSwimTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final long START = 1776705018L;
    private static final int LENGTH_COUNT = 4;
    private static final int LENGTH_SECONDS = 30;

    @Test
    public void poolSwimEmitsOneLengthRecordPerPoolLength() throws Exception {
        final File out = exportPoolSwim(newSummaryData(ActivitySummaryEntries.LANE_LENGTH,
                25, ActivitySummaryEntries.UNIT_METERS));

        final FitFile fit = FitFile.parseIncoming(out);
        final List<FitLength> lengths = lengths(fit);
        assertEquals(LENGTH_COUNT, lengths.size());

        for (int i = 0; i < lengths.size(); i++) {
            final FitLength length = lengths.get(i);
            assertEquals("length " + i + " start time",
                    Long.valueOf(START + (long) i * LENGTH_SECONDS), length.getStartTime());
            assertEquals((double) LENGTH_SECONDS, length.getTotalElapsedTime(), 0.001);
            assertEquals(Integer.valueOf(20), length.getTotalStrokes());
            assertEquals(Integer.valueOf(0), length.getSwimStroke()); // freestyle
            assertEquals(Integer.valueOf(1), length.getLengthType()); // active
            assertEquals(Integer.valueOf(40), length.getAvgSwimmingCadence());
        }

        final FitSession session = onlySession(fit);
        assertEquals(Integer.valueOf(LENGTH_COUNT), session.getNumLengths());
        // Every length falls inside the single lap.
        final List<FitLap> laps = laps(fit);
        int lapLengths = 0;
        for (final FitLap lap : laps) {
            if (lap.getNumLengths() != null) {
                lapLengths += lap.getNumLengths();
            }
        }
        assertEquals(LENGTH_COUNT, lapLengths);
    }

    @Test
    public void poolLengthIsReadFromLaneLengthWhenPoolLengthIsAbsent() throws Exception {
        final File out = exportPoolSwim(newSummaryData(ActivitySummaryEntries.LANE_LENGTH,
                25, ActivitySummaryEntries.UNIT_METERS));

        final FitSession session = onlySession(FitFile.parseIncoming(out));
        assertNotNull("session.poolLength", session.getPoolLength());
        assertEquals(25f, session.getPoolLength(), 0.01f);
    }

    @Test
    public void poolLengthInYardsIsConvertedToMeters() throws Exception {
        final File out = exportPoolSwim(newSummaryData(ActivitySummaryEntries.LANE_LENGTH,
                25, ActivitySummaryEntries.UNIT_YARD));

        final FitSession session = onlySession(FitFile.parseIncoming(out));
        assertNotNull("session.poolLength", session.getPoolLength());
        assertEquals(22.86f, session.getPoolLength(), 0.01f);
    }

    @Test
    public void poolLengthKeepsPreferringPoolLengthOverLaneLength() throws Exception {
        final ActivitySummaryData data = newSummaryData(ActivitySummaryEntries.LANE_LENGTH,
                25, ActivitySummaryEntries.UNIT_METERS);
        data.add(ActivitySummaryEntries.POOL_LENGTH, 50, ActivitySummaryEntries.UNIT_METERS);

        final File out = exportPoolSwim(data);

        final FitSession session = onlySession(FitFile.parseIncoming(out));
        assertEquals(50f, session.getPoolLength(), 0.01f);
    }

    @Test
    public void avgSwolfFallsBackToSwolfIndex() throws Exception {
        final File out = exportPoolSwim(newSummaryData(ActivitySummaryEntries.LANE_LENGTH,
                25, ActivitySummaryEntries.UNIT_METERS));

        final FitSession session = onlySession(FitFile.parseIncoming(out));
        assertEquals(Integer.valueOf(50), session.getAvgSwolf());
    }

    @Test
    public void swimStrokeFallsBackToThePredominantStrokeOfTheLengths() throws Exception {
        // SWIM_STYLE is a localised label here, as ZeppOS writes it, so the session stroke
        // has to come from the per-length records instead.
        final File out = exportPoolSwim(newSummaryData(ActivitySummaryEntries.LANE_LENGTH,
                25, ActivitySummaryEntries.UNIT_METERS));

        final FitSession session = onlySession(FitFile.parseIncoming(out));
        assertEquals(Integer.valueOf(0), session.getSwimStroke()); // freestyle
    }

    // ---------- helpers ----------

    private File exportPoolSwim(final ActivitySummaryData data) throws Exception {
        final long elapsed = (long) LENGTH_COUNT * LENGTH_SECONDS;
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setStartTime(new Date(START * 1000L));
        summary.setEndTime(new Date((START + elapsed) * 1000L));
        summary.setActivityKind(ActivityKind.POOL_SWIM.getCode());

        final File out = tmp.newFile("pool-swim-" + System.nanoTime() + ".fit");
        new FitExporter().performExport(newPoolSwimTrack(), summary, data, out);
        return out;
    }

    private static ActivitySummaryData newSummaryData(final String poolLengthKey,
                                                      final int poolLength,
                                                      final String unit) {
        final ActivitySummaryData data = new ActivitySummaryData();
        data.add(poolLengthKey, poolLength, unit);
        // ZeppOS spelling: an index rather than an average, and a label rather than a code.
        data.add(ActivitySummaryEntries.SWOLF_INDEX, 50, ActivitySummaryEntries.UNIT_NONE);
        data.add(ActivitySummaryEntries.SWIM_STYLE, "freestyle");
        data.add(ActivitySummaryEntries.HR_AVG, 130, ActivitySummaryEntries.UNIT_BPM);
        return data;
    }

    /** One continuous segment at 1 Hz, plus one 25 m freestyle length per 30 s. */
    private static ActivityTrack newPoolSwimTrack() {
        final ActivityTrack track = new ActivityTrack();
        track.setCurrentSegmentInfo(new ActivityTrack.SegmentInfo(ActivityTrack.SegmentIntensity.ACTIVE));

        for (long i = 0; i < (long) LENGTH_COUNT * LENGTH_SECONDS; i++) {
            final ActivityPoint p = new ActivityPoint(new Date((START + i) * 1000L));
            p.setHeartRate(130);
            track.addTrackPoint(p);
        }

        for (int i = 0; i < LENGTH_COUNT; i++) {
            track.addLength(new ActivityTrack.LengthInfo(
                    START + (long) i * LENGTH_SECONDS,
                    LENGTH_SECONDS,
                    LENGTH_SECONDS,
                    20,
                    25f / LENGTH_SECONDS,
                    0, // freestyle
                    1, // active
                    40
            ));
        }
        return track;
    }

    private static FitSession onlySession(final FitFile fit) {
        FitSession session = null;
        for (final RecordData r : fit.getRecords()) {
            if (r instanceof FitSession) {
                if (session != null) {
                    throw new AssertionError("multiple sessions");
                }
                session = (FitSession) r;
            }
        }
        assertNotNull("no session record", session);
        return session;
    }

    private static List<FitLength> lengths(final FitFile fit) {
        final List<FitLength> out = new ArrayList<>();
        for (final RecordData r : fit.getRecords()) {
            if (r instanceof FitLength) out.add((FitLength) r);
        }
        return out;
    }

    private static List<FitLap> laps(final FitFile fit) {
        final List<FitLap> out = new ArrayList<>();
        for (final RecordData r : fit.getRecords()) {
            if (r instanceof FitLap) out.add((FitLap) r);
        }
        assertTrue("expected at least one lap", !out.isEmpty());
        return out;
    }
}
