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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Covers how the per-service upload rows collapse into the single state shown on a workout row.
 */
public class WorkoutUploadStatusTest extends TestBase {

    private static final List<WorkoutUploadTarget> BOTH =
            Arrays.asList(EndurainUploadTarget.INSTANCE, WandererUploadTarget.INSTANCE);

    private BaseActivitySummary summaryWithTrack() throws Exception {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setId(1L);
        summary.setStartTime(new Date(1_600_000_000_000L));
        summary.setEndTime(new Date(1_600_003_600_000L));
        summary.setActivityKind(ActivityKind.RUNNING.getCode());
        summary.setName("Morning run");

        final File gpx = File.createTempFile("track", ".gpx");
        try (FileOutputStream out = new FileOutputStream(gpx)) {
            out.write("<gpx/>".getBytes(StandardCharsets.UTF_8));
        }
        gpx.deleteOnExit();
        summary.setGpxTrack(gpx.getAbsolutePath());
        return summary;
    }

    private BaseActivitySummary summaryWithoutTrack() throws Exception {
        final BaseActivitySummary summary = summaryWithTrack();
        summary.setGpxTrack(null);
        return summary;
    }

    private WorkoutUpload row(final int service, final int status, final String sourceHash,
                              final String lastError) {
        final WorkoutUpload upload = new WorkoutUpload();
        upload.setSummaryId(1L);
        upload.setService(service);
        upload.setStatus(status);
        upload.setSourceHash(sourceHash);
        upload.setLastError(lastError);
        return upload;
    }

    private Map<Integer, WorkoutUpload> rows(final WorkoutUpload... uploads) {
        final Map<Integer, WorkoutUpload> map = new HashMap<>();
        for (final WorkoutUpload upload : uploads) {
            map.put(upload.getService(), upload);
        }
        return map;
    }

    @Test
    public void testNothingToShowWithoutServicesOrRows() throws Exception {
        assertNull(WorkoutUploadStatusKt.workoutUploadStatus(
                summaryWithTrack(), Collections.emptyMap(), Collections.emptyList()));
    }

    @Test
    public void testAServiceThatOnceTookItStillShowsAfterItIsSwitchedOff() throws Exception {
        final BaseActivitySummary summary = summaryWithTrack();
        final String hash = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        assertEquals(WorkoutUploadStatus.UPLOADED, WorkoutUploadStatusKt.workoutUploadStatus(
                summary,
                rows(row(WorkoutUploadStore.SERVICE_ENDURAIN, WorkoutUploadStore.STATUS_SUCCESS, hash, null)),
                Collections.emptyList()));
    }

    @Test
    public void testUploadedWhenEveryApplicableServiceIsInStep() throws Exception {
        final BaseActivitySummary summary = summaryWithTrack();
        final String hash = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        assertEquals(WorkoutUploadStatus.UPLOADED, WorkoutUploadStatusKt.workoutUploadStatus(
                summary,
                rows(row(WorkoutUploadStore.SERVICE_ENDURAIN, WorkoutUploadStore.STATUS_SUCCESS, hash, null),
                        row(WorkoutUploadStore.SERVICE_WANDERER, WorkoutUploadStore.STATUS_SUCCESS, hash, null)),
                BOTH));
    }

    @Test
    public void testPartialWhenOnlyOneOfTwoTookIt() throws Exception {
        final BaseActivitySummary summary = summaryWithTrack();
        final String hash = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        assertEquals(WorkoutUploadStatus.PARTIAL, WorkoutUploadStatusKt.workoutUploadStatus(
                summary,
                rows(row(WorkoutUploadStore.SERVICE_ENDURAIN, WorkoutUploadStore.STATUS_SUCCESS, hash, null)),
                BOTH));
    }

    @Test
    public void testAServiceThatCannotTakeATracklessWorkoutIsNotCountedAgainstIt() throws Exception {
        final BaseActivitySummary summary = summaryWithoutTrack();
        final String hash = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        // Wanderer stores trails, so a workout with no track is complete once Endurain has it.
        assertEquals(WorkoutUploadStatus.UPLOADED, WorkoutUploadStatusKt.workoutUploadStatus(
                summary,
                rows(row(WorkoutUploadStore.SERVICE_ENDURAIN, WorkoutUploadStore.STATUS_SUCCESS, hash, null)),
                BOTH));
    }

    @Test
    public void testPendingWhenNothingHasTakenItYet() throws Exception {
        assertEquals(WorkoutUploadStatus.PENDING, WorkoutUploadStatusKt.workoutUploadStatus(
                summaryWithTrack(), Collections.emptyMap(), BOTH));
    }

    @Test
    public void testAnEditAfterTheUploadReadsAsPendingAgain() throws Exception {
        assertEquals(WorkoutUploadStatus.PENDING, WorkoutUploadStatusKt.workoutUploadStatus(
                summaryWithTrack(),
                rows(row(WorkoutUploadStore.SERVICE_ENDURAIN, WorkoutUploadStore.STATUS_SUCCESS, "stale", null),
                        row(WorkoutUploadStore.SERVICE_WANDERER, WorkoutUploadStore.STATUS_SUCCESS, "stale", null)),
                BOTH));
    }

    @Test
    public void testAFailedFirstUploadWins() throws Exception {
        final BaseActivitySummary summary = summaryWithTrack();
        final String hash = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        assertEquals(WorkoutUploadStatus.FAILED, WorkoutUploadStatusKt.workoutUploadStatus(
                summary,
                rows(row(WorkoutUploadStore.SERVICE_ENDURAIN, WorkoutUploadStore.STATUS_SUCCESS, hash, null),
                        row(WorkoutUploadStore.SERVICE_WANDERER, WorkoutUploadStore.STATUS_FAILED, null, "timed out")),
                BOTH));
    }

    @Test
    public void testARefusedResyncWinsEvenThoughTheActivityIsUp() throws Exception {
        final BaseActivitySummary summary = summaryWithTrack();
        final String hash = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        assertEquals(WorkoutUploadStatus.FAILED, WorkoutUploadStatusKt.workoutUploadStatus(
                summary,
                rows(row(WorkoutUploadStore.SERVICE_ENDURAIN, WorkoutUploadStore.STATUS_SUCCESS, hash, "edited on the server"),
                        row(WorkoutUploadStore.SERVICE_WANDERER, WorkoutUploadStore.STATUS_SUCCESS, hash, null)),
                BOTH));
    }
}
