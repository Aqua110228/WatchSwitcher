package nodomain.freeyourgadget.gadgetbridge.devices.test.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;

/**
 * Builds the small track - a circle around a starting point, with one point every 5 seconds.
 */
public class TestActivityTrackProvider implements ActivityTrackProvider {
    private static final long POINT_INTERVAL_MILLIS = 5000L;
    private static final double BASE_LATITUDE = 38.7;
    private static final double BASE_LONGITUDE = -9.1;
    private static final double METERS_PER_DEGREE = 111_320d;

    @Nullable
    @Override
    public ActivityTrack getActivityTrack(@NonNull final BaseActivitySummary summary) {
        final ActivityKind activityKind = ActivityKind.fromCode(summary.getActivityKind());
        if (!TestActivitySummaryParser.hasGps(activityKind)) {
            return null;
        }

        final long startTime = summary.getStartTime().getTime();
        final long endTime = summary.getEndTime().getTime();
        final float speed = TestActivitySummaryParser.averageSpeed(activityKind, startTime);
        final double radiusMeters = speed * ((endTime - startTime) / 1000d) / (2 * Math.PI);

        final double startLatitude = BASE_LATITUDE + TestDeviceRand.randFloat(startTime, -0.2f, 0.2f);
        final double startLongitude = BASE_LONGITUDE + TestDeviceRand.randFloat(startTime + 1, -0.2f, 0.2f);
        final double baseAltitude = TestDeviceRand.randInt(startTime, 0, 200);

        final ActivityTrack track = new ActivityTrack();
        track.setName(activityKind.name());
        track.setBaseTime(summary.getStartTime());

        double distance = 0;

        for (long ts = startTime; ts < endTime; ts += POINT_INTERVAL_MILLIS) {
            final double progress = (double) (ts - startTime) / (endTime - startTime);
            final double angle = progress * 2 * Math.PI;
            final double latitude = startLatitude + (radiusMeters * (1 - Math.cos(angle))) / METERS_PER_DEGREE;
            final double longitude = startLongitude + (radiusMeters * Math.sin(angle))
                / (METERS_PER_DEGREE * Math.cos(Math.toRadians(startLatitude)));
            final double altitude = baseAltitude + 30 * Math.sin(angle * 3);

            distance += speed * (POINT_INTERVAL_MILLIS / 1000d);

            final ActivityPoint point = new ActivityPoint(new Date(ts));
            point.setLocation(new GPSCoordinate(longitude, latitude, altitude));
            point.setAltitude(altitude);
            point.setDistance(distance);
            point.setSpeed(speed * TestDeviceRand.randFloat(ts, 0.8f, 1.2f));
            point.setHeartRate(TestDeviceRand.randInt(ts, 110, 165));
            point.setCadence(TestDeviceRand.randInt(ts, 70, 95));
            point.setRespiratoryRate(TestDeviceRand.randFloat(ts, 15f, 30f));
            track.addTrackPoint(point);
        }

        return track;
    }
}
