/*  Copyright (C) 2026 Dominic Monroe

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.bose;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class BoseProtocolTest {

    private static byte[] hex(final String hex) {
        final String cleaned = hex.replaceAll("[\\s:]", "");
        final byte[] bytes = new byte[cleaned.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static void assertHexEquals(final byte[] expected, final byte[] actual) {
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testConnectHandshake() {
        assertHexEquals(hex("00 01 01 00"), BoseProtocol.connectHandshake());
    }

    @Test
    public void testGetBattery() {
        assertHexEquals(hex("02 02 01 00"), BoseProtocol.getBattery());
    }

    @Test
    public void testEnableStatusNotifications() {
        assertHexEquals(hex("09 02 02 02 01 04"),
                BoseProtocol.enableNotificationsForFunctionBlocks(BoseProtocol.BLOCK_STATUS));
        assertHexEquals(hex("09 02 02 02 01 14"),
                BoseProtocol.enableNotificationsForFunctionBlocks(BoseProtocol.BLOCK_STATUS,
                        BoseProtocol.BLOCK_DEVICE_MANAGEMENT));
        assertHexEquals(hex("09 02 02 02 01 34"),
                BoseProtocol.enableNotificationsForFunctionBlocks(BoseProtocol.BLOCK_STATUS,
                        BoseProtocol.BLOCK_DEVICE_MANAGEMENT, BoseProtocol.BLOCK_AUDIO_MANAGEMENT));
        assertHexEquals(hex("09 02 02 02 01 35"),
                BoseProtocol.enableNotificationsForFunctionBlocks(BoseProtocol.BLOCK_PRODUCT_INFO,
                        BoseProtocol.BLOCK_STATUS, BoseProtocol.BLOCK_DEVICE_MANAGEMENT,
                        BoseProtocol.BLOCK_AUDIO_MANAGEMENT));
        assertHexEquals(hex("09 02 02 02 01 37"),
                BoseProtocol.enableNotificationsForFunctionBlocks(BoseProtocol.BLOCK_PRODUCT_INFO,
                        BoseProtocol.BLOCK_SETTINGS, BoseProtocol.BLOCK_STATUS,
                        BoseProtocol.BLOCK_DEVICE_MANAGEMENT, BoseProtocol.BLOCK_AUDIO_MANAGEMENT));
    }

    @Test
    public void testAnrLevelMapping() {
        assertHexEquals(hex("01 06 02 01 00"), BoseProtocol.setAnr(0));
        assertHexEquals(hex("01 06 02 01 01"), BoseProtocol.setAnr(1));
        assertHexEquals(hex("01 06 02 01 02"), BoseProtocol.setAnr(2));
        assertHexEquals(hex("01 06 02 01 03"), BoseProtocol.setAnr(3));
    }

    @Test
    public void testCncLevelInvertedAndRepeated() {
        assertHexEquals(
                hex("01 05 02 02 05 01  01 05 02 02 05 01  01 05 02 02 05 01"),
                BoseProtocol.setCnc(5)
        );
        assertHexEquals(
                hex("01 05 02 02 00 01  01 05 02 02 00 01  01 05 02 02 00 01"),
                BoseProtocol.setCnc(10)
        );
        assertHexEquals(
                hex("01 05 02 02 0a 01  01 05 02 02 0a 01  01 05 02 02 0a 01"),
                BoseProtocol.setCnc(0)
        );
    }

    @Test
    public void testGetNoiseCancelling() {
        assertHexEquals(hex("01 05 01 00"), BoseProtocol.getCnc());
        assertHexEquals(hex("01 06 01 00"), BoseProtocol.getAnr());
    }

    @Test
    public void testDecodeCncLevel() {
        Assert.assertEquals(5, BoseProtocol.decodeCncLevel(hex("0b 05 01")));
        Assert.assertEquals(10, BoseProtocol.decodeCncLevel(hex("0b 00 01")));
        Assert.assertEquals(-1, BoseProtocol.decodeCncLevel(hex("0b 0b 01")));
        Assert.assertEquals(-1, BoseProtocol.decodeCncLevel(new byte[]{0x0b}));
    }

    @Test
    public void testDecodeAnrLevel() {
        Assert.assertEquals(0, BoseProtocol.decodeAnrLevel(hex("00 0b")));
        Assert.assertEquals(1, BoseProtocol.decodeAnrLevel(hex("01 0b")));
        Assert.assertEquals(2, BoseProtocol.decodeAnrLevel(hex("02 0b")));
        Assert.assertEquals(3, BoseProtocol.decodeAnrLevel(hex("03 0b")));
    }

    @Test
    public void testDecodeMultipoint() {
        assertHexEquals(hex("01 0a 01 00"), BoseProtocol.getMultipoint());
        assertHexEquals(hex("01 0a 02 01 01"), BoseProtocol.setMultipoint(true));
        assertHexEquals(hex("01 0a 02 01 00"), BoseProtocol.setMultipoint(false));
        Assert.assertEquals(Boolean.TRUE, BoseProtocol.decodeMultipointEnabled(hex("07")));
        Assert.assertEquals(Boolean.FALSE, BoseProtocol.decodeMultipointEnabled(hex("06")));
        Assert.assertEquals(Boolean.TRUE, BoseProtocol.decodeMultipointSupported(hex("03")));
        Assert.assertEquals(Boolean.FALSE, BoseProtocol.decodeMultipointDisableSupported(hex("03")));
        Assert.assertEquals(Boolean.TRUE, BoseProtocol.decodeMultipointDisableSupported(hex("05")));
    }

    @Test
    public void testVoicePrompts() {
        assertHexEquals(hex("01 03 01 00"), BoseProtocol.getVoicePrompts());
        assertHexEquals(hex("01 03 02 01 25"), BoseProtocol.setVoicePrompts(true, 5));
        assertHexEquals(hex("01 03 02 01 00"), BoseProtocol.setVoicePrompts(false, 0));
        Assert.assertEquals(Boolean.TRUE, BoseProtocol.decodeVoicePromptsEnabled(hex("21")));
        Assert.assertEquals(1, BoseProtocol.decodeVoicePromptsLanguage(hex("21")));
        Assert.assertEquals(Boolean.FALSE, BoseProtocol.decodeVoicePromptsEnabled(hex("0e")));
        Assert.assertEquals(14, BoseProtocol.decodeVoicePromptsLanguage(hex("0e")));
        Assert.assertEquals(Boolean.TRUE, BoseProtocol.decodeVoicePromptsTogglable(hex("a1")));
        Assert.assertEquals(Boolean.FALSE, BoseProtocol.decodeVoicePromptsTogglable(hex("21")));
        Assert.assertNull(BoseProtocol.decodeVoicePromptsEnabled(new byte[0]));
        Assert.assertEquals(0x0001815E, BoseProtocol.decodeVoicePromptsSupportedMask(hex("a1 00 01 81 5e")));
        Assert.assertEquals(-1, BoseProtocol.decodeVoicePromptsSupportedMask(hex("a1")));
    }

    @Test
    public void testStandbyTimer() {
        assertHexEquals(hex("01 04 01 00"), BoseProtocol.getStandbyTimer());
        assertHexEquals(hex("01 04 02 01 3c"), BoseProtocol.setStandbyTimer(60));
        assertHexEquals(hex("01 04 02 01 b4"), BoseProtocol.setStandbyTimer(180));
        assertHexEquals(hex("01 04 02 02 a0 05"), BoseProtocol.setStandbyTimer(1440));
        Assert.assertEquals(60, BoseProtocol.decodeStandbyTimerMinutes(hex("3c")));
        Assert.assertEquals(1440, BoseProtocol.decodeStandbyTimerMinutes(hex("a0 05")));
        Assert.assertEquals(-1, BoseProtocol.decodeStandbyTimerMinutes(new byte[0]));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStandbyTimerRejectsOutOfRangeValue() {
        BoseProtocol.setStandbyTimer(0x10000);
    }

    @Test
    public void testDecodePairedDevices() {
        final byte[] payload = hex("03  aa bb cc dd ee ff  11 22 33 44 55 66  01 02 03 04 05 06");
        final List<BoseProtocol.PairedDevice> devices = BoseProtocol.decodePairedDevices(payload);
        Assert.assertEquals(3, devices.size());
        Assert.assertEquals("AA:BB:CC:DD:EE:FF", devices.get(0).mac);
        Assert.assertTrue(devices.get(0).connected);
        Assert.assertEquals("11:22:33:44:55:66", devices.get(1).mac);
        Assert.assertTrue(devices.get(1).connected);
        Assert.assertEquals("01:02:03:04:05:06", devices.get(2).mac);
        Assert.assertFalse(devices.get(2).connected);
    }

    @Test
    public void testDecodePairedDevicesEmpty() {
        Assert.assertTrue(BoseProtocol.decodePairedDevices(hex("00")).isEmpty());
        Assert.assertTrue(BoseProtocol.decodePairedDevices(new byte[0]).isEmpty());
    }

    @Test
    public void testMacConversion() {
        final byte[] bytes = BoseProtocol.macToBytes("AA:BB:CC:DD:EE:FF");
        Assert.assertEquals("AA:BB:CC:DD:EE:FF", BoseProtocol.bytesToMac(bytes, 0));
        Assert.assertEquals("BB:CC:DD:EE:FF:00", BoseProtocol.bytesToMac(hex("aa bb cc dd ee ff 00"), 1));
    }

    @Test
    public void testDecodeDeviceInfoName() {
        final byte[] nonBose = hex("aa bb cc dd ee ff  00 02 03  50 68 6f 6e 65");
        Assert.assertEquals("Phone", BoseProtocol.decodeDeviceInfoName(nonBose));
        final byte[] bose = hex("aa bb cc dd ee ff  05 40 24 01  50 72 6f 20 48 50");
        Assert.assertEquals("Pro HP", BoseProtocol.decodeDeviceInfoName(bose));
    }

    @Test
    public void testDecodeDeviceInfoSummary() {
        final byte[] nonBose = hex("aa bb cc dd ee ff  00 02 03  50 68 6f 6e 65");
        final String summary = BoseProtocol.decodeDeviceInfoSummary(nonBose);
        Assert.assertNotNull(summary);
        Assert.assertTrue(summary, summary.contains("AA:BB:CC:DD:EE:FF"));
        Assert.assertTrue(summary, summary.contains("Phone"));

        final byte[] bose = hex("aa bb cc dd ee ff  05 40 24 01  50 72 6f 20 48 50");
        final String boseSummary = BoseProtocol.decodeDeviceInfoSummary(bose);
        Assert.assertNotNull(boseSummary);
        Assert.assertTrue(boseSummary, boseSummary.contains("(connected)"));
        Assert.assertTrue(boseSummary, boseSummary.contains("Pro HP"));
    }

    @Test
    public void testButtons() {
        assertHexEquals(hex("01 09 01 00"), BoseProtocol.getButtons());
        assertHexEquals(hex("01 09 02 03 80 05 03"),
                BoseProtocol.setActionButton(0x80, 0x05, BoseProtocol.BUTTON_MODE_BATTERY_LEVEL));
        final BoseProtocol.ButtonConfig config =
                BoseProtocol.decodeButtonConfig(hex("80 05 03 00 01 20 88 00 00 00 80"));
        Assert.assertNotNull(config);
        Assert.assertEquals(0x80, config.buttonId);
        Assert.assertEquals(0x05, config.eventType);
        Assert.assertEquals(BoseProtocol.BUTTON_MODE_BATTERY_LEVEL, config.currentMode);
        Assert.assertEquals(0x00012088, config.supportedMask);
        Assert.assertEquals(0x00000080, config.unavailableMask);
        Assert.assertNull(BoseProtocol.decodeButtonConfig(new byte[]{0x01, 0x02}));
    }

    @Test
    public void testDecodeActiveSource() {
        final byte[] payload = hex("03 00 01 aa bb cc dd ee ff");
        Assert.assertEquals(BoseProtocol.SOURCE_BLUETOOTH, BoseProtocol.decodeActiveSourceType(payload));
        Assert.assertEquals("AA:BB:CC:DD:EE:FF", BoseProtocol.decodeActiveSourceMac(payload));

        final byte[] aux = hex("03 00 02");
        Assert.assertEquals(BoseProtocol.SOURCE_AUXILIARY, BoseProtocol.decodeActiveSourceType(aux));
        Assert.assertNull(BoseProtocol.decodeActiveSourceMac(aux));

        final byte[] none = hex("03 00 00");
        Assert.assertEquals(BoseProtocol.SOURCE_NONE, BoseProtocol.decodeActiveSourceType(none));
        Assert.assertNull(BoseProtocol.decodeActiveSourceMac(none));
    }

    @Test
    public void testDecodeBatteryLevel() {
        Assert.assertEquals(80, BoseProtocol.decodeBatteryLevel(hex("50 ff ff 00")));
        Assert.assertEquals(50, BoseProtocol.decodeBatteryLevel(hex("32")));
        Assert.assertEquals(-1, BoseProtocol.decodeBatteryLevel(new byte[0]));
    }

    @Test
    public void testPairingMode() {
        assertHexEquals(hex("04 08 05 01 01"), BoseProtocol.setPairingMode(true));
        assertHexEquals(hex("04 08 05 01 00"), BoseProtocol.setPairingMode(false));
    }

    @Test
    public void testRemoveDevice() {
        assertHexEquals(hex("04 03 05 06 aabbccddeeff"), BoseProtocol.removeDevice(hex("aa:bb:cc:dd:ee:ff")));
    }

    @Test
    public void testConnectDisconnectDevice() {
        final byte[] mac = hex("aa:bb:cc:dd:ee:ff");
        assertHexEquals(hex("04 01 05 07 00 aabbccddeeff"), BoseProtocol.connectDevice(mac));
        assertHexEquals(hex("04 02 05 06 aabbccddeeff"), BoseProtocol.disconnectDevice(mac));
    }

    @Test
    public void testMediaControls() {
        assertHexEquals(hex("05 03 01 00"), BoseProtocol.getMediaControlCapabilities());
        assertHexEquals(hex("05 03 05 01 01"), BoseProtocol.mediaControl(BoseProtocol.MEDIA_PLAY));
        assertHexEquals(hex("05 03 05 01 02"), BoseProtocol.mediaControl(BoseProtocol.MEDIA_PAUSE));
        assertHexEquals(hex("05 03 05 01 03"), BoseProtocol.mediaControl(BoseProtocol.MEDIA_NEXT));
        assertHexEquals(hex("05 03 05 01 04"), BoseProtocol.mediaControl(BoseProtocol.MEDIA_PREVIOUS));
        Assert.assertEquals(0x0012, BoseProtocol.decodeMediaControlCapabilities(hex("12 00")));
        Assert.assertTrue(BoseProtocol.isMediaControlSupported(0x0012, BoseProtocol.MEDIA_PLAY));
        Assert.assertFalse(BoseProtocol.isMediaControlSupported(0x0012, BoseProtocol.MEDIA_PAUSE));
        Assert.assertTrue(BoseProtocol.isMediaControlSupported(0x0012, BoseProtocol.MEDIA_PREVIOUS));
    }

    @Test
    public void testFirmwareVersion() {
        assertHexEquals(hex("00 05 01 00"), BoseProtocol.getFirmwareVersion());
    }

    @Test
    public void testParserSingleFrame() {
        final BoseProtocol.BoseFrameParser parser = new BoseProtocol.BoseFrameParser();
        final List<byte[]> frames = parser.feed(hex("02 02 03 01 50"));
        Assert.assertEquals(1, frames.size());
        assertHexEquals(hex("02 02 03 01 50"), frames.get(0));
    }

    @Test
    public void testParserMultipleFramesInOneChunk() {
        final BoseProtocol.BoseFrameParser parser = new BoseProtocol.BoseFrameParser();
        final List<byte[]> frames = parser.feed(hex(
                "02 02 03 01 50  01 05 03 03 0b 05 01  00 01 03 00"));
        Assert.assertEquals(3, frames.size());
        assertHexEquals(hex("02 02 03 01 50"), frames.get(0));
        assertHexEquals(hex("01 05 03 03 0b 05 01"), frames.get(1));
        assertHexEquals(hex("00 01 03 00"), frames.get(2));
    }

    @Test
    public void testParserFrameSplitAcrossFeeds() {
        final BoseProtocol.BoseFrameParser parser = new BoseProtocol.BoseFrameParser();
        Assert.assertTrue(parser.feed(hex("02 02 03")).isEmpty());
        Assert.assertTrue(parser.feed(hex("01")).isEmpty());
        final List<byte[]> frames = parser.feed(hex("50 02 02 03 01 42"));
        Assert.assertEquals(2, frames.size());
        assertHexEquals(hex("02 02 03 01 50"), frames.get(0));
        assertHexEquals(hex("02 02 03 01 42"), frames.get(1));
    }

    @Test
    public void testParserEmptyFeed() {
        final BoseProtocol.BoseFrameParser parser = new BoseProtocol.BoseFrameParser();
        Assert.assertTrue(parser.feed(new byte[0]).isEmpty());
        Assert.assertTrue(parser.feed(new byte[]{0x02, 0x02}).isEmpty());
    }

    @Test
    public void testParserUnsignedLength() {
        final BoseProtocol.BoseFrameParser parser = new BoseProtocol.BoseFrameParser();
        final byte[] frame = BoseProtocol.frame(0x04, 0x04, 0x03,
                new byte[BoseProtocol.MAX_FRAME_LENGTH]);
        final List<byte[]> frames = parser.feed(frame);
        Assert.assertEquals(1, frames.size());
        Assert.assertArrayEquals(frame, frames.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFrameRejectsPayloadsLongerThanOneByteLength() {
        BoseProtocol.frame(0x04, 0x04, 0x03, new byte[BoseProtocol.MAX_FRAME_LENGTH + 1]);
    }
}
