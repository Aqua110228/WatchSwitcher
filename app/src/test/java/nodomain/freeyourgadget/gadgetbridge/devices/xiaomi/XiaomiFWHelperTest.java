package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.util.Size;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand7ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand9Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand9ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiSmartBandProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch2LiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch5ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch5Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.RedmiWatch5LiteCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS3Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.XiaomiWatchS4Coordinator;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

@SuppressWarnings("SameParameterValue")
public class XiaomiFWHelperTest extends TestBase {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testMiBand10() throws IOException {
        assertWatchface(
            new MiBand10Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003132333335373732" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000006d6962616e6431300000000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "00000000d400080280ba0600",
            "123357723",
            "miband10",
            new Size(212, 520)
        );
    }

    @Test
    public void testMiBand7Pro() throws IOException {
        assertWatchface(
            new MiBand7ProCoordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003134333534333532" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000006d6962616e643770726f000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "00080000dc006601100c0000",
            "143543523",
            "miband7pro",
            new Size(220, 358)
        );
    }

    @Test
    public void testMiBand8() throws IOException {
        assertWatchface(
            new MiBand8Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003134333534333532" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000006d6962616e6438000000000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "030400007a00360186030000",
            "143543523",
            "miband8",
            new Size(122, 310)
        );
    }

    @Test
    public void testMiBand8Pro() throws IOException {
        assertWatchface(
            new MiBand8ProCoordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003134333233343532" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000006d6962616e643870726f000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "000400005001e001d6180000",
            "143234523",
            "miband8pro",
            new Size(336, 480)
        );
    }

    @Test
    public void testMiBand9() throws IOException {
        assertWatchface(
            new MiBand9Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003132343536343536" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000006d6962616e6439000000000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "06040000c000ea019c0b0000",
            "124564563",
            "miband9",
            new Size(192, 490)
        );
    }

    @Test
    public void testMiBand9Pro() throws IOException {
        assertWatchface(
            new MiBand9ProCoordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003132333335373732" +
                "340000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000006d6962616e643970726f000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "00040000e6004801a70b0000",
            "123357724",
            "miband9pro",
            new Size(230, 328)
        );
    }

    @Test
    public void testRedmiSmartBandPro() throws IOException {
        assertWatchface(
            new RedmiSmartBandProCoordinator(),
            "5aa534120000000000000000000000000107000000000000000000000100000000010000000000003438393233343532" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007265646d6962616e6470726f00000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "040000006e00d000200c0100",
            "489234523",
            "redmibandpro",
            new Size(110, 208)
        );
    }

    @Test
    public void testRedmiWatch2Lite() throws IOException {
        assertWatchface(
            new RedmiWatch2LiteCoordinator(),
            "5aa534120000000000000000000000000107000000000000000000000100000000010000000000003332343233343233" +
                "340000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007265646d697761746368326c69746500000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "04000000b200b2004c730100",
            "324234234",
            "redmiwatch2lite",
            new Size(178, 178)
        );
    }

    @Test
    public void testRedmiWatch3() throws IOException {
        assertWatchface(
            new RedmiWatch3Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003332343233343233" +
                "340000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007265646d697761746368330000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "03040000ea000e01de050000",
            "324234234",
            "redmiwatch3",
            new Size(234, 270)
        );
    }

    @Test
    public void testRedmiWatch4() throws IOException {
        assertWatchface(
            new RedmiWatch4Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003635343332343233" +
                "340000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007265646d697761746368340000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "00040000ea000e01c2090000",
            "654324234",
            "redmiwatch4",
            new Size(234, 270)
        );
    }

    @Test
    public void testRedmiWatch5() throws IOException {
        assertWatchface(
            new RedmiWatch5Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003635343336353436" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007265646d697761746368350000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "00040000b001020231220000",
            "654365463",
            "redmiwatch5",
            new Size(432, 514)
        );
    }

    @Test
    public void testRedmiWatch5Active() throws IOException {
        assertWatchface(
            new RedmiWatch5ActiveCoordinator(),
            "5aa534120000010000000000000000000008000000000100000000000100000000010000000000003635343336353436" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007265646d697761746368356163746976650000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "280c0000b400d800dc010000",
            "654365463",
            "redmiwatch5active",
            new Size(180, 216)
        );
    }

    @Test
    public void testRedmiWatch5Lite() throws IOException {
        assertWatchface(
            new RedmiWatch5LiteCoordinator(),
            "5aa534120000010000000000000000000008000000000100000000000100000000010000000000003635343336353436" +
                "330000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007265646d697761746368356c69746500000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "280c0000f4002a01f8020000",
            "654365463",
            "redmiwatch5lite",
            new Size(244, 298)
        );
    }

    @Test
    public void testXiaomiWatchS3() throws IOException {
        assertWatchface(
            new XiaomiWatchS3Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003332343233343233" +
                "340000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007869616f6d6977617463687333000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "000400004601460161100000",
            "324234234",
            "xiaomiwatchs3",
            new Size(326, 326)
        );
    }

    @Test
    public void testXiaomiWatchS4() throws IOException {
        assertWatchface(
            new XiaomiWatchS4Coordinator(),
            "5aa534120000000000000000000000000008000000000000000000000100000000010000000000003332343233343233" +
                "340000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000007869616f6d6977617463687334000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000",
            256,
            "000400004601460161100000",
            "324234234",
            "xiaomiwatchs4",
            new Size(326, 326)
        );
    }

    private void assertWatchface(final XiaomiCoordinator coordinator,
                                 final String headerHex,
                                 final int previewOffset,
                                 final String previewHeaderHex,
                                 final String id,
                                 final String name,
                                 final Size previewSize) throws IOException {
        final byte[] header = GB.hexStringToByteArray(headerHex);
        final byte[] previewHeader = GB.hexStringToByteArray(previewHeaderHex);
        final byte[] bytes = new byte[previewOffset + previewHeader.length];
        System.arraycopy(header, 0, bytes, 0, header.length);
        System.arraycopy(previewHeader, 0, bytes, previewOffset, previewHeader.length);

        final XiaomiFWHelper helper = parse(bytes);

        assertTrue(helper.isValid());
        assertTrue(helper.isWatchface());
        assertEquals(id, helper.getId());
        assertEquals(name, helper.getName());
        assertEquals(previewSize, helper.getPreviewSize());

        assertTrue(coordinator.getWatchfacePreviewSizes().contains(previewSize) ||
            previewSize.equals(coordinator.getScreenSize()));
    }

    static byte[] buildWatchfaceV2(final int format,
                                   final int compression,
                                   final int width,
                                   final int height,
                                   final byte[] data) {
        final int previewOffset = 168;
        final ByteBuffer bb = ByteBuffer.allocate(previewOffset + 12 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[]{0x5a, (byte) 0xa5, 0x34, 0x12});
        bb.putInt(0x20, previewOffset);
        bb.put(0x28, "123456789".getBytes(StandardCharsets.US_ASCII));
        bb.put(0x68, "Test face".getBytes(StandardCharsets.UTF_8));
        bb.position(previewOffset);
        bb.put((byte) format);
        bb.put((byte) compression);
        bb.putShort((short) 0);
        bb.putShort((short) width);
        bb.putShort((short) height);
        bb.putInt(data.length);
        bb.put(data);
        return bb.array();
    }

    private XiaomiFWHelper parse(final byte[] bytes) throws IOException {
        final File file = tempFolder.newFile();
        Files.write(file.toPath(), bytes);
        return new XiaomiFWHelper(Uri.fromFile(file), getContext());
    }
}
