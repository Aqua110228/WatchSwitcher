/*  Copyright (C) 2026 oddballza

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class SennheiserMomentumProtocolTest extends TestBase {
    private static byte[] hex(final String s) {
        return GB.hexStringToByteArray(s.replace(" ", ""));
    }

    private static Object preference(final GBDeviceEvent event, final String key) {
        assertTrue("expected a preference update, got " + event, event instanceof GBDeviceEventUpdatePreferences);
        final GBDeviceEventUpdatePreferences update = (GBDeviceEventUpdatePreferences) event;
        assertTrue("no " + key + " in " + update.preferences, update.preferences.containsKey(key));
        return update.preferences.get(key);
    }

    @Test
    public void encodesCommandsInGaiaVersionOneFrames() {
        assertArrayEquals(hex("FF 01 00 00 00 0A 02 87"), SennheiserMomentumProtocol.encode(SennheiserMomentumProtocol.CMD_GET_LED_CONTROL));
        assertArrayEquals(hex("FF 01 00 01 00 0A 02 07 00"), SennheiserMomentumProtocol.encodeSetting(SennheiserMomentumProtocol.CMD_SET_LED_CONTROL, false));
        assertArrayEquals(hex("FF 01 00 01 00 0A 02 0A 01"), SennheiserMomentumProtocol.encodeSetting(SennheiserMomentumProtocol.CMD_SET_VOICE_PROMPT_CONTROL, true));
        assertArrayEquals(hex("FF 01 00 01 00 0A 02 09 01"), SennheiserMomentumProtocol.encodeFindDevice());
        assertArrayEquals(hex("FF 01 00 00 00 0A 02 04"), SennheiserMomentumProtocol.encode(SennheiserMomentumProtocol.CMD_POWER_OFF));
    }

    @Test
    public void readsTheFirmwareVersionFromTheDeviceIdRecord() {
        // status, then source 0x0002, vendor 0x1377, product 0x6002, version 0x0213
        final List<GBDeviceEvent> events = new SennheiserMomentumProtocol().decode(hex("FF 01 00 09 00 0A 83 04 00 00 02 13 77 60 02 02 13"));
        assertEquals(1, events.size());
        assertEquals("2.1.3", ((GBDeviceEventVersionInfo) events.get(0)).fwVersion);
    }

    @Test
    public void readsEachSettingIntoItsPreference() {
        final SennheiserMomentumProtocol protocol = new SennheiserMomentumProtocol();
        assertEquals(true, preference(protocol.decode(hex("FF 01 00 02 00 0A 82 87 00 01")).get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED));
        assertEquals(false, preference(protocol.decode(hex("FF 01 00 02 00 0A 82 87 00 00")).get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED));
        assertEquals(true, preference(protocol.decode(hex("FF 01 00 02 00 0A 82 8A 00 01")).get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_PROMPTS));
        assertEquals(false, preference(protocol.decode(hex("FF 01 00 02 00 0A 82 8C 00 00")).get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_ANSWER));
        assertEquals(true, preference(protocol.decode(hex("FF 01 00 02 00 0A 82 85 00 01")).get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_SWAP_VOLUME_BUTTONS));
    }

    @Test
    public void waitsForTheRestOfAFrameSplitAcrossReads() {
        final SennheiserMomentumProtocol protocol = new SennheiserMomentumProtocol();
        assertEquals(0, protocol.decode(hex("FF 01 00 02 00 0A")).size());
        final List<GBDeviceEvent> events = protocol.decode(hex("82 87 00 01"));
        assertEquals(1, events.size());
        assertEquals(true, preference(events.get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED));
    }

    @Test
    public void decodesSeveralFramesInOneRead() {
        final List<GBDeviceEvent> events = new SennheiserMomentumProtocol().decode(hex(
                "FF 01 00 02 00 0A 82 87 00 01" + "FF 01 00 02 00 0A 82 8A 00 00"));
        assertEquals(2, events.size());
        assertEquals(true, preference(events.get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED));
        assertEquals(false, preference(events.get(1), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_PROMPTS));
    }

    @Test
    public void skipsBytesUntilTheStartOfAFrame() {
        final List<GBDeviceEvent> events = new SennheiserMomentumProtocol().decode(hex("00 13 FF 7F FF 01 00 02 00 0A 82 87 00 00"));
        assertEquals(1, events.size());
        assertEquals(false, preference(events.get(0), DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED));
    }

    @Test
    public void ignoresRefusedCommandsAndOtherVendors() {
        final SennheiserMomentumProtocol protocol = new SennheiserMomentumProtocol();
        // a refused request to read the LED setting, followed by a byte that must not be taken for its value
        assertEquals("refused", 0, protocol.decode(hex("FF 01 00 02 00 0A 82 87 01 00")).size());
        assertEquals("other vendor", 0, protocol.decode(hex("FF 01 00 02 13 77 82 87 00 01")).size());
        assertEquals("not a reply", 0, protocol.decode(hex("FF 01 00 01 00 0A 40 03 09")).size());
    }
}
