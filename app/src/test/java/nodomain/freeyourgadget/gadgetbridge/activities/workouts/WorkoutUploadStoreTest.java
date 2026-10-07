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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Covers the persistence and the change-detection fingerprint that drive the re-sync of workouts
 * already uploaded to an online fitness tracker.
 */
public class WorkoutUploadStoreTest extends TestBase {

    private BaseActivitySummary newSummary() {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setId(1L);
        summary.setStartTime(new Date(1_600_000_000_000L));
        summary.setEndTime(new Date(1_600_003_600_000L));
        summary.setActivityKind(ActivityKind.RUNNING.getCode());
        summary.setName("Morning run");
        return summary;
    }

    private File writeTempFile(final String name, final String content) throws Exception {
        final File file = File.createTempFile(name, ".tmp");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        file.deleteOnExit();
        return file;
    }

    @Test
    public void testRoundTripKeyedOnSummaryAndService() {
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "100", "photohash", 7, "src", "payload", true, null);
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_WANDERER, "trail1", null, null, "src", "payload", true, null);

        final Map<Long, WorkoutUpload> endurain =
                WorkoutUploadStore.INSTANCE.allUploadedRows(WorkoutUploadStore.SERVICE_ENDURAIN);
        final Map<Long, WorkoutUpload> wanderer =
                WorkoutUploadStore.INSTANCE.allUploadedRows(WorkoutUploadStore.SERVICE_WANDERER);

        // The same summary has an independent row per service, which is what the composite key is for.
        assertEquals(1, endurain.size());
        assertEquals(1, wanderer.size());

        final WorkoutUpload row = endurain.get(42L);
        assertNotNull(row);
        assertEquals("100", row.getRemoteActivityId());
        assertEquals("photohash", row.getPhotoHash());
        assertEquals(Integer.valueOf(7), row.getPhotoMediaId());
        assertEquals("src", row.getSourceHash());
        assertEquals("payload", row.getPayloadHash());
        assertEquals(Boolean.TRUE, row.getHadTrack());
        assertEquals("trail1", wanderer.get(42L).getRemoteActivityId());
    }

    @Test
    public void testRecordSuccessReplacesTheRowRatherThanAddingOne() {
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "100", null, null, "src1", "payload1", false, null);
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "200", null, null, "src2", "payload2", true, null);

        final Map<Long, WorkoutUpload> rows =
                WorkoutUploadStore.INSTANCE.allUploadedRows(WorkoutUploadStore.SERVICE_ENDURAIN);
        assertEquals(1, rows.size());
        // An activity re-created under a new id must leave the row pointing at the replacement.
        assertEquals("200", rows.get(42L).getRemoteActivityId());
        assertEquals("src2", rows.get(42L).getSourceHash());
    }

    @Test
    public void testDeleteRemovesOnlyTheRowOfThatService() {
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "100", null, null, "src", "payload", true, null);
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_WANDERER, "trail1", null, null, "src", "payload", true, null);

        WorkoutUploadStore.INSTANCE.delete(42L, WorkoutUploadStore.SERVICE_ENDURAIN);

        assertTrue(WorkoutUploadStore.INSTANCE
                .allUploadedRows(WorkoutUploadStore.SERVICE_ENDURAIN).isEmpty());
        assertEquals(1, WorkoutUploadStore.INSTANCE
                .allUploadedRows(WorkoutUploadStore.SERVICE_WANDERER).size());
    }

    @Test
    public void testRecordFailureKeepsWhatAnEarlierSuccessEstablished() {
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "100", "photohash", 7, "src", "payload", true, null);

        WorkoutUploadStore.INSTANCE.recordFailure(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "server unreachable");

        final Map<Long, WorkoutUpload> rows =
                WorkoutUploadStore.INSTANCE.allUploadedRows(WorkoutUploadStore.SERVICE_ENDURAIN);
        // The activity is still up there, so the row keeps driving the next re-sync attempt.
        assertEquals(1, rows.size());
        final WorkoutUpload row = rows.get(42L);
        assertNotNull(row);
        assertEquals("100", row.getRemoteActivityId());
        assertEquals("src", row.getSourceHash());
        assertEquals("server unreachable", row.getLastError());
    }

    @Test
    public void testRecordFailureOnAWorkoutThatWasNeverUploadedDoesNotBlockARetry() {
        WorkoutUploadStore.INSTANCE.recordFailure(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "timed out");

        assertTrue(WorkoutUploadStore.INSTANCE
                .allUploadedRows(WorkoutUploadStore.SERVICE_ENDURAIN).isEmpty());

        final WorkoutUpload row = WorkoutUploadStore.INSTANCE
                .rowsForSummaries(Collections.singletonList(42L))
                .get(42L)
                .get(WorkoutUploadStore.SERVICE_ENDURAIN);
        assertNotNull(row);
        assertEquals(WorkoutUploadStore.STATUS_FAILED, row.getStatus());
        assertNull(row.getRemoteActivityId());
        assertEquals("timed out", row.getLastError());
    }

    @Test
    public void testRecordSuccessClearsAnEarlierFailure() {
        WorkoutUploadStore.INSTANCE.recordFailure(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "timed out");
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "100", null, null, "src", "payload", true, null);

        assertNull(WorkoutUploadStore.INSTANCE
                .allUploadedRows(WorkoutUploadStore.SERVICE_ENDURAIN).get(42L).getLastError());
    }

    @Test
    public void testRowsForSummariesReadsEveryServiceAtOnce() {
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_ENDURAIN, "100", null, null, "src", "payload", true, null);
        WorkoutUploadStore.INSTANCE.recordSuccess(
                42L, WorkoutUploadStore.SERVICE_WANDERER, "trail1", null, null, "src", "payload", true, null);
        WorkoutUploadStore.INSTANCE.recordSuccess(
                43L, WorkoutUploadStore.SERVICE_ENDURAIN, "101", null, null, "src", "payload", true, null);

        final Map<Long, Map<Integer, WorkoutUpload>> rows = WorkoutUploadStore.INSTANCE
                .rowsForSummaries(Arrays.asList(42L, 43L, 44L));

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(42L).size());
        assertEquals(1, rows.get(43L).size());
        assertNull(rows.get(44L));
        assertEquals("trail1",
                rows.get(42L).get(WorkoutUploadStore.SERVICE_WANDERER).getRemoteActivityId());
    }

    @Test
    public void testEmptySummaryListIsNotQueried() {
        assertTrue(WorkoutUploadStore.INSTANCE.rowsForSummaries(Collections.emptyList()).isEmpty());
    }

    @Test
    public void testSourceHashIsStableForAnUnchangedSummary() {
        final BaseActivitySummary summary = newSummary();
        assertEquals(WorkoutUploadStore.INSTANCE.sourceHashOf(summary),
                WorkoutUploadStore.INSTANCE.sourceHashOf(summary));
    }

    @Test
    public void testSourceHashChangesWithEverySignalItWatches() throws Exception {
        final BaseActivitySummary summary = newSummary();
        final String base = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        summary.setName("Evening run");
        final String renamed = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);
        assertNotEquals("a renamed workout must be detected", base, renamed);

        summary.setActivityKind(ActivityKind.CYCLING.getCode());
        final String rekinded = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);
        assertNotEquals("a changed activity kind must be detected", renamed, rekinded);

        // A device reprocess rewrites summaryData, which is how it gets noticed.
        summary.setSummaryData("{\"a\":1}");
        final String resummarised = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);
        assertNotEquals("a reparsed summary must be detected", rekinded, resummarised);

        // Attaching a GPX afterwards is the case this whole mechanism exists for.
        final File gpx = writeTempFile("track", "<gpx/>");
        summary.setGpxTrack(gpx.getAbsolutePath());
        final String withTrack = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);
        assertNotEquals("an attached gpx track must be detected", resummarised, withTrack);

        final File photo = writeTempFile("photo", "not really a jpeg");
        summary.setHeaderPhoto(photo.getAbsolutePath());
        final String withPhoto = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);
        assertNotEquals("an attached header photo must be detected", withTrack, withPhoto);

        // Garmin's reprocess repoints rawDetailsPath at a freshly written file.
        final File raw = writeTempFile("raw", "fit bytes");
        summary.setRawDetailsPath(raw.getAbsolutePath());
        assertNotEquals("a repointed rawDetailsPath must be detected",
                withPhoto, WorkoutUploadStore.INSTANCE.sourceHashOf(summary));
    }

    @Test
    public void testSourceHashFollowsFileContentNotJustThePath() throws Exception {
        final BaseActivitySummary summary = newSummary();
        final File gpx = writeTempFile("track", "<gpx>short</gpx>");
        summary.setGpxTrack(gpx.getAbsolutePath());
        final String before = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        // Same path, different contents: the fingerprint uses size and mtime, so this must differ.
        try (FileOutputStream out = new FileOutputStream(gpx)) {
            out.write("<gpx>a considerably longer track</gpx>".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(gpx.setLastModified(gpx.lastModified() + 10_000L));

        assertNotEquals("a rewritten gpx at the same path must be detected",
                before, WorkoutUploadStore.INSTANCE.sourceHashOf(summary));
    }

    @Test
    public void testFileAndPhotoHashes() throws Exception {
        final File a = writeTempFile("file-a", "identical");
        final File b = writeTempFile("file-b", "identical");
        final File c = writeTempFile("file-c", "different");

        assertEquals(WorkoutUploadStore.INSTANCE.fileHashOf(a), WorkoutUploadStore.INSTANCE.fileHashOf(b));
        assertNotEquals(WorkoutUploadStore.INSTANCE.fileHashOf(a), WorkoutUploadStore.INSTANCE.fileHashOf(c));

        assertEquals(WorkoutUploadStore.INSTANCE.fileHashOf(a),
                WorkoutUploadStore.INSTANCE.photoHashOf(a.getAbsolutePath()));
        assertNull(WorkoutUploadStore.INSTANCE.photoHashOf(null));
        assertNull(WorkoutUploadStore.INSTANCE.photoHashOf("   "));
        assertNull(WorkoutUploadStore.INSTANCE.photoHashOf("/does/not/exist.jpg"));
    }

    @Test
    public void testSourceHashUsesTheStoredSummaryDataNotTheInMemoryOne() {
        final BaseActivitySummary summary = newSummary();
        summary.setDeviceId(1L);
        summary.setUserId(1L);
        summary.setSummaryData("{\"stored\":1}");
        try (DBHandler db = GBApplication.acquireDB()) {
            db.getDaoSession().getBaseActivitySummaryDao().insertOrReplace(summary);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        final String stored = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);

        // Opening a workout re-parses its raw details onto the entity without saving it, so the
        // fingerprint has to stay on the row, or one workout would hash two ways.
        summary.setSummaryData("{\"stored\":1,\"parsed\":\"a much richer summary\"}");
        assertEquals(stored, WorkoutUploadStore.INSTANCE.sourceHashOf(summary));

        assertEquals("{\"stored\":1}",
                WorkoutUploadStore.INSTANCE.storedSummaryData(Collections.singletonList(1L)).get(1L));
    }

    @Test
    public void testStoredSummaryDataSkipsSummariesThatHaveNoRow() {
        assertTrue(WorkoutUploadStore.INSTANCE.storedSummaryData(Collections.emptyList()).isEmpty());
        assertTrue(WorkoutUploadStore.INSTANCE.storedSummaryData(
                Collections.singletonList(9999L)).isEmpty());
    }

    @Test
    public void testSourceHashToleratesMissingFiles() {
        final BaseActivitySummary summary = newSummary();
        summary.setGpxTrack("/gone/track.gpx");
        summary.setHeaderPhoto("/gone/photo.jpg");
        summary.setRawDetailsPath("/gone/raw.fit");

        final String hash = WorkoutUploadStore.INSTANCE.sourceHashOf(summary);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(hash, WorkoutUploadStore.INSTANCE.sourceHashOf(summary));
    }
}
