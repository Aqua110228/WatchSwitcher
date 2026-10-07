package nodomain.freeyourgadget.gadgetbridge.util;

import org.junit.Test;
import org.junit.Assert;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static nodomain.freeyourgadget.gadgetbridge.util.GB.hexStringToByteArray;

public class LEB128UtilsTest {
    @Test
    public void testEncodeUnsigned() {
        Assert.assertArrayEquals(
                hexStringToByteArray("00"),
                LEB128Utils.encodeUnsigned(0));

        Assert.assertArrayEquals(
                hexStringToByteArray("7F"),
                LEB128Utils.encodeUnsigned(127));

        Assert.assertArrayEquals(
                hexStringToByteArray("8001"),
                LEB128Utils.encodeUnsigned(128));

        Assert.assertArrayEquals(
                hexStringToByteArray("FF7F"),
                LEB128Utils.encodeUnsigned(16383));

        Assert.assertArrayEquals(
                hexStringToByteArray("808001"),
                LEB128Utils.encodeUnsigned(16384));

        Assert.assertArrayEquals(
                hexStringToByteArray("FFFFFFFFFFFFFFFFFF01"),
                LEB128Utils.encodeUnsigned(-1));
    }

    @Test
    public void testDecodeUnsigned() {
        Assert.assertEquals(0, LEB128Utils.decodeUnsigned(
                    hexStringToByteBuffer("00")));

        Assert.assertEquals(127, LEB128Utils.decodeUnsigned(
                    hexStringToByteBuffer("7F")));

        Assert.assertEquals(128, LEB128Utils.decodeUnsigned(
                    hexStringToByteBuffer("8001")));

        Assert.assertEquals(16384, LEB128Utils.decodeUnsigned(
                    hexStringToByteBuffer("808001")));

        Assert.assertEquals(-1, LEB128Utils.decodeUnsigned(
                    hexStringToByteBuffer("FFFFFFFFFFFFFFFFFF01")));
    }

    @Test
    public void decodeThrowsOnTruncatedInput() {
        Assert.assertThrows(BufferUnderflowException.class,
                () -> LEB128Utils.decodeUnsigned(hexStringToByteBuffer("80")));
        Assert.assertThrows(BufferUnderflowException.class,
                () -> LEB128Utils.decodeUnsigned(hexStringToByteBuffer("")));
    }

    private static ByteBuffer hexStringToByteBuffer(final String hex) {
        return ByteBuffer.wrap(GB.hexStringToByteArray(hex));
    }
}
