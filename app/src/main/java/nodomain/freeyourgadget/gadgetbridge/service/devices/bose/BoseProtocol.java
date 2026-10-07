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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Encodes and decodes BMAP protocol frames. */
public final class BoseProtocol {
    public static final int MAX_FRAME_LENGTH = 255;

    // Function blocks
    public static final int BLOCK_PRODUCT_INFO = 0x00;
    public static final int BLOCK_SETTINGS = 0x01;
    public static final int BLOCK_STATUS = 0x02;
    public static final int BLOCK_DEVICE_MANAGEMENT = 0x04;
    public static final int BLOCK_AUDIO_MANAGEMENT = 0x05;
    public static final int BLOCK_NOTIFICATION = 0x09;

    // Operators
    public static final int OP_GET = 0x01;
    public static final int OP_SETGET = 0x02;
    public static final int OP_STATUS = 0x03;
    public static final int OP_ERROR = 0x04;
    public static final int OP_START = 0x05;
    public static final int OP_RESULT = 0x06;
    public static final int OP_PROCESSING = 0x07;

    // Notification functions
    public static final int FUNCTION_NOTIFICATION_BY_FUNCTION_BLOCK = 0x02;

    // Settings functions
    public static final int FUNCTION_VOICE_PROMPTS = 0x03;
    public static final int FUNCTION_STANDBY_TIMER = 0x04;
    public static final int FUNCTION_NOISE_CANCELLING = 0x05;
    public static final int FUNCTION_ANR = 0x06;
    public static final int FUNCTION_BUTTONS = 0x09;
    public static final int FUNCTION_MULTIPOINT = 0x0a;

    public static final int VOICE_PROMPTS_ENABLED_MASK = 0x20;
    public static final int VOICE_PROMPTS_LANGUAGE_MASK = 0x1f;

    // Status functions
    public static final int FUNCTION_BATTERY = 0x02;

    // Product info functions
    public static final int FUNCTION_INIT_HANDSHAKE = 0x01;
    public static final int FUNCTION_FIRMWARE_VERSION = 0x05;

    // Device management functions
    public static final int FUNCTION_CONNECT_DEVICE = 0x01;
    public static final int FUNCTION_DISCONNECT_DEVICE = 0x02;
    public static final int FUNCTION_REMOVE_DEVICE = 0x03;
    public static final int FUNCTION_LIST_DEVICES = 0x04;
    public static final int FUNCTION_DEVICE_INFO = 0x05;
    public static final int FUNCTION_PAIRING_MODE = 0x08;

    // Audio management functions
    public static final int FUNCTION_SOURCE = 0x01;
    public static final int FUNCTION_MEDIA_CONTROL = 0x03;

    // Active source types
    public static final int SOURCE_NONE = 0x00;
    public static final int SOURCE_BLUETOOTH = 0x01;
    public static final int SOURCE_AUXILIARY = 0x02;

    // Media transport control actions
    public static final int MEDIA_PLAY = 0x01;
    public static final int MEDIA_PAUSE = 0x02;
    public static final int MEDIA_NEXT = 0x03;
    public static final int MEDIA_PREVIOUS = 0x04;

    // Configurable button ids
    public static final int BUTTON_SHORTCUT = 0x80;
    // Button event types
    public static final int BUTTON_EVENT_PRESS_AND_HOLD = 0x05;
    // Shortcut action modes
    public static final int BUTTON_MODE_BATTERY_LEVEL = 3;
    public static final int BUTTON_MODE_SELF_VOICE_OR_WIND = 13;
    public static final int BUTTON_MODE_SPOTIFY = 16;

    public static final class Command {
        public final int block;
        public final int function;
        public final int operator;

        public Command(final int block, final int function, final int operator) {
            this.block = block;
            this.function = function;
            this.operator = operator;
        }

        public byte[] frame(final byte... payload) {
            return BoseProtocol.frame(block, function, operator, payload);
        }
    }

    private BoseProtocol() {
    }

