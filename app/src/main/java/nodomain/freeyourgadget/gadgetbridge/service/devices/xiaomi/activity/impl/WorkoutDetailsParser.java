/*  Copyright (C) 2025 José Rebelo, Martin Schitter, Dany Mestas

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

import android.content.Context;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityParser;
import nodomain.freeyourgadget.gadgetbridge.util.ArrayUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class WorkoutDetailsParser extends XiaomiActivityParser {
    private static final Logger LOG = LoggerFactory.getLogger(WorkoutDetailsParser.class);

    /** Bytes per field group of an outdoor run/walk record, in bitmap order. v5 records hold
     *  groups 0-8 and v8 records all 13. The sizes reproduce the record size of every captured
     *  bitmap: v5 FF CF F8 BF FF = 13 bytes, v5 EC CC C0 0C C0 = 8, v5 0C 0C 00 0C C0 = 5,
     *  v8 FF CF F8 BF FB BB BF, FF CF FA BF FB BF FF and DF CB F8 BB FB BB BF = 21. */
    private static final int[] RUN_WALK_GROUP_BYTES = {1, 1, 1, 1, 1, 4, 1, 1, 2, 2, 2, 2, 2};
    private static final int RUN_WALK_GROUP_STEPS = 0;
    private static final int RUN_WALK_GROUP_HR = 1;
    private static final int RUN_WALK_GROUP_DISTANCE = 3;
    private static final int RUN_WALK_GROUP_CADENCE = 7;
    private static final int RUN_WALK_GROUP_PACE = 8;
    /** Records averaged, centred on each record, to turn the per-second distance into speed
     *  where no pace is available. */
    private static final int SPEED_WINDOW = 5;

    private static final byte[] FREESTYLE_V3_BITMAP = {(byte) 0xFF, (byte) 0xBB};
    private static final byte[] OUTDOOR_CYCLING_BITMAP = {(byte) 0xDF, (byte) 0xCF, (byte) 0xFB};
    private static final byte[] ELLIPTICAL_V3_BITMAP = {(byte) 0xFF, (byte) 0xFF};
    private static final byte[] ROWING_V4_BITMAP = {(byte) 0xFF, (byte) 0xFF};
    private static final byte[] TREADMILL_V6_BITMAP = {(byte) 0xFF, (byte) 0xFF, (byte) 0x8B, (byte) 0xFF};
    private static final byte[] INDOOR_CYCLING_V6_BITMAP = {(byte) 0xDF, (byte) 0xBB, (byte) 0xBB, (byte) 0xBF};

    /** Per-record output of {@link #parseBytes}. Fields are nullable when the version
     *  format does not encode them. Decoupled from {@link XiaomiActivitySample} so parsed
     *  metrics never touch the sample table — {@link nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityTrackProvider}
     *  re-parses raw bytes on demand. */
    public static final class WorkoutDetailRecord {
        public int ts;
        public int hr;
        public Integer steps;
        public Integer spo2;
        public Integer cadence;
        public Integer speedRaw;
        /** Speed in m/s, for layouts whose speed unit is confirmed. Takes precedence over
         *  {@link #speedRaw}. */
        public Float speedMps;
        /** Distance covered during this record's second, in dm, for layouts that record it.
         *  Feeds {@link #speedMps} through {@link #setSpeedFromDistance}. */
        public Integer distanceDm;
        /** Pace in s/km as the band displays it, 0 while stopped. Shapes {@link #speedMps}
         *  in {@link #setSpeedFromDistance}. */
        public Integer paceSecPerKm;
        /** True on the first record of each interval/segment, for layouts whose phase
         *  semantics are confirmed (currently rowing v4 only). Drives
         *  {@link ActivityTrack} segment boundaries → one FIT lap per interval. */
        public boolean segmentStart;
        /** Intensity of the segment this record opens. Only meaningful when
         *  {@link #segmentStart} is set; null otherwise. */
        public ActivityTrack.SegmentIntensity segmentIntensity;
        /** Per-segment strokes parsed directly from the segment header (rowing v4).
         *  Only set on a {@link #segmentStart} record; null when not encoded. */
        public Integer segmentStrokes;
    }

    @Override
    public boolean parse(final Context context, final GBDevice gbDevice, final XiaomiActivityFileId fileId, final byte[] bytes) {
        // DETAILS files are not persisted to the sample table — XiaomiActivityTrackProvider
        // re-parses the raw bytes on demand. Returning success here just acks the file.
        return parseBytes(fileId, bytes) != null;
    }

    /**
     * Build an {@link ActivityTrack} from DETAILS binary data for non-GPS workouts.
     * {@link ActivityPoint}s carry HR, cadence, and speed where the layout has them, but no
     * GPS location.
     * Returns null on parse failure or if there are no records.
     */
    @Nullable
    public ActivityTrack getActivityTrack(final XiaomiActivityFileId fileId, final byte[] bytes) {
        final List<WorkoutDetailRecord> records = parseBytes(fileId, bytes);
        if (records == null || records.isEmpty()) {
            return null;
        }
        final ActivityTrack track = new ActivityTrack();
        boolean firstSegment = true;
        for (final WorkoutDetailRecord r : records) {
            if (r.segmentStart) {
                // Parsers that expose confirmed interval phases (rowing v4) mark the first
                // record of each segment. The first marked segment sets the metadata of the
                // implicit initial segment; later ones open new segments → one FIT lap each.
                final ActivityTrack.SegmentInfo info = new ActivityTrack.SegmentInfo(
                        r.segmentIntensity, null, r.segmentStrokes);
                if (firstSegment) {
                    track.setCurrentSegmentInfo(info);
                } else {
                    track.startNewSegment(info);
                }
                firstSegment = false;
            }
            final ActivityPoint.Builder builder = new ActivityPoint.Builder(new Date(r.ts * 1000L));
            applyMetrics(builder, fileId.getVersion(), r);
            // No GPS location for non-GPS activities — map will not render, charts still work
            track.addTrackPoint(builder.build());
        }
        return track;
    }

    private static void applyMetrics(final ActivityPoint.Builder builder,
                                     final int version,
                                     final WorkoutDetailRecord r) {
        if (r.hr > 0) builder.setHeartRate(r.hr);
        if (r.cadence != null && r.cadence > 0) builder.setCadence(r.cadence);
        final Float speedMps = pointSpeedMps(version, r);
        if (speedMps != null) builder.setSpeed(speedMps);
    }

    /**
     * Speed in m/s for the record's {@link ActivityPoint}, or null when it carries none or an
     * implausible one. Treadmill v5 stores the belt speed in 0.1 km/h, m/s = speedRaw / 36. A
     * raw 0 is a stopped belt (the rest intervals, where the cadence is 0 as well), so it is a
     * speed of 0, not a missing sample. Treadmills cap around 25 km/h (~7 m/s); anything above
     * 20 m/s is noise.
     */
    @Nullable
    private static Float pointSpeedMps(final int version, final WorkoutDetailRecord r) {
        if (r.speedMps != null) return r.speedMps;
        if (version != 5 || r.speedRaw == null) return null;
        final float speedMps = r.speedRaw / 36f;
        return speedMps < 20f ? speedMps : null;
    }

    /** Apply parsed DETAILS metrics onto an existing track (e.g. from GPS) by matching
     *  unix-second timestamps. Used by
     *  {@link nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityTrackProvider}
     *  when a workout has both GPS_TRACK and DETAILS files.
     *
     *  DETAILS records whose timestamp has no matching point in the track (e.g. mid-trip
     *  GPS fix loss, where the watch emits sparse or no GPS records but still records HR
     *  at 1 Hz) are appended as location-less {@link ActivityPoint}s so the HR / cadence
     *  chart stays continuous; the segment is sorted by time afterwards.
     */
    public static void mergeOntoTrack(final ActivityTrack track,
                                      final XiaomiActivityFileId fileId,
                                      final byte[] bytes) {
        final List<WorkoutDetailRecord> records = parseBytes(fileId, bytes);
        if (records == null || records.isEmpty()) return;
        final java.util.Map<Integer, WorkoutDetailRecord> byTs = new java.util.HashMap<>(records.size());
        for (final WorkoutDetailRecord r : records) byTs.put(r.ts, r);
        final int version = fileId.getVersion();
        for (final ActivityPoint p : track.getAllPoints()) {
            final int ts = (int) (p.getTime().getTime() / 1000L);
            final WorkoutDetailRecord r = byTs.remove(ts);
            if (r == null) continue;
            applyMetricsToPoint(p, version, r);
        }
        if (byTs.isEmpty()) return;
        for (final WorkoutDetailRecord r : byTs.values()) {
            final ActivityPoint.Builder builder = new ActivityPoint.Builder(new Date(r.ts * 1000L));
            applyMetrics(builder, version, r);
            track.addTrackPoint(builder.build());
        }
        track.sortPointsByTime();
    }

    private static void applyMetricsToPoint(final ActivityPoint p,
                                            final int version,
                                            final WorkoutDetailRecord r) {
        if (r.hr > 0) p.setHeartRate(r.hr);
        if (r.cadence != null && r.cadence > 0) p.setCadence(r.cadence);
        final Float speedMps = pointSpeedMps(version, r);
        if (speedMps != null) p.setSpeed(speedMps);
    }

    /**
     * Parse DETAILS binary data into a list of records.
     * Returns null on parse failure (bad version/signature), empty list if no records.
     *
     * Guard wrapper around {@link #parseRecords}: any outcome that yields no per-record
     * samples (unsupported version, unknown signature, or an empty record stream) is logged
     * once at WARN with the full fileId + signature. This is the single point where an
     * unsupported or format-drifted DETAILS layout silently strips HR / cadence from the
     * GPX / FIT export — without this line the only symptom is "the exported file has no HR".
     * Grep {@code "DETAILS produced no samples"} to find devices whose per-record stream still
     * needs a layout.
     */
    @Nullable
    public static List<WorkoutDetailRecord> parseBytes(final XiaomiActivityFileId fileId, final byte[] bytes) {
        final List<WorkoutDetailRecord> records = parseRecords(fileId, bytes);
        if (records == null || records.isEmpty()) {
            final String sig = (bytes != null && bytes.length >= 16)
                    ? GB.hexdump(bytes, 8, 8)
                    : "<short>";
            LOG.warn("Xiaomi workout DETAILS produced no samples — HR/cadence will be ABSENT "
                    + "from this workout's export. fileId={} sig@8={}", fileId, sig);
        }
        return records;
    }

    /** True when the bitmap at offset 8 flags the same field groups present as {@code bitmap}:
     *  bit 3 of every nibble matches. The other three bits of a nibble mark which values of the
     *  group the band measured and vary between bands, without changing the record layout. */
    private static boolean hasSameFieldGroups(final byte[] bytes, final byte[] bitmap) {
        if (bytes.length < 8 + bitmap.length) {
            return false;
        }
        for (int i = 0; i < bitmap.length; i++) {
            if ((bytes[8 + i] & 0x88) != (bitmap[i] & 0x88)) {
                return false;
            }
        }
        return true;
    }

    /** True when the bitmap at offset 8 is {@code bitmap}, or when the file is of
     *  {@code subtype} and its bitmap flags the same field groups present. The subtype is
     *  required for the second case because sports share presence patterns with different
     *  records (FF BB freestyle and FF FF elliptical both flag 4 groups). */
    private static boolean matchesBitmap(final XiaomiActivityFileId fileId, final byte[] bytes,
                                         final XiaomiActivityFileId.Subtype subtype, final byte[] bitmap) {
        return ArrayUtils.equals(bytes, bitmap, 8)
                || (fileId.getSubtype() == subtype && hasSameFieldGroups(bytes, bitmap));
    }

    /**
     * Outdoor run/walk (subtype 0x16) v5 and v8. The fileId and padding are followed by a bitmap
     * with one nibble per field group, as read by {@link XiaomiComplexActivityParser}: a group is
     * in the record when bit 3 of its nibble is set. The record size follows from the bitmap,
     * which varies with the band and the sport (a Mi Band 9 Active run carries 4 of the groups).
     * The other three bits do not tell whether these single-value groups hold data: a B nibble
     * carries real cadence and distance, so only bit 3 is checked.
     *
     * Segment header, 17 bytes in v5 and 27 bytes in v8:
     *   offset 4-7:   int32 record count
     *   offset 8-11:  int32 segment start, unix seconds
     *   offset 13-16: int32 segment distance, metres
     *
     * One record per second. Groups read, see {@link #RUN_WALK_GROUP_BYTES} for the sizes:
     *   0: steps in the low nibble
     *   1: HR (bpm)
     *   3: distance covered in that second (uint8, dm). Summed over a segment it equals the
     *      segment distance in the header on every capture.
     *   7: cadence (steps/min)
     *   8: pace (uint16, s/km). Smooth, and its extremes equal the summary fastest/slowest
     *      pace, but it lags: integrated over a workout it gives 20% to 128% of the distance.
     * Speed combines groups 3 and 8, see {@link #setSpeedFromDistance}.
     * HR min/avg/max and top cadence of a Mi Band 9 Active run equal the paired summary.
     *
     * Returns null when the segments do not consume the payload exactly.
     */
    @Nullable
    private static List<WorkoutDetailRecord> parseRunWalkRecords(final XiaomiActivityFileId fileId, final byte[] bytes) {
        final int version = fileId.getVersion();
        final int bitmapSize = version == 5 ? 5 : 7;
        final int groupCount = version == 5 ? 9 : RUN_WALK_GROUP_BYTES.length;
        final int segmentHeaderSize = version == 5 ? 17 : 27;

        if (bytes.length < 8 + bitmapSize + 4) {
            LOG.warn("Run/walk v{} DETAILS too short: {} bytes", version, bytes.length);
            return null;
        }
        final byte[] bitmap = Arrays.copyOfRange(bytes, 8, 8 + bitmapSize);

        int recordSize = 0;
        for (int group = 0; group < groupCount; group++) {
            final int nibble = (group % 2 == 0 ? bitmap[group / 2] >> 4 : bitmap[group / 2]) & 0x0F;
            if ((nibble & 8) != 0) {
                recordSize += RUN_WALK_GROUP_BYTES[group];
            }
        }
        if (recordSize == 0) {
            LOG.warn("Run/walk v{} DETAILS bitmap {} has no field groups", version, GB.hexdump(bitmap));
            return null;
        }

        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.limit(buf.limit() - 4); // strip CRC32
        buf.position(8 + bitmapSize);

        final XiaomiComplexActivityParser groups = new XiaomiComplexActivityParser(bitmap, buf);
        final List<WorkoutDetailRecord> records = new ArrayList<>();
        while (buf.hasRemaining()) {
            final int segmentStart = buf.position();
            if (buf.remaining() < segmentHeaderSize) {
                LOG.warn("Run/walk v{} DETAILS bitmap {}: {} trailing bytes at {}",
                        version, GB.hexdump(bitmap), buf.remaining(), segmentStart);
                return null;
            }
            final int nr = buf.getInt(segmentStart + 4);
            int ts = buf.getInt(segmentStart + 8);
            buf.position(segmentStart + segmentHeaderSize);
            if (nr < 0 || (long) nr * recordSize > buf.remaining()) {
                LOG.warn("Run/walk v{} DETAILS bitmap {}: segment at {} claims {} records of {} bytes, {} bytes remain",
                        version, GB.hexdump(bitmap), segmentStart, nr, recordSize, buf.remaining());
                return null;
            }
            LOG.debug("Segment: {} records of {} bytes starting at ts={}", nr, recordSize, ts);

            final int firstRecord = records.size();
            for (int i = 0; i < nr; i++) {
                groups.reset();
                final WorkoutDetailRecord r = new WorkoutDetailRecord();
                r.ts = ts++;
                for (int group = 0; group < groupCount; group++) {
                    if (!groups.nextGroup(RUN_WALK_GROUP_BYTES[group] * 8)) {
                        continue;
                    }
                    switch (group) {
                        case RUN_WALK_GROUP_STEPS:
                            r.steps = groups.get(4, 4);
                            break;
                        case RUN_WALK_GROUP_HR:
                            r.hr = groups.get(0, 8);
                            break;
                        case RUN_WALK_GROUP_DISTANCE:
                            r.distanceDm = groups.get(0, 8);
                            break;
                        case RUN_WALK_GROUP_CADENCE:
                            r.cadence = groups.get(0, 8);
                            break;
                        case RUN_WALK_GROUP_PACE:
                            r.paceSecPerKm = groups.get(0, 16);
                            break;
                    }
                }
                records.add(r);
            }
            setSpeedFromDistance(records, firstRecord);
        }

        return records;
    }

    /**
     * Sets {@link WorkoutDetailRecord#speedMps} on the records of one segment, from
     * {@code first} to the end of {@code records}, so that the speed integrates to the
     * segment's {@link WorkoutDetailRecord#distanceDm}. Records are one second apart.
     *
     * The per-second distance is exact in total but arrives in bursts (a 0 every few seconds,
     * then a doubled value). The pace is smooth but its level drifts from the distance. Records
     * without a pace (stopped, or a layout without one) take their mean distance over
     * {@link #SPEED_WINDOW} records centred on them, clipped to the segment. Records with a pace
     * take their shape from {@code 1000 / pace}, scaled by one factor per segment so that they
     * cover whatever distance of the segment the unpaced records did not.
     *
     * No-op when the layout carries no distance.
     */
    private static void setSpeedFromDistance(final List<WorkoutDetailRecord> records, final int first) {
        final int last = records.size() - 1;
        if (first > last || records.get(first).distanceDm == null) {
            return;
        }

        final int half = SPEED_WINDOW / 2;
        double remainingDistance = 0;
        double pacedSpeedSum = 0;
        for (int i = first; i <= last; i++) {
            final WorkoutDetailRecord r = records.get(i);
            remainingDistance += r.distanceDm / 10.0;
            if (r.paceSecPerKm != null && r.paceSecPerKm > 0) {
                pacedSpeedSum += 1000.0 / r.paceSecPerKm;
                continue;
            }
            final int from = Math.max(first, i - half);
            final int to = Math.min(last, i + half);
            int sum = 0;
            for (int j = from; j <= to; j++) {
                sum += records.get(j).distanceDm;
            }
            r.speedMps = sum / 10f / (to - from + 1);
            remainingDistance -= r.speedMps;
        }
        if (pacedSpeedSum == 0) {
            return;
        }

        final double scale = Math.max(0, remainingDistance) / pacedSpeedSum;
        for (int i = first; i <= last; i++) {
            final WorkoutDetailRecord r = records.get(i);
            if (r.paceSecPerKm != null && r.paceSecPerKm > 0) {
                r.speedMps = (float) (1000.0 / r.paceSecPerKm * scale);
            }
        }
    }

    @Nullable
    private static List<WorkoutDetailRecord> parseRecords(final XiaomiActivityFileId fileId, final byte[] bytes) {
        final int version = fileId.getVersion();
        if (fileId.getSubtype() == XiaomiActivityFileId.Subtype.SPORTS_OUTDOOR_WALKING_V2
                && (version == 5 || version == 8)) {
            return parseRunWalkRecords(fileId, bytes);
        }
        // Layout code keys the segment-header + record-read switches. Defaults to `version`,
        // but signature-keyed sub-dispatch (e.g. v3 FFBB, v5 DFCFFB) can override it
        // to a synthetic code (>= 100) so the record loop can read the variant layout.
        int layoutCode = version;
        final int segmentHeaderSize;
        final int recordSize;
        // Byte offsets within the segment header for the record-count and start-timestamp fields.
        // Most layouts derive nrPosition from tsPosition (nr immediately precedes ts), but
        // signature-keyed variants can override both independently.
        final int tsPosition;
        final int nrPosition;
        final byte[] expectedSignature;

        switch (version) {
            case 2:
                // Signature: 0xC0 (1 byte), segment header 9 bytes, 2-byte records: [hr][calories]
                expectedSignature = new byte[]{(byte) 0xC0};
                segmentHeaderSize = 9;
                recordSize = 2;
                tsPosition = 4;
                nrPosition = 0;
                break;
            case 3:
                // v3 reused across sport types. Dispatch by signature.
                if (bytes.length >= 11
                        && bytes[8] == (byte) 0xCC && bytes[9] == (byte) 0xCC && bytes[10] == (byte) 0xC0) {
                    // Existing v3 layout (cycling / running pre-v5): CC CC C0 + 17-byte hdr + 6-byte records.
                    expectedSignature = new byte[]{(byte) 0xCC, (byte) 0xCC, (byte) 0xC0};
                    segmentHeaderSize = 17;
                    recordSize = 6;
                    tsPosition = 8;
                    nrPosition = 4;
                } else if (matchesBitmap(fileId, bytes, XiaomiActivityFileId.Subtype.SPORTS_FREESTYLE, FREESTYLE_V3_BITMAP)) {
                    // SPORTS_FREESTYLE v3: signature FF BB. The byte after the signature is the
                    // low byte of the record count and varies per workout — earlier code matched
                    // a fixed third byte (0x53) and so parsed only one workout in seven.
                    //   9-byte segment header:
                    //     offset 0-1:  int16 nr  — record count (read as int32; offset 2-3 are 0 pad)
                    //     offset 2-3:  2 byte pad
                    //     offset 4-7:  int32 ts  — segment start, unix seconds
                    //     offset 8:    byte phase — only 0x7f observed
                    //   4-byte records: [hr][flags][reserved][reserved]. Validated against 7
                    //   captured workouts: each tiles exactly (19 + nr*4 == payload end) and the
                    //   per-record HR range is physiological (max 145-188).
                    expectedSignature = Arrays.copyOfRange(bytes, 8, 8 + FREESTYLE_V3_BITMAP.length);
                    segmentHeaderSize = 9;
                    recordSize = 4;
                    tsPosition = 4;
                    nrPosition = 0;
                    layoutCode = 103; // synthetic: v3-freestyle record shape
                } else if (matchesBitmap(fileId, bytes, XiaomiActivityFileId.Subtype.SPORTS_OUTDOOR_CYCLING, OUTDOOR_CYCLING_BITMAP)) {
                    // SPORTS_OUTDOOR_CYCLING (subtype 0x17) v3: signature DF CF FB.
                    //   17-byte segment header:
                    //     offset 0-3:   4 pad
                    //     offset 4-7:   int32 nr        — record count for this segment
                    //     offset 8-11:  int32 ts        — segment start, unix seconds
                    //     offset 12:    byte  phase     — only 0x7f observed
                    //     offset 13-16: int32 distance  — meters
                    //   7-byte records — HR decoded in case 113. Validated against the paired
                    //   cycling summary: the max per-record HR matched the summary HR_MAX.
                    expectedSignature = Arrays.copyOfRange(bytes, 8, 8 + OUTDOOR_CYCLING_BITMAP.length);
                    segmentHeaderSize = 17;
                    recordSize = 7;
                    tsPosition = 8;
                    nrPosition = 4;
                    layoutCode = 113; // synthetic: outdoor-cycling-v3 record shape
                } else if (matchesBitmap(fileId, bytes, XiaomiActivityFileId.Subtype.SPORTS_ELLIPTICAL, ELLIPTICAL_V3_BITMAP)) {
                    // SPORTS_ELLIPTICAL (subtype 0x0B) v3: signature FF FF.
                    //   9-byte segment header: int32 nr | int32 ts | byte phase (0x7f only observed).
                    //   3-byte records decoded in case 111. Validated against the paired
                    //   elliptical summary: the max per-record HR matched the summary HR_MAX.
                    expectedSignature = Arrays.copyOfRange(bytes, 8, 8 + ELLIPTICAL_V3_BITMAP.length);
                    segmentHeaderSize = 9;
                    recordSize = 3;
                    tsPosition = 4;
                    nrPosition = 0;
                    layoutCode = 111; // synthetic: elliptical-v3 record shape
                } else {
                    LOG.warn("Unknown v3 DETAILS signature: {}",
                            GB.hexdump(bytes, 8, Math.min(3, bytes.length - 8)));
                    return null;
                }
                break;
            case 4:
                // v4 is reused across multiple sport types with different payload layouts.
                // Dispatch by the leading signature bytes at offset 8 to pick the format.
                if (matchesBitmap(fileId, bytes, XiaomiActivityFileId.Subtype.SPORTS_ROWING, ROWING_V4_BITMAP)) {
                    // Rowing: FF FF (2-byte sig) + 13-byte segment header + 3-byte records
                    //   [hr][events][stroke_rate]. Multi-segment, alternating active/rest.
                    // Segment header layout:
                    //   offset 0-3:  int32 nr      — record count for this segment
                    //   offset 4-7:  int32 ts      — segment start, unix seconds
                    //   offset 8:    byte  phase   — 0x81 = active rowing, 0x82 = rest/transition
                    //   offset 9-12: int32 strokes — strokes count for this segment;
                    //                                sum across segments matches summary STROKES.
                    expectedSignature = Arrays.copyOfRange(bytes, 8, 8 + ROWING_V4_BITMAP.length);
                    segmentHeaderSize = 13;
                    recordSize = 3;
                    tsPosition = 4;
                    nrPosition = 0;
                } else if (bytes.length >= 13
                        && bytes[8] == (byte) 0xEC && bytes[9] == (byte) 0xCC && bytes[10] == (byte) 0xC8) {
                    // Mi Band 8 walking-style v4: signature EC CC C8 00 00. Layout not yet
                    // decoded; appears to be a type-tagged TLV stream.
                    // Returning null routes the workout to GpxActivityTrackProvider so summary
                    // metrics still render; HR / cadence / SpO2 charts will be empty until a
                    // binary fixture lets us implement the TLV decoder.
                    LOG.info("Mi Band 8 walking-style DETAILS payload (sig EC CC C8) not yet supported; falling back to GPX");
                    return null;
                } else {
                    LOG.warn("Unknown v4 DETAILS signature: {}",
                            GB.hexdump(bytes, 8, Math.min(5, bytes.length - 8)));
                    return null;
                }
                break;
            case 5:
                // v5 reused across sport types. Dispatch by signature.
                if (bytes.length >= 13
                        && bytes[8] == (byte) 0xEC && bytes[9] == (byte) 0xCC && bytes[10] == (byte) 0xC0
                        && bytes[11] == (byte) 0x0C && bytes[12] == (byte) 0xC0) {
                    // Existing v5 layout (cycling / running): EC CC C0 0C C0 + 17-byte hdr + 8-byte records.
                    expectedSignature = new byte[]{(byte) 0xEC, (byte) 0xCC, (byte) 0xC0, (byte) 0x0C, (byte) 0xC0};
                    segmentHeaderSize = 17;
                    recordSize = 8;
                    tsPosition = 8;
                    nrPosition = 4;
                } else if (bytes.length >= 13
                        && bytes[8] == (byte) 0xEC && bytes[9] == (byte) 0xCC && bytes[10] == (byte) 0x80
                        && bytes[11] == (byte) 0x28 && bytes[12] == (byte) 0x06) {
                    // SPORTS_TREADMILL v5: signature EC CC 80 28 06.
                    //   115-byte segment header: int32 start ts at offset 2; byte 0x7f at offset 6
                    //     (same 0x7f-only "phase" byte seen in the v6 treadmill header; remainder is zero).
                    //   No record-count field in the header → single-segment fallback below.
                    //   8-byte records — layout decoded in case 205. Validated against the paired
                    //   treadmill summary: mean HR matched HR_AVG and the top speed matched PACE_MAX.
                    expectedSignature = new byte[]{(byte) 0xEC, (byte) 0xCC, (byte) 0x80, (byte) 0x28, (byte) 0x06};
                    segmentHeaderSize = 115;
                    recordSize = 8;
                    tsPosition = 2;
                    nrPosition = 0;
                    layoutCode = 205; // synthetic: treadmill-v5 record shape
                } else if (matchesBitmap(fileId, bytes, XiaomiActivityFileId.Subtype.SPORTS_OUTDOOR_CYCLING, OUTDOOR_CYCLING_BITMAP)) {
                    // SPORTS_OUTDOOR_CYCLING v5 (Smart Band 10 Pro): same records as v3, with
                    // 6 more bytes of segment header.
                    //   23-byte segment header:
                    //     offset 0-3:   4 pad
                    //     offset 4-7:   int32 nr        — record count for this segment
                    //     offset 8-11:  int32 ts        — segment start, unix seconds
                    //     offset 12:    byte  phase     — only 0x7f observed
                    //     offset 13-16: int32 distance  — meters
                    //     offset 17-20: int32 duration  — seconds
                    //     offset 21-22: int16 avg speed — 0.1 km/h
                    //   Validated against the paired summary: the records tile the file exactly,
                    //   their distances sum to DISTANCE_METERS, and HR min/avg/max and top speed
                    //   match the summary.
                    expectedSignature = Arrays.copyOfRange(bytes, 8, 8 + OUTDOOR_CYCLING_BITMAP.length);
                    segmentHeaderSize = 23;
                    recordSize = 7;
                    tsPosition = 8;
                    nrPosition = 4;
                    layoutCode = 113; // synthetic: outdoor-cycling record shape
                } else {
                    LOG.warn("Unknown v5 DETAILS signature: {}",
                            GB.hexdump(bytes, 8, Math.min(5, bytes.length - 8)));
                    return null;
                }
                break;
            case 6:
                // v6 reused across sport types. Dispatch by signature.
                if (matchesBitmap(fileId, bytes, XiaomiActivityFileId.Subtype.SPORTS_TREADMILL, TREADMILL_V6_BITMAP)) {
                    // SPORTS_TREADMILL: Signature FF FF 8B FF (4 bytes), segment header 13 bytes,
                    // 12-byte records, decoded in case 6.
                    // Segment header layout:
                    //   offset 0-3:  int32 nr            — record count for this segment
                    //   offset 4-7:  int32 ts            — segment start, unix seconds
                    //   offset 8:    byte  phase         — only 0x7f observed; semantic unconfirmed
                    //   offset 9-12: int32 distance      — meters; matches summary DISTANCE_METERS
                    expectedSignature = Arrays.copyOfRange(bytes, 8, 8 + TREADMILL_V6_BITMAP.length);
                    segmentHeaderSize = 13;
                    recordSize = 12;
                    tsPosition = 4;
                    nrPosition = 0;
                } else if (matchesBitmap(fileId, bytes, XiaomiActivityFileId.Subtype.SPORTS_INDOOR_CYCLING, INDOOR_CYCLING_V6_BITMAP)) {
                    // SPORTS_INDOOR_CYCLING (subtype 0x07) v6: signature DF BB BB BF.
                    //   13-byte segment header (same shape as treadmill): int32 nr | int32 ts |
                    //   byte phase (0x7f only observed) | int32 distance.
                    //   9-byte records — HR decoded in case 116. Validated against the paired
                    //   indoor cycling summary: the max per-record HR matched the summary HR_MAX.
                    expectedSignature = Arrays.copyOfRange(bytes, 8, 8 + INDOOR_CYCLING_V6_BITMAP.length);
                    segmentHeaderSize = 13;
                    recordSize = 9;
                    tsPosition = 4;
                    nrPosition = 0;
                    layoutCode = 116; // synthetic: indoor-cycling-v6 record shape
                } else {
                    LOG.warn("Unknown v6 DETAILS signature: {}",
                            GB.hexdump(bytes, 8, Math.min(4, bytes.length - 8)));
                    return null;
                }
                break;
            case 9:
                // SPORTS_OUTDOOR_WALKING_V2 v9: 27-byte segment header like v8 (see
                // parseRunWalkRecords), but 25-byte records that the v8 group sizes do not
                // account for, so this bitmap is matched as a fixed signature.
                expectedSignature = new byte[]{
                        (byte) 0xFF, (byte) 0xFF, (byte) 0xF8, (byte) 0xBF,
                        (byte) 0xFB, (byte) 0xBB, (byte) 0xBF
                };
                segmentHeaderSize = 27;
                recordSize = 25;
                tsPosition = 8;
                nrPosition = 4;
                layoutCode = 109;
                break;
            default:
                LOG.warn("Unable to parse workout details version {}", version);
                return null;
        }

        // Validate signature at byte offset 8 (after 7-byte fileId + 1 padding)
        final byte[] actualSignature = Arrays.copyOfRange(bytes, 8, 8 + expectedSignature.length);
        if (!Arrays.equals(expectedSignature, actualSignature)) {
            LOG.warn("Unexpected signature for v{}: expected {} got {}",
                    version, GB.hexdump(expectedSignature), GB.hexdump(actualSignature));
            return null;
        }

        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.limit(buf.limit() - 4); // strip CRC32

        buf.get(new byte[7]); // skip fileId
        final byte padding = buf.get();
        if (padding != 0) {
            LOG.warn("Expected 0 padding after fileId, got {}", padding);
        }
        buf.get(new byte[expectedSignature.length]); // skip signature

        final List<WorkoutDetailRecord> records = new ArrayList<>();

        while (buf.position() < buf.limit()) {
            // Read segment header
            if (buf.remaining() < segmentHeaderSize) {
                LOG.warn("Not enough bytes for segment header, stopping");
                break;
            }
            final byte[] segmentHeaderBytes = new byte[segmentHeaderSize];
            buf.get(segmentHeaderBytes);
            final ByteBuffer segHdr = ByteBuffer.wrap(segmentHeaderBytes).order(ByteOrder.LITTLE_ENDIAN);
            int nr = segHdr.getInt(nrPosition);
            int ts = segHdr.getInt(tsPosition);
            // Treadmill v5 (205) has no record-count field in its header — treat the file as a
            // single segment and consume all remaining record bytes instead.
            if (layoutCode == 205) {
                nr = buf.remaining() / recordSize;
            }
            LOG.debug("Segment: {} records starting at ts={}", nr, ts);

            // Per-segment interval metadata — only for layouts with confirmed phase semantics.
            // Rowing v4: header offset 8 = phase (0x81 active / 0x82 rest), offset 9-12 = strokes.
            // Stamped onto the first record of the segment so getActivityTrack can open one
            // ActivityTrack segment (→ FIT lap) per interval.
            final boolean segmentedLayout = layoutCode == 4;
            ActivityTrack.SegmentIntensity segmentIntensity = null;
            Integer segmentStrokes = null;
            if (segmentedLayout) {
                final byte phase = segHdr.get(8);
                segmentIntensity = phase == (byte) 0x81 ? ActivityTrack.SegmentIntensity.ACTIVE
                        : phase == (byte) 0x82 ? ActivityTrack.SegmentIntensity.REST
                        : ActivityTrack.SegmentIntensity.UNKNOWN;
                final int strokes = segHdr.getInt(9);
                segmentStrokes = strokes > 0 ? strokes : null;
            }
            boolean firstInSegment = true;
            final int segmentFirstRecord = records.size();

            final int segmentEnd = buf.position() + nr * recordSize;
            if (segmentEnd > buf.limit()) {
                LOG.warn("Segment claims {} records but only {} bytes remain, clamping",
                        nr, buf.remaining());
            }

            while (buf.position() < Math.min(segmentEnd, buf.limit())) {
                final WorkoutDetailRecord r = new WorkoutDetailRecord();
                r.ts = ts;
                if (segmentedLayout && firstInSegment) {
                    r.segmentStart = true;
                    r.segmentIntensity = segmentIntensity;
                    r.segmentStrokes = segmentStrokes;
                }
                firstInSegment = false;

                switch (layoutCode) {
                    case 2:
                        r.hr = buf.get() & 0xFF;
                        buf.get(); // calories — not stored (see DailyDetailsParser for calorie parsing)
                        break;
                    case 3:
                        buf.get(); // calories
                        r.hr = buf.get() & 0xFF;
                        // Raw speed int32 from device; unit TBD — kept for potential future calibration,
                        // not exposed to ActivityPoint until unit is confirmed empirically.
                        r.speedRaw = buf.getInt();
                        break;
                    case 4:
                        // Rowing 3-byte record. Stroke rate already in strokes/min (matches summary
                        // averageCadence unit). Verified against rowing workout: per-record stroke
                        // rate range 12-71 with avg ~34, summary range typically 30-40 spm.
                        r.hr = buf.get() & 0xFF;
                        buf.get(); // events/flags (mostly 0, occasional 1)
                        r.cadence = buf.get() & 0xFF;
                        break;
                    case 5:
                        r.steps = buf.get() & 0xFF;
                        r.hr = buf.get() & 0xFF;
                        buf.get(); // events/flags
                        buf.get(); // calories — not stored (see DailyDetailsParser for calorie parsing)
                        r.spo2 = buf.get() & 0xFF;
                        r.cadence = buf.get() & 0xFF;
                        buf.getShort(); // pace
                        break;
                    case 6:
                        // SPORTS_TREADMILL v6: 12-byte record, one byte per bitmap nibble except
                        // where noted. Checked on 55 treadmill workouts from two bands against the
                        // paired summary and segment headers.
                        //   byte 0:     steps in the low nibble; their sum equals summary steps
                        //   byte 1:     HR (bpm)
                        //   byte 2:     distance covered in that second (uint8, dm); its sum
                        //               equals the segment header distance and summary distance
                        //   byte 3:     stride (cm); median close to the summary step length
                        //   bytes 4-8:  reserved (5 bytes)
                        //   byte 9:     cadence (steps/min); its maximum equals summary max cadence
                        //   bytes 10-11: pace (uint16 LE, s/km); its fastest value equals the
                        //               summary fastest pace, but integrated it gives 72% to 97%
                        //               of the distance. Speed combines it with byte 2, see
                        //               setSpeedFromDistance.
                        r.steps = buf.get() & 0x0F;
                        r.hr = buf.get() & 0xFF;
                        r.distanceDm = buf.get() & 0xFF;
                        buf.get();    // stride
                        buf.getInt(); // reserved
                        buf.get();    // reserved
                        r.cadence = buf.get() & 0xFF;
                        r.paceSecPerKm = buf.getShort() & 0xFFFF;
                        break;
                    case 103:
                        // v3 FREESTYLE (Mi Band 8). 4-byte record: HR at byte 0, byte 1 events/flags,
                        // bytes 2-3 unknown (mostly zero; occasional small values).
                        r.hr = buf.get() & 0xFF;
                        buf.get(); // events/flags
                        buf.get(); // unknown
                        buf.get(); // unknown
                        break;
                    case 205:
                        // SPORTS_TREADMILL v5: 8-byte record.
                        buf.get();                       // reserved (1 byte)
                        r.hr = buf.get() & 0xFF;
                        buf.get();                       // reserved (1 byte)
                        // Belt speed shown on the treadmill display, in units of 0.1 km/h.
                        r.speedRaw = buf.get() & 0xFF;
                        buf.getInt();                    // reserved (4 bytes)
                        break;
                    case 111:
                        // SPORTS_ELLIPTICAL v3: 3-byte record. Checked against the paired summary:
                        // the step nibbles sum to its steps and the cadence peak equals its max.
                        //   byte 0: steps in the low nibble
                        //   byte 1: HR (bpm)
                        //   byte 2: cadence (steps/min)
                        r.steps = buf.get() & 0x0F;
                        r.hr = buf.get() & 0xFF;
                        r.cadence = buf.get() & 0xFF;
                        break;
                    case 113:
                        // SPORTS_OUTDOOR_CYCLING v3 and v5: 7-byte record.
                        //   byte 0:    reserved
                        //   byte 1:    HR (uint8 bpm)
                        //   byte 2:    reserved
                        //   byte 3:    distance covered in this second (uint8 m)
                        //   bytes 4-5: speed (uint16 LE, 0.1 km/h)
                        //   byte 6:    reserved
                    {
                        buf.get();
                        r.hr = buf.get() & 0xFF;
                        buf.get();
                        buf.get();
                        final int speedTenthsKmh = buf.getShort() & 0xFFFF;
                        r.speedMps = speedTenthsKmh / 36f;
                        buf.get();
                        break;
                    }
                    case 116:
                        // SPORTS_INDOOR_CYCLING v6: 9-byte record. HR at byte 1.
                        buf.get();                       // reserved (1 byte)
                        r.hr = buf.get() & 0xFF;
                        buf.getInt();                    // reserved (4 bytes)
                        buf.getShort();                  // reserved (2 bytes)
                        buf.get();                       // reserved (1 byte)
                        break;
                    case 109:
                        // SPORTS_OUTDOOR_WALKING_V2 v9: 25-byte record sharing its first two
                        // bytes with the v5/v8 run/walk records. Verified on a real walk: summing the
                        // step nibbles reproduces the step count the watch reports in its own
                        // summary, and the heart rate column reproduces its minimum and maximum.
                        // The remaining bytes are not identified - notably cadence is no longer
                        // where v8 keeps it.
                    {
                        final int caloriesAndSteps = buf.get() & 0xFF;
                        r.steps = caloriesAndSteps & 0x0F;
                        r.hr = buf.get() & 0xFF;
                        buf.get(new byte[23]); // 23 unidentified bytes
                        break;
                    }
                }

                LOG.trace("Sample: ts={} hr={}", ts, r.hr);
                records.add(r);
                ts++;
            }
            setSpeedFromDistance(records, segmentFirstRecord);
        }

        return records;
    }
}
