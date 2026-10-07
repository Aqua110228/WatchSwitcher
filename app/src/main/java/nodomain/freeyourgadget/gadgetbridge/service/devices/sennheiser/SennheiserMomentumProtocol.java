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

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_LED;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_SWAP_VOLUME_BUTTONS;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_ANSWER;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_VOICE_PROMPTS;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Qualcomm/CSR GAIA version 1, as spoken by the Sennheiser MOMENTUM In-Ear Wireless over its
 * serial port service. Every command uses the CSR vendor id; the headset implements none under
 * Sennheiser's own. A frame is {@code FF 01 flags length vendor(2) command(2) payload}; the headset
 * sets no flags. A reply has the command with bit 15 set, and its first payload byte is the status.
 */
public class SennheiserMomentumProtocol {
    private static final Logger LOG = LoggerFactory.getLogger(SennheiserMomentumProtocol.class);

    static final int VENDOR_CSR = 0x000A;

    static final int CMD_POWER_OFF = 0x0204;
    static final int CMD_SET_VOLUME_ORIENTATION = 0x0205;
    static final int CMD_SET_LED_CONTROL = 0x0207;
    static final int CMD_PLAY_TONE = 0x0209;
    static final int CMD_SET_VOICE_PROMPT_CONTROL = 0x020A;
    static final int CMD_SET_SPEECH_RECOGNITION_CONTROL = 0x020C;
    static final int CMD_GET_VOLUME_ORIENTATION = 0x0285;
    static final int CMD_GET_LED_CONTROL = 0x0287;
    static final int CMD_GET_VOICE_PROMPT_CONTROL = 0x028A;
    static final int CMD_GET_SPEECH_RECOGNITION_CONTROL = 0x028C;
    static final int CMD_GET_APPLICATION_VERSION = 0x0304;

    private static final int ACK = 0x8000;
    private static final int STATUS_SUCCESS = 0x00;
    private static final int TONE_FIND_DEVICE = 0x01;

    private static final int SOF = 0xFF;
    private static final int PROTOCOL_VERSION = 0x01;
    private static final int HEADER_LENGTH = 8;

    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    public static byte[] encode(final int command, final byte... payload) {
        final byte[] frame = new byte[HEADER_LENGTH + payload.length];
        frame[0] = (byte) SOF;
        frame[1] = PROTOCOL_VERSION;
        frame[2] = 0;
        frame[3] = (byte) payload.length;
        frame[4] = (byte) (VENDOR_CSR >> 8);
        frame[5] = (byte) VENDOR_CSR;
        frame[6] = (byte) (command >> 8);
        frame[7] = (byte) command;
        System.arraycopy(payload, 0, frame, HEADER_LENGTH, payload.length);
        return frame;
    }

    public static byte[] encodeSetting(final int command, final boolean enabled) {
        return encode(command, (byte) (enabled ? 1 : 0));
    }

    public static byte[] encodeFindDevice() {
        return encode(CMD_PLAY_TONE, (byte) TONE_FIND_DEVICE);
    }

    /**
     * Decodes whatever complete frames the data finishes, keeping an incomplete one for the next call:
     * the serial port does not preserve frame boundaries.
     */
    public List<GBDeviceEvent> decode(final byte[] data) {
        pending.write(data, 0, data.length);
        final byte[] buf = pending.toByteArray();
        final List<GBDeviceEvent> events = new ArrayList<>();

        int pos = 0;
        while (pos < buf.length) {
            if ((buf[pos] & 0xFF) != SOF || (pos + 1 < buf.length && buf[pos + 1] != PROTOCOL_VERSION)) {
                pos++; // not the start of a frame
                continue;
            }
            if (buf.length - pos < HEADER_LENGTH) {
                break;
            }
            final int payloadLength = buf[pos + 3] & 0xFF;
            final int frameLength = HEADER_LENGTH + payloadLength;
            if (buf.length - pos < frameLength) {
                break;
            }
            final int vendor = ((buf[pos + 4] & 0xFF) << 8) | (buf[pos + 5] & 0xFF);
            final int command = ((buf[pos + 6] & 0xFF) << 8) | (buf[pos + 7] & 0xFF);
            final byte[] payload = Arrays.copyOfRange(buf, pos + HEADER_LENGTH, pos + HEADER_LENGTH + payloadLength);
            final GBDeviceEvent event = handleFrame(vendor, command, payload);
            if (event != null) {
                events.add(event);
            }
            pos += frameLength;
        }

        pending.reset();
        pending.write(buf, pos, buf.length - pos);
        return events;
    }

    @Nullable
    private GBDeviceEvent handleFrame(final int vendor, final int command, final byte[] payload) {
        if (vendor != VENDOR_CSR || (command & ACK) == 0) {
            LOG.debug("Ignoring vendor {} command {}: {}", String.format("%04x", vendor), String.format("%04x", command), GB.hexdump(payload));
            return null;
        }
        final int acknowledged = command & ~ACK;
        if (payload.length == 0 || payload[0] != STATUS_SUCCESS) {
            LOG.warn("Command {} was not successful: {}", String.format("%04x", acknowledged), GB.hexdump(payload));
            return null;
        }

        switch (acknowledged) {
            case CMD_GET_APPLICATION_VERSION:
                return handleApplicationVersion(payload);
            case CMD_GET_LED_CONTROL:
                return handleSetting(PREF_SENNHEISER_MOMENTUM_LED, payload);
            case CMD_GET_VOICE_PROMPT_CONTROL:
                return handleSetting(PREF_SENNHEISER_MOMENTUM_VOICE_PROMPTS, payload);
            case CMD_GET_SPEECH_RECOGNITION_CONTROL:
                return handleSetting(PREF_SENNHEISER_MOMENTUM_VOICE_ANSWER, payload);
            case CMD_GET_VOLUME_ORIENTATION:
                return handleSetting(PREF_SENNHEISER_MOMENTUM_SWAP_VOLUME_BUTTONS, payload);
            default:
                LOG.debug("Command {} acknowledged", String.format("%04x", acknowledged));
                return null;
        }
    }

    /**
     * The headset answers with its Device ID record: source, vendor, product and version, two bytes each.
     * The version is 0xJJMN, read as major JJ, minor M, sub-minor N.
     */
    @Nullable
    private static GBDeviceEvent handleApplicationVersion(final byte[] payload) {
        if (payload.length < 9) {
            LOG.warn("Unexpected application version: {}", GB.hexdump(payload));
            return null;
        }
        final int version = ((payload[7] & 0xFF) << 8) | (payload[8] & 0xFF);
        final GBDeviceEventVersionInfo versionInfo = new GBDeviceEventVersionInfo();
        versionInfo.fwVersion = String.format(Locale.ROOT, "%d.%d.%d", version >> 8, (version >> 4) & 0xF, version & 0xF);
        return versionInfo;
    }

    @Nullable
    private static GBDeviceEvent handleSetting(final String key, final byte[] payload) {
        if (payload.length < 2) {
            LOG.warn("No value for {}: {}", key, GB.hexdump(payload));
            return null;
        }
        return new GBDeviceEventUpdatePreferences(key, payload[1] != 0);
    }
}