    public static String errorName(final int error) {
        switch (error) {
            case 1: return "Length";
            case 3: return "Function block not supported";
            case 4: return "Function not supported";
            case 5: return "Operator not supported";
            case 6: return "Invalid data";
            default: return "Unknown";
        }
    }

    public static byte[] frame(final int block, final int function, final int operator,
                               final byte... payload) {
        if (payload.length > MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("Payload exceeds one-byte frame length");
        }
        final byte[] frame = new byte[4 + payload.length];
        frame[0] = (byte) block;
        frame[1] = (byte) function;
        frame[2] = (byte) operator;
        frame[3] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        return frame;
    }

    public static byte[] connectHandshake() {
        return frame(BLOCK_PRODUCT_INFO, FUNCTION_INIT_HANDSHAKE, OP_GET);
    }

    public static byte[] getBattery() {
        return frame(BLOCK_STATUS, FUNCTION_BATTERY, OP_GET);
    }

    public static byte[] enableNotificationsForFunctionBlocks(final int... blocks) {
        int highestBlock = 0;
        for (final int block : blocks) {
            if (block < 0 || block > 0xFF) {
                throw new IllegalArgumentException("Function block must fit in an unsigned byte");
            }
            highestBlock = Math.max(highestBlock, block);
        }

        final byte[] bitset = new byte[Math.max(1, (highestBlock / 8) + 1)];
        for (final int block : blocks) {
            bitset[bitset.length - 1 - (block / 8)] |= 1 << (block % 8);
        }
        final byte[] payload = new byte[1 + bitset.length];
        payload[0] = 0x01;
        System.arraycopy(bitset, 0, payload, 1, bitset.length);
        return frame(BLOCK_NOTIFICATION, FUNCTION_NOTIFICATION_BY_FUNCTION_BLOCK, OP_SETGET, payload);
    }

    public static byte[] getFirmwareVersion() {
        return frame(BLOCK_PRODUCT_INFO, FUNCTION_FIRMWARE_VERSION, OP_GET);
    }

    public static byte[] getCnc() {
        return frame(BLOCK_SETTINGS, FUNCTION_NOISE_CANCELLING, OP_GET);
    }

    public static byte[] getAnr() {
        return frame(BLOCK_SETTINGS, FUNCTION_ANR, OP_GET);
    }

    public static byte[] getMultipoint() {
        return frame(BLOCK_SETTINGS, FUNCTION_MULTIPOINT, OP_GET);
    }

    public static byte[] setMultipoint(final boolean enabled) {
        return frame(BLOCK_SETTINGS, FUNCTION_MULTIPOINT, OP_SETGET, (byte) (enabled ? 1 : 0));
    }

    public static byte[] getVoicePrompts() {
        return frame(BLOCK_SETTINGS, FUNCTION_VOICE_PROMPTS, OP_GET);
    }

    public static byte[] setVoicePrompts(final boolean enabled, final int language) {
        return frame(BLOCK_SETTINGS, FUNCTION_VOICE_PROMPTS, OP_SETGET,
                (byte) ((enabled ? VOICE_PROMPTS_ENABLED_MASK : 0)
                        | (language & VOICE_PROMPTS_LANGUAGE_MASK)));
    }

    public static byte[] getStandbyTimer() {
        return frame(BLOCK_SETTINGS, FUNCTION_STANDBY_TIMER, OP_GET);
    }

    public static byte[] setStandbyTimer(final int minutes) {
        if (minutes < 0 || minutes > 0xFFFF) {
            throw new IllegalArgumentException("Standby timer must fit in an unsigned 16-bit integer");
        }
        if (minutes <= 0xFF) {
            return frame(BLOCK_SETTINGS, FUNCTION_STANDBY_TIMER, OP_SETGET, (byte) minutes);
        }
        return frame(BLOCK_SETTINGS, FUNCTION_STANDBY_TIMER, OP_SETGET,
                (byte) minutes, (byte) (minutes >>> 8));
    }

