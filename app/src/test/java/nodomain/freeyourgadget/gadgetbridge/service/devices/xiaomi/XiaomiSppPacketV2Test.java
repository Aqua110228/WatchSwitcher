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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import org.junit.Assert;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Frame layout is {@code a5 a5 | type | seq | len (LE) | crc (LE) | payload}.
 */
public class XiaomiSppPacketV2Test extends TestBase {

    /** Empty payload, so the CRC is zero. */
    private static byte[] emptyFrame(final int type, final int sequenceNumber) {
        return new byte[]{
                (byte) 0xa5, (byte) 0xa5,
                (byte) type,
                (byte) sequenceNumber,
                0x00, 0x00,
                0x00, 0x00,
        };
    }

    @Test
    public void decodesAck() {
        final XiaomiSppPacketV2 packet = XiaomiSppPacketV2.decode(emptyFrame(XiaomiSppPacketV2.PACKET_TYPE_ACK, 0xf2));

        Assert.assertNotNull(packet);
        Assert.assertEquals(XiaomiSppPacketV2.PACKET_TYPE_ACK, packet.getPacketType());
        Assert.assertEquals(0xf2, packet.getSequenceNumber());
        Assert.assertTrue(packet instanceof XiaomiSppPacketV2.AckPacket);
    }

    @Test
    public void decodesNack() {
        // Captured from a Mi Band 10 refusing a command: type 0, echoing the sequence number of
        // the packet just sent, empty payload. Previously this fell through to a warning and was
        // dropped, so a rejected command looked identical to a delivered one.
        final byte[] captured = new byte[]{
                (byte) 0xa5, (byte) 0xa5, 0x00, (byte) 0xf6, 0x00, 0x00, 0x00, 0x00,
        };

        final XiaomiSppPacketV2 packet = XiaomiSppPacketV2.decode(captured);

        Assert.assertNotNull(packet);
        Assert.assertEquals(XiaomiSppPacketV2.PACKET_TYPE_NACK, packet.getPacketType());
        Assert.assertEquals(0xf6, packet.getSequenceNumber());
        Assert.assertTrue(packet instanceof XiaomiSppPacketV2.NackPacket);
    }

    @Test
    public void rejectsShortBuffer() {
        Assert.assertNull(XiaomiSppPacketV2.decode(new byte[]{(byte) 0xa5, (byte) 0xa5, 0x01}));
    }

    @Test
    public void rejectsTruncatedPayload() {
        // Declares a six byte payload but carries none.
        final byte[] frame = new byte[]{
                (byte) 0xa5, (byte) 0xa5, (byte) XiaomiSppPacketV2.PACKET_TYPE_DATA, 0x01,
                0x06, 0x00,
                0x00, 0x00,
        };

        Assert.assertNull(XiaomiSppPacketV2.decode(frame));
    }

    @Test
    public void rejectsChecksumMismatch() {
        final byte[] frame = new byte[]{
                (byte) 0xa5, (byte) 0xa5, (byte) XiaomiSppPacketV2.PACKET_TYPE_DATA, 0x01,
                0x02, 0x00,
                (byte) 0xff, (byte) 0xff,
                0x01, 0x02,
        };

        Assert.assertNull(XiaomiSppPacketV2.decode(frame));
    }
}
