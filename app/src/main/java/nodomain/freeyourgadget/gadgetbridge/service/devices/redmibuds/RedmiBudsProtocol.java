/*  Copyright (C) 2024 Jonathan Gobbo

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.redmibuds;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_ADAPTIVE_NOISE_CANCELLING;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_ADAPTIVE_SOUND;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_AUTO_REPLY_PHONECALL;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_LEFT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_RIGHT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_DOUBLE_CONNECTION;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_EQUALIZER_PRESET;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_NOISE_CANCELLING_STRENGTH;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_TRANSPARENCY_STRENGTH;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_REDMI_BUDS_WEARING_DETECTION;
import static nodomain.freeyourgadget.gadgetbridge.util.GB.hexdump;

import android.content.SharedPreferences.Editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventSendBytes;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceState;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.Configuration.Config;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.Configuration.StrengthTarget;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundCycle;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundMode;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerBand;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerBandLevel;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerPreset;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsLongGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsNoiseCancellingStrength;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsPosition;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsPrefs;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsTapType;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsTransparencyStrength;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice.State;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.model.FindDeviceTarget;
import nodomain.freeyourgadget.gadgetbridge.service.devices.redmibuds.protocol.Authentication;
import nodomain.freeyourgadget.gadgetbridge.service.devices.redmibuds.protocol.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.redmibuds.protocol.MessageType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.redmibuds.protocol.Opcode;
import nodomain.freeyourgadget.gadgetbridge.service.serial.GBDeviceProtocol;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class RedmiBudsProtocol extends GBDeviceProtocol {

    private static final Logger LOG = LoggerFactory.getLogger(RedmiBudsProtocol.class);
    public static final UUID UUID_DEVICE_CTRL = UUID.fromString("0000fd2d-0000-1000-8000-00805f9b34fb");

    /// The configuration values requested from the earbuds after authentication.
    private static final List<Config> INITIAL_CONFIG_REQUESTS = List.of(
        Config.EFFECT_STRENGTH, Config.ADAPTIVE_ANC, Config.GESTURES, Config.LONG_GESTURES,
        Config.EAR_DETECTION, Config.DOUBLE_CONNECTION, Config.AUTO_ANSWER,
        Config.ADAPTIVE_SOUND, Config.EQ_PRESET, Config.EQ_CURVE
    );

    private static final byte FIND_EARBUDS_LEFT = 0x01;
    private static final byte FIND_EARBUDS_RIGHT = 0x02;
    private static final byte FIND_EARBUDS_BOTH = 0x03;

    /// Index of the first equalizer band level in an EQ_CURVE payload.
    private static final int EQ_CURVE_FIRST_LEVEL = 12;

    /// Number of bytes between two equalizer band levels in an EQ_CURVE payload.
    private static final int EQ_CURVE_LEVEL_STRIDE = 3;

    private byte sequenceNumber = 0;

    protected RedmiBudsProtocol(GBDevice device) {
        super(device);
    }

    public byte[] encodeStartAuthentication() {
        byte[] authRnd = Authentication.getRandomChallenge();
        LOG.debug("[AUTH] Sending challenge: {}", hexdump(authRnd));

        byte[] payload = new byte[17];
        payload[0] = 0x01;
        System.arraycopy(authRnd, 0, payload, 1, 16);
        return new Message(MessageType.PHONE_REQUEST, Opcode.AUTH_CHALLENGE, sequenceNumber++, payload).encode();
    }

    @Override
    public byte[] encodeSendConfiguration(String config) {
        switch (config) {
            case PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL:
                return encodeSetAmbientSoundControl();
            case PREF_REDMI_BUDS_NOISE_CANCELLING_STRENGTH:
                return encodeSetEffectStrength(StrengthTarget.ANC);
            case PREF_REDMI_BUDS_TRANSPARENCY_STRENGTH:
                return encodeSetEffectStrength(StrengthTarget.TRANSPARENCY);
            case PREF_REDMI_BUDS_ADAPTIVE_NOISE_CANCELLING:
                return encodeSetBooleanConfig(config, Config.ADAPTIVE_ANC);

            case PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_LEFT:
                return encodeSetAmbientSoundCycle(RedmiBudsPosition.LEFT);
            case PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_RIGHT:
                return encodeSetAmbientSoundCycle(RedmiBudsPosition.RIGHT);

            case PREF_REDMI_BUDS_WEARING_DETECTION:
                return encodeSetEarDetection();
            case PREF_REDMI_BUDS_AUTO_REPLY_PHONECALL:
                return encodeSetBooleanConfig(config, Config.AUTO_ANSWER);
            case PREF_REDMI_BUDS_DOUBLE_CONNECTION:
                return encodeSetBooleanConfig(config, Config.DOUBLE_CONNECTION);
            case PREF_REDMI_BUDS_ADAPTIVE_SOUND:
                return encodeSetBooleanConfig(config, Config.ADAPTIVE_SOUND);

            case PREF_REDMI_BUDS_EQUALIZER_PRESET:
                return encodeSetEqualizerPreset();
        }

        for (final RedmiBudsTapType tapType : RedmiBudsTapType.values()) {
            for (final RedmiBudsPosition position : RedmiBudsPosition.values()) {
                if (tapType.getPreferenceKey(position).equals(config)) {
                    return encodeSetGesture(tapType, position);
                }
            }
        }

        for (final RedmiBudsEqualizerBand band : RedmiBudsEqualizerBand.values()) {
            if (band.getPreferenceKey().equals(config)) {
                return encodeSetCustomEqualizer();
            }
        }

        LOG.debug("Unsupported config: {}", config);
        return super.encodeSendConfiguration(config);
    }

    public byte[] encodeSetCustomEqualizer() {
        final Prefs prefs = getDevicePrefs();
        final RedmiBudsEqualizerBand[] bands = RedmiBudsEqualizerBand.values();

        final ByteBuffer payload = ByteBuffer
            .allocate(7 + EQ_CURVE_LEVEL_STRIDE * bands.length)
            .order(ByteOrder.BIG_ENDIAN)
            .put(new byte[]{0x24, 0x00, 0x37, 0x05, 0x01, 0x01, (byte) bands.length});
        for (final RedmiBudsEqualizerBand band : bands) {
            payload.putShort((short) band.getFrequency());
            payload.put(RedmiBudsPrefs.getCode(prefs, band.getPreferenceKey(), RedmiBudsEqualizerBandLevel.FLAT));
        }

        return new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++, payload.array()).encode();
    }

    public byte[] encodeSetEarDetection() {
        final Prefs prefs = getDevicePrefs();
        final byte value = (byte) (prefs.getBoolean(PREF_REDMI_BUDS_WEARING_DETECTION, false) ? 0x00 : 0x01);
        return new Message(MessageType.PHONE_REQUEST, Opcode.ANC, sequenceNumber++, new byte[]{0x02, 0x06, value}).encode();
    }

    public byte[] encodeSetAmbientSoundCycle(final RedmiBudsPosition position) {
        final Prefs prefs = getDevicePrefs();
        final String key = position == RedmiBudsPosition.LEFT
            ? PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_LEFT
            : PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_RIGHT;
        final byte value = RedmiBudsPrefs.getCode(prefs, key, RedmiBudsAmbientSoundCycle.ALL);

        final byte[] payload = {0x04, 0x00, 0x0a, (byte) 0xFF, (byte) 0xFF};
        payload[position == RedmiBudsPosition.LEFT ? 3 : 4] = value;
        return new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++, payload).encode();
    }

    public byte[] encodeSetGesture(final RedmiBudsTapType tapType, final RedmiBudsPosition position) {
        final Prefs prefs = getDevicePrefs();
        final String key = tapType.getPreferenceKey(position);
        final byte value = tapType == RedmiBudsTapType.LONG
            ? RedmiBudsPrefs.getCode(prefs, key, RedmiBudsLongGestureAction.VOICE_ASSISTANT)
            : RedmiBudsPrefs.getCode(prefs, key, RedmiBudsGestureAction.PLAY_PAUSE);

        final byte[] payload = {0x05, 0x00, 0x02, tapType.getCode(), (byte) 0xFF, (byte) 0xFF};
        payload[position == RedmiBudsPosition.LEFT ? 4 : 5] = value;
        return new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++, payload).encode();
    }

    public byte[] encodeSetEffectStrength(final StrengthTarget effect) {
        final Prefs prefs = getDevicePrefs();
        final byte value = effect == StrengthTarget.ANC
            ? RedmiBudsPrefs.getCode(prefs, PREF_REDMI_BUDS_NOISE_CANCELLING_STRENGTH, RedmiBudsNoiseCancellingStrength.BALANCED)
            : RedmiBudsPrefs.getCode(prefs, PREF_REDMI_BUDS_TRANSPARENCY_STRENGTH, RedmiBudsTransparencyStrength.REGULAR);
        return new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++, new byte[]{0x04, 0x00, 0x0b, effect.value, value}).encode();
    }

    public byte[] encodeSetEqualizerPreset() {
        final Prefs prefs = getDevicePrefs();
        final byte value = RedmiBudsPrefs.getCode(prefs, PREF_REDMI_BUDS_EQUALIZER_PRESET, RedmiBudsEqualizerPreset.STANDARD);
        return new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++, new byte[]{0x03, 0x00, Config.EQ_PRESET.value, value}).encode();
    }

    public byte[] encodeSetBooleanConfig(String pref, Config config) {
        final Prefs prefs = getDevicePrefs();
        final byte value = (byte) (prefs.getBoolean(pref, false) ? 0x01 : 0x00);
        return new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++, new byte[]{0x03, 0x00, config.value, value}).encode();
    }

    public byte[] encodeGetConfig() {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            for (final Config config : INITIAL_CONFIG_REQUESTS) {
                final Message message = new Message(MessageType.PHONE_REQUEST, Opcode.GET_CONFIG, sequenceNumber++, new byte[]{0x00, config.value});
                outputStream.write(message.encode());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }

    public byte[] encodeSetAmbientSoundControl() {
        final Prefs prefs = getDevicePrefs();
        final byte mode = RedmiBudsPrefs.getCode(prefs, PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL, RedmiBudsAmbientSoundMode.OFF);
        return new Message(MessageType.PHONE_REQUEST, Opcode.ANC, sequenceNumber++, new byte[]{0x02, 0x04, mode}).encode();
    }

    @Override
    public byte[] encodeFindDevice(final boolean start, @NonNull final FindDeviceTarget target) {
        final byte[] stopFind = new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++,
            new byte[]{0x04, 0x00, 0x09, 0x00, FIND_EARBUDS_BOTH}).encode();

        if (!start) {
            return stopFind;
        }

        final byte earbuds = switch (target) {
            case LEFT -> FIND_EARBUDS_LEFT;
            case RIGHT -> FIND_EARBUDS_RIGHT;
            default -> FIND_EARBUDS_BOTH;
        };

        final byte[] startFind = new Message(MessageType.PHONE_REQUEST, Opcode.SET_CONFIG, sequenceNumber++,
            new byte[]{0x04, 0x00, 0x09, 0x01, earbuds}).encode();

        final ByteArrayOutputStream messages = new ByteArrayOutputStream();
        try {
            messages.write(stopFind);
            messages.write(startFind);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return messages.toByteArray();
    }

    public void decodeGetConfig(byte[] configPayload) {
        if (configPayload.length < 3) {
            return;
        }

        final Editor editor = getDevicePrefs().getPreferences().edit();
        final Config config = Config.fromCode(configPayload[2]);
        switch (config) {
            case GESTURES:
                decodeGestures(configPayload, editor);
                break;
            case AUTO_ANSWER:
                editor.putBoolean(PREF_REDMI_BUDS_AUTO_REPLY_PHONECALL, configPayload[3] == 0x01);
                break;
            case DOUBLE_CONNECTION:
                editor.putBoolean(PREF_REDMI_BUDS_DOUBLE_CONNECTION, configPayload[3] == 0x01);
                break;
            case EQ_PRESET:
                RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_EQUALIZER_PRESET, RedmiBudsEqualizerPreset.class, configPayload[3]);
                break;
            case LONG_GESTURES:
                RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_LEFT, RedmiBudsAmbientSoundCycle.class, configPayload[3]);
                RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_CONTROL_LONG_TAP_SETTINGS_RIGHT, RedmiBudsAmbientSoundCycle.class, configPayload[4]);
                break;
            case EFFECT_STRENGTH:
                if (configPayload[3] == StrengthTarget.ANC.value) {
                    RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_NOISE_CANCELLING_STRENGTH, RedmiBudsNoiseCancellingStrength.class, configPayload[4]);
                } else if (configPayload[3] == StrengthTarget.TRANSPARENCY.value) {
                    RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_TRANSPARENCY_STRENGTH, RedmiBudsTransparencyStrength.class, configPayload[4]);
                }
                break;
            case ADAPTIVE_ANC:
                editor.putBoolean(PREF_REDMI_BUDS_ADAPTIVE_NOISE_CANCELLING, configPayload[3] == 0x01);
                break;
            case ADAPTIVE_SOUND:
                editor.putBoolean(PREF_REDMI_BUDS_ADAPTIVE_SOUND, configPayload[3] == 0x01);
                break;
            case EQ_CURVE:
                decodeEqualizerCurve(configPayload, editor);
                break;
            default:
                LOG.debug("Unhandled device update: {}", hexdump(configPayload));
        }
        editor.apply();
    }

    private void decodeGestures(final byte[] configPayload, final Editor editor) {
        for (int i = 3; i + 2 < configPayload.length; i += 3) {
            final RedmiBudsTapType tapType = RedmiBudsTapType.fromCode(configPayload[i]);
            if (tapType == null) {
                LOG.warn("Unknown tap type 0x{}", String.format(Locale.ROOT, "%02X", configPayload[i]));
                continue;
            }

            for (final RedmiBudsPosition position : RedmiBudsPosition.values()) {
                final String key = tapType.getPreferenceKey(position);
                final byte action = configPayload[i + 1 + position.ordinal()];
                if (tapType == RedmiBudsTapType.LONG) {
                    RedmiBudsPrefs.putCode(editor, key, RedmiBudsLongGestureAction.class, action);
                } else {
                    RedmiBudsPrefs.putCode(editor, key, RedmiBudsGestureAction.class, action);
                }
            }
        }
    }

    private void decodeEqualizerCurve(final byte[] configPayload, final Editor editor) {
        for (final RedmiBudsEqualizerBand band : RedmiBudsEqualizerBand.values()) {
            final int index = EQ_CURVE_FIRST_LEVEL + EQ_CURVE_LEVEL_STRIDE * band.ordinal();
            if (index >= configPayload.length) {
                LOG.warn("Equalizer curve is too short for {}", band);
                return;
            }

            RedmiBudsPrefs.putCode(editor, band.getPreferenceKey(), RedmiBudsEqualizerBandLevel.class, configPayload[index]);
        }
    }

    @Nullable
    private GBDeviceEventBatteryInfo parseBatteryInfo(byte batteryInfo, int index) {
        if (batteryInfo == (byte) 0xff) {
            return null;
        }
        GBDeviceEventBatteryInfo batteryEvent = new GBDeviceEventBatteryInfo();
        batteryEvent.state = (batteryInfo & 128) != 0 ? BatteryState.BATTERY_CHARGING : BatteryState.BATTERY_NORMAL;
        batteryEvent.batteryIndex = index;
        batteryEvent.level = (batteryInfo & 127);
        LOG.debug("Battery {}: {}", index, batteryEvent.level);
        return batteryEvent;
    }

    private GBDeviceEvent[] decodeDeviceInfo(byte[] deviceInfoPayload) {

        List<GBDeviceEvent> events = new ArrayList<>();

        GBDeviceEventVersionInfo info = new GBDeviceEventVersionInfo();
        byte[] fw = new byte[4];
        byte[] vidPid = new byte[4];
        byte[] batteryData = new byte[3];
        int i = 0;
        while (i < deviceInfoPayload.length) {
            byte len = deviceInfoPayload[i];
            byte index = deviceInfoPayload[i + 1];
            switch (index) {
                case 0x01:
                    System.arraycopy(deviceInfoPayload, i + 2, fw, 0, 4);
                    break;
                case 0x03:
                    System.arraycopy(deviceInfoPayload, i + 2, vidPid, 0, 4);
                    break;
                case 0x07:
                    System.arraycopy(deviceInfoPayload, i + 2, batteryData, 0, 3);
                    break;
            }
            i += len + 1;
        }

        String fwVersion1 = ((fw[0] >> 4) & 0xF) + "." + (fw[0] & 0xF) + "." + ((fw[1] >> 4) & 0xF) + "." + (fw[1] & 0xF);
        String fwVersion2 = ((fw[2] >> 4) & 0xF) + "." + (fw[2] & 0xF) + "." + ((fw[3] >> 4) & 0xF) + "." + (fw[3] & 0xF);
        String hwVersion = String.format("VID: 0x%02X%02X, PID: 0x%02X%02X", vidPid[0], vidPid[1], vidPid[2], vidPid[3]);

        info.fwVersion = fwVersion1;
        info.fwVersion2 = fwVersion2;
        info.hwVersion = hwVersion;

        events.add(parseBatteryInfo(batteryData[0], 1));
        events.add(parseBatteryInfo(batteryData[1], 2));
        events.add(parseBatteryInfo(batteryData[2], 0));
        events.add(info);

        return events.toArray(new GBDeviceEvent[0]);
    }

    private void decodeDeviceRunInfo(byte[] deviceRunInfoPayload) {
        int i = 0;
        while (i < deviceRunInfoPayload.length) {
            byte len = deviceRunInfoPayload[i];
            byte index = deviceRunInfoPayload[i + 1];
            final Editor editor = getDevicePrefs().getPreferences().edit();
            switch (index) {
                case 0x09:
                    RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL, RedmiBudsAmbientSoundMode.class, deviceRunInfoPayload[i + 2]);
                    break;
                case 0x0A:
                    editor.putBoolean(PREF_REDMI_BUDS_WEARING_DETECTION, deviceRunInfoPayload[i + 2] == 0x00);
            }
            editor.apply();
            i += len + 1;
        }
    }

    private GBDeviceEvent[] decodeDeviceUpdate(Message updateMessage) {
        byte[] updatePayload = updateMessage.getPayload();
        List<GBDeviceEvent> events = new ArrayList<>();

        int i = 0;
        while (i < updatePayload.length) {
            byte len = updatePayload[i];
            byte index = updatePayload[i + 1];
            switch (index) {
                case 0x00:
                    events.add(parseBatteryInfo(updatePayload[i + 2], 1));
                    events.add(parseBatteryInfo(updatePayload[i + 3], 2));
                    events.add(parseBatteryInfo(updatePayload[i + 4], 0));
                    break;
                case 0x04:
                    final Editor editor = getDevicePrefs().getPreferences().edit();
                    RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL, RedmiBudsAmbientSoundMode.class, updatePayload[i + 2]);
                    editor.apply();
                    break;
                default:
                    LOG.debug("Unimplemented device update: {}", hexdump(updatePayload));
            }
            i += len + 1;
        }
        events.add(new GBDeviceEventSendBytes(new Message(MessageType.RESPONSE, Opcode.REPORT_STATUS, updateMessage.getSequenceNumber(), new byte[]{}).encode()));
        return events.toArray(new GBDeviceEvent[0]);
    }

    private GBDeviceEvent[] decodeNotifyConfig(Message notifyMessage) {

        byte[] notifyPayload = notifyMessage.getPayload();
        List<GBDeviceEvent> events = new ArrayList<>();

        int i = 0;
        while (i < notifyPayload.length) {
            byte len = notifyPayload[i];
            byte index = notifyPayload[i + 2];
            switch (index) {
                case 0x0C:
                    LOG.debug("Received earbuds position info");
                    /*
                    e.g. 0C 03
                            0011
                            wearing left, wearing right, left in case, right in case
                     */
                    break;
                case 0x0B:
                    final Editor editor = getDevicePrefs().getPreferences().edit();

                    final byte soundCtrlMode = notifyPayload[i + 3];
                    RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_AMBIENT_SOUND_CONTROL, RedmiBudsAmbientSoundMode.class, soundCtrlMode);

                    final byte strength = notifyPayload[i + 4];
                    if (soundCtrlMode == RedmiBudsAmbientSoundMode.NOISE_CANCELLING.getCode()) {
                        RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_NOISE_CANCELLING_STRENGTH, RedmiBudsNoiseCancellingStrength.class, strength);
                    } else {
                        RedmiBudsPrefs.putCode(editor, PREF_REDMI_BUDS_TRANSPARENCY_STRENGTH, RedmiBudsTransparencyStrength.class, strength);
                    }

                    editor.apply();
                    break;
            }

            i += len + 1;
        }
        events.add(new GBDeviceEventSendBytes(new Message(MessageType.RESPONSE, Opcode.NOTIFY_CONFIG, notifyMessage.getSequenceNumber(), new byte[]{}).encode()));
        return events.toArray(new GBDeviceEvent[0]);
    }

    private GBDeviceEvent[] handleAuthentication(Message authMessage) {
        List<GBDeviceEvent> events = new ArrayList<>();
        switch (authMessage.getOpcode()) {
            case AUTH_CHALLENGE:
                if (authMessage.getType() == MessageType.RESPONSE) {
                    LOG.debug("[AUTH] Received Challenge Response");
                    /*
                        Should check if equal, but does not really matter
                     */
                    LOG.debug("[AUTH] Sending authentication confirmation");
                    events.add(new GBDeviceEventSendBytes(new Message(MessageType.PHONE_REQUEST, Opcode.AUTH_CONFIRM, sequenceNumber++, new byte[]{0x01, 0x00}).encode()));
                } else {
                    byte[] responsePayload = authMessage.getPayload();
                    byte[] challenge = new byte[16];
                    System.arraycopy(responsePayload, 1, challenge, 0, 16);

                    LOG.info("[AUTH] Received Challenge: {}", hexdump(challenge));
                    Authentication auth = new Authentication();
                    byte[] challengeResponse = auth.computeChallengeResponse(challenge);
                    LOG.info("[AUTH] Sending Challenge Response: {}", hexdump(challengeResponse));

                    byte[] payload = new byte[17];
                    payload[0] = 0x01;
                    System.arraycopy(challengeResponse, 0, payload, 1, 16);
                    Message res = new Message(MessageType.RESPONSE, Opcode.AUTH_CHALLENGE, authMessage.getSequenceNumber(), payload);
                    events.add(new GBDeviceEventSendBytes(res.encode()));
                }
                break;
            case AUTH_CONFIRM:
                if (authMessage.getType() == MessageType.RESPONSE) {
                    LOG.debug("[AUTH] Confirmed first authentication step");
                } else {
                    LOG.debug("[AUTH] Received authentication confirmation");
                    Message res = new Message(MessageType.RESPONSE, Opcode.AUTH_CONFIRM, authMessage.getSequenceNumber(), new byte[]{0x01});
                    LOG.debug("[AUTH] Sending final authentication confirmation");
                    events.add(new GBDeviceEventSendBytes(res.encode()));

                    LOG.debug("[INIT] Sending device info request");
                    Message info = new Message(MessageType.PHONE_REQUEST, Opcode.GET_DEVICE_INFO, sequenceNumber++, new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
                    events.add(new GBDeviceEventSendBytes(info.encode()));

                    LOG.debug("[INIT] Sending device run info request");
                    Message runInfo = new Message(MessageType.PHONE_REQUEST, Opcode.GET_DEVICE_RUN_INFO, sequenceNumber++, new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
                    events.add(new GBDeviceEventSendBytes(runInfo.encode()));

                    LOG.debug("[INIT] Sending configuration request");
                    events.add(new GBDeviceEventSendBytes(encodeGetConfig()));
                }
                break;
        }
        return events.toArray(new GBDeviceEvent[0]);
    }

    @Override
    public GBDeviceEvent[] decodeResponse(byte[] responseData) {

        LOG.debug("Incoming message: {}", hexdump(responseData));

        List<GBDeviceEvent> events = new ArrayList<>();

        List<Message> incomingMessages = Message.splitPiggybackedMessages(responseData);

        for (Message message : incomingMessages) {

            LOG.debug("Parsed message: {}", message);

            switch (message.getOpcode()) {
                case AUTH_CHALLENGE:
                case AUTH_CONFIRM:
                    events.addAll(Arrays.asList(handleAuthentication(message)));
                    break;
                case GET_DEVICE_INFO:
                    LOG.debug("[INIT] Received device info");
                    if (getDevice().getState() != State.INITIALIZED) {
                        events.addAll(Arrays.asList(decodeDeviceInfo(message.getPayload())));
                        LOG.debug("[INIT] Device Initialized");
                        events.add(new GBDeviceEventUpdateDeviceState(State.INITIALIZED));
                    }
                    break;
                case GET_DEVICE_RUN_INFO:
                    LOG.debug("[INIT] Received device run info");
                    decodeDeviceRunInfo(message.getPayload());
                    break;
                case REPORT_STATUS:
                    events.addAll(Arrays.asList(decodeDeviceUpdate(message)));
                    break;
                case GET_CONFIG:
                    decodeGetConfig(message.getPayload());
                    break;
                case NOTIFY_CONFIG:
                    events.addAll(Arrays.asList(decodeNotifyConfig(message)));
                    break;
                default:
                    LOG.debug("Unhandled message: {}", message);
                    break;
            }
        }
        return events.toArray(new GBDeviceEvent[0]);
    }
}
