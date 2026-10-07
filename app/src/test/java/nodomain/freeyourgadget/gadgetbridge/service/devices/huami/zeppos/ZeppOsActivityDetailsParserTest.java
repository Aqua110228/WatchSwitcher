package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos;

import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import static org.junit.Assert.assertEquals;

public class ZeppOsActivityDetailsParserTest extends TestBase {
    @Test
    public void decodesCumulativeDistanceInCentimetres() throws Exception {
        final long start = 1700000000000L;
        final ByteBuffer data = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 1).put((byte) 12).putInt(0).putLong(start);
        final int[] distances = {0, 118, 1045, 2719, 2719};
        for (int i = 0; i < distances.length; i++) {
            data.put((byte) 0x1F).put((byte) 0x09).put((byte) 6);
            data.putShort((short) (i * 1000)).putInt(distances[i]);
            // A second metric at the same time commits the updated distance.
            data.put((byte) 8).put((byte) 3).putShort((short) (i * 1000)).put((byte) 87);
        }
        final byte[] bytes = new byte[data.position()];
        data.flip();
        data.get(bytes);
        final BaseActivitySummary summary = new BaseActivitySummary();
        final User user = new User();
        user.setId(1L);
        final Device device = new Device();
        device.setId(1L);
        summary.setUser(user);
        summary.setDevice(device);
        final ZeppOsActivityTrack track = new ZeppOsActivityDetailsParser(summary).parse(bytes);
        final List<ActivityPoint> points = track.getSegments().get(0);
        assertEquals(distances.length, points.size());
        for (int i = 0; i < distances.length; i++) {
            assertEquals(start + i * 1000L, points.get(i).getTime().getTime());
            assertEquals(distances[i] / 100.0, points.get(i).getDistance(), 0.000001);
        }
    }

    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void localTest() throws Exception {
        final byte[] bytes = Files.readAllBytes(Paths.get("/storage/downloads/raw_details.bin"));

        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setRawSummaryData(bytes);
        summary.setBaseLatitude(0);
        summary.setBaseLongitude(0);
        summary.setBaseAltitude(0);

        final ZeppOsActivityDetailsParser parser = new ZeppOsActivityDetailsParser(summary);
        final ZeppOsActivityTrack track = parser.parse(bytes);
    }
}