    public static byte[] getButtons() {
        return frame(BLOCK_SETTINGS, FUNCTION_BUTTONS, OP_GET);
    }

    public static byte[] setActionButton(final int buttonId, final int eventType, final int mode) {
        return frame(BLOCK_SETTINGS, FUNCTION_BUTTONS, OP_SETGET,
                (byte) buttonId, (byte) eventType, (byte) mode);
    }

    // Wire values: 0=Off, 1=High, 2=Wind, 3=Low.
    public static byte[] setAnr(final int level) {
        return frame(BLOCK_SETTINGS, FUNCTION_ANR, OP_SETGET, (byte) level);
    }

    // Wire value is inverted (10 - level) and sent three times because enabling ANC resets the level
    public static byte[] setCnc(final int level) {
        final int clamped = Math.max(0, Math.min(10, level));
        final byte[] packet = frame(BLOCK_SETTINGS, FUNCTION_NOISE_CANCELLING, OP_SETGET,
                (byte) (10 - clamped), (byte) 0x01);
        final byte[] repeated = new byte[packet.length * 3];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(packet, 0, repeated, i * packet.length, packet.length);
        }
        return repeated;
    }

    public static byte[] setPairingMode(final boolean enabled) {
        return frame(BLOCK_DEVICE_MANAGEMENT, FUNCTION_PAIRING_MODE, OP_START,
                (byte) (enabled ? 0x01 : 0x00));
    }

    public static byte[] removeDevice(final byte[] mac) {
        return frame(BLOCK_DEVICE_MANAGEMENT, FUNCTION_REMOVE_DEVICE, OP_START, mac);
    }

    public static byte[] connectDevice(final byte[] mac) {
        return frame(BLOCK_DEVICE_MANAGEMENT, FUNCTION_CONNECT_DEVICE, OP_START,
                concat(new byte[]{0x00}, mac));
    }

    public static byte[] disconnectDevice(final byte[] mac) {
        return frame(BLOCK_DEVICE_MANAGEMENT, FUNCTION_DISCONNECT_DEVICE, OP_START, mac);
    }

    public static byte[] listPairedDevices() {
        return frame(BLOCK_DEVICE_MANAGEMENT, FUNCTION_LIST_DEVICES, OP_GET);
    }

    public static byte[] getDeviceInfo(final byte[] mac) {
        return frame(BLOCK_DEVICE_MANAGEMENT, FUNCTION_DEVICE_INFO, OP_GET, mac);
    }

    public static byte[] macToBytes(final String mac) {
        final String cleaned = mac.replace(":", "");
        if (cleaned.length() != 12) {
            throw new IllegalArgumentException("Invalid MAC address: " + mac);
        }
        final byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    public static String bytesToMac(final byte[] bytes, final int offset) {
        final StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", bytes[offset + i]));
        }
        return sb.toString();
    }

    public static final class PairedDevice {
        public final String mac;
        public final boolean connected;

        PairedDevice(final String mac, final boolean connected) {
            this.mac = mac;
            this.connected = connected;
        }

        @Override
        public String toString() {
            return mac + (connected ? " (connected)" : "");
        }
    }

    // Paired-device payload: [connectedBitmask, mac...] - bit i of byte 0 = entry i connected; order is not stable
    public static List<PairedDevice> decodePairedDevices(final byte[] payload) {
        final List<PairedDevice> devices = new ArrayList<>();
        if (payload.length < 1) {
            return devices;
        }
        final int connectedMask = payload[0] & 0xFF;
        final int numDevices = (payload.length - 1) / 6;
        for (int i = 0; i < numDevices; i++) {
            final int offset = 1 + i * 6;
            if (offset + 6 > payload.length) {
                break;
            }
            devices.add(new PairedDevice(bytesToMac(payload, offset), (connectedMask & (1 << i)) != 0));
        }
        return devices;
    }

    // Device-info payload: [mac(6), flags, b7, b8, (variant), name...]; name at 10 when flag 0x04 is set, otherwise 9
    public static String decodeDeviceInfoSummary(final byte[] payload) {
        final String name = decodeDeviceInfoName(payload);
        if (name == null) {
            return null;
        }
        final String mac = bytesToMac(payload, 0);
        final boolean connected = (payload[6] & 0x01) != 0;
        return mac + (connected ? " (connected)" : "") + " " + name;
    }

    public static String decodeDeviceInfoName(final byte[] payload) {
        if (payload.length < 9) {
            return null;
        }
        final int flags = payload[6] & 0xFF;
        final int nameOffset = (flags & 0x04) != 0 ? 10 : 9;
        if (nameOffset >= payload.length) {
            return null;
        }
        return new String(payload, nameOffset, payload.length - nameOffset,
                StandardCharsets.UTF_8).trim();
    }

    /** Returns battery percentage, or -1 when absent. */
    public static int decodeBatteryLevel(final byte[] payload) {
        if (payload.length < 1) {
            return -1;
        }
        return payload[0] & 0xFF;
    }

    // CNC status payload: [numSteps, invertedLevel, enabled]
    public static int decodeCncLevel(final byte[] payload) {
        if (payload.length < 2) {
            return -1;
        }
        return 10 - (payload[1] & 0xFF);
    }

    // ANR status payload: [wireLevel, 0x0b]; wire 0=off, 1=high, 2=wind, 3=low
    public static int decodeAnrLevel(final byte[] payload) {
        if (payload.length < 1) {
            return -1;
        }
        final int level = payload[0] & 0xFF;
        return level <= 3 ? level : -1;
    }

    private static byte[] concat(final byte[] a, final byte[] b) {
        final byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    // Multipoint status flags: bit0 enabled, bit1 supported, bit2 disableSupported
    public static Boolean decodeMultipointEnabled(final byte[] payload) {
        if (payload.length < 1) {
            return null;
        }
        return (payload[0] & 0x01) != 0;
    }

    public static Boolean decodeMultipointSupported(final byte[] payload) {
        if (payload.length < 1) {
            return null;
        }
        return (payload[0] & 0x02) != 0;
    }

    public static Boolean decodeMultipointDisableSupported(final byte[] payload) {
        if (payload.length < 1) {
            return null;
        }
        return (payload[0] & 0x04) != 0;
    }

    // Voice prompts status byte: (togglable << 7) | (default << 6) | (enabled << 5) | language
    public static Boolean decodeVoicePromptsEnabled(final byte[] payload) {
        if (payload.length < 1) {
            return null;
        }
        return (payload[0] & VOICE_PROMPTS_ENABLED_MASK) != 0;
    }

    public static Boolean decodeVoicePromptsTogglable(final byte[] payload) {
        if (payload.length < 1) {
            return null;
        }
        return (payload[0] & 0x80) != 0;
    }

    public static int decodeVoicePromptsLanguage(final byte[] payload) {
        if (payload.length < 1) {
            return -1;
        }
        return payload[0] & VOICE_PROMPTS_LANGUAGE_MASK;
    }

    // Voice prompts status bytes 1-4: big-endian bitmask of supported language ids
    public static int decodeVoicePromptsSupportedMask(final byte[] payload) {
        if (payload.length < 5) {
            return -1;
        }
        return ((payload[1] & 0xFF) << 24)
                | ((payload[2] & 0xFF) << 16)
                | ((payload[3] & 0xFF) << 8)
                | (payload[4] & 0xFF);
    }

    // Standby timer duration in minutes as one byte or little-endian u16
    public static int decodeStandbyTimerMinutes(final byte[] payload) {
        if (payload.length < 1) {
            return -1;
        }
        if (payload.length == 1) {
            return payload[0] & 0xFF;
        }
        return (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8);
    }

    // Configurable-button payload: [buttonId, eventType, currentMode, supportedMask(4), unavailableMask(4)]
    public static final class ButtonConfig {
        public final int buttonId;
        public final int eventType;
        public final int currentMode;
        public final int supportedMask;
        public final int unavailableMask;

        ButtonConfig(final int buttonId, final int eventType, final int currentMode,
                     final int supportedMask, final int unavailableMask) {
            this.buttonId = buttonId;
            this.eventType = eventType;
            this.currentMode = currentMode;
            this.supportedMask = supportedMask;
            this.unavailableMask = unavailableMask;
        }
    }

    public static ButtonConfig decodeButtonConfig(final byte[] payload) {
        if (payload.length < 3) {
            return null;
        }
        final int buttonId = payload[0] & 0xFF;
        final int eventType = payload[1] & 0xFF;
        final int currentMode = payload[2] & 0xFF;
        int supportedMask = 0;
        int unavailableMask = 0;
        if (payload.length >= 7) {
            supportedMask = ((payload[3] & 0xFF) << 24) | ((payload[4] & 0xFF) << 16)
                    | ((payload[5] & 0xFF) << 8) | (payload[6] & 0xFF);
        }
        if (payload.length >= 11) {
            unavailableMask = ((payload[7] & 0xFF) << 24) | ((payload[8] & 0xFF) << 16)
                    | ((payload[9] & 0xFF) << 8) | (payload[10] & 0xFF);
        }
        return new ButtonConfig(buttonId, eventType, currentMode, supportedMask, unavailableMask);
    }

    public static byte[] getMediaControlCapabilities() {
        return frame(BLOCK_AUDIO_MANAGEMENT, FUNCTION_MEDIA_CONTROL, OP_GET);
    }

    public static byte[] mediaControl(final int action) {
        return frame(BLOCK_AUDIO_MANAGEMENT, FUNCTION_MEDIA_CONTROL, OP_START, (byte) action);
    }

    public static int decodeMediaControlCapabilities(final byte[] payload) {
        if (payload.length < 1) {
            return 0;
        }
        int capabilities = payload[0] & 0xFF;
        if (payload.length > 1) {
            capabilities |= (payload[1] & 0xFF) << 8;
        }
        return capabilities;
    }

    public static boolean isMediaControlSupported(final int capabilities, final int action) {
        return action >= 0 && action < Integer.SIZE && (capabilities & (1 << action)) != 0;
    }

    public static byte[] getSourceInfo() {
        return frame(BLOCK_AUDIO_MANAGEMENT, FUNCTION_SOURCE, OP_GET);
    }

    // Source payload: [supportedBitset(2), activeType, mac(6)?] - MAC only when the active type is Bluetooth
    public static String decodeActiveSourceMac(final byte[] payload) {
        if (payload.length < 3) {
            return null;
        }
        final int activeType = payload[2] & 0xFF;
        if (activeType != SOURCE_BLUETOOTH || payload.length < 9) {
            return null;
        }
        return bytesToMac(payload, 3);
    }

    public static int decodeActiveSourceType(final byte[] payload) {
        if (payload.length < 3) {
            return SOURCE_NONE;
        }
        return payload[2] & 0xFF;
    }

    /** Buffers incomplete data and returns complete frames. */
    public static final class BoseFrameParser {
        private byte[] pending = new byte[0];

        public synchronized List<byte[]> feed(final byte[] data) {
            final List<byte[]> frames = new ArrayList<>();
            pending = concat(pending, data);
            int offset = 0;
            while (pending.length - offset >= 4) {
                final int payloadLength = pending[offset + 3] & 0xFF;
                if (pending.length - offset < 4 + payloadLength) {
                    break;
                }
                frames.add(Arrays.copyOfRange(pending, offset, offset + 4 + payloadLength));
                offset += 4 + payloadLength;
            }
            pending = offset == 0 ? pending : Arrays.copyOfRange(pending, offset, pending.length);
            return frames;
        }

        private static byte[] concat(final byte[] a, final byte[] b) {
            final byte[] result = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }
    }
}
