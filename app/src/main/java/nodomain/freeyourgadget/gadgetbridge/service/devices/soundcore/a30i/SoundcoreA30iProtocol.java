package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.a30i;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF;
import static nodomain.freeyourgadget.gadgetbridge.util.GB.hexdump;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.SoundcorePacket;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class SoundcoreA30iProtocol extends SoundcoreProtocolImplV1 {

    private static final Logger LOG = LoggerFactory.getLogger(SoundcoreA30iProtocol.class);

    private static final int battery_earphone_left = 0;
    private static final int battery_earphone_right = 1;
    private static final String OFF = "off";
    private static final String TRANSPARENCY = "transparency";
    private static final String NOISE_CANCELLING = "noise_cancelling";
    private static final String ADAPTIVE_ANC = "adaptive_anc";
    private static final String AMBIENT_SOUND = "ambient_sound";
    private static final String TRANSPORT = "transport";
    private static final String OUTDOOR = "outdoor";
    private static final String INDOOR = "indoor";

    protected SoundcoreA30iProtocol(GBDevice device) {
        super(device);
    }

    @Nullable
    @Override
    public GBDeviceEvent[] decodeResponse(byte[] responseData) {
        SoundcorePacket packet = decodePacket(responseData);

        if (packet == null)
            return null;

        List<GBDeviceEvent> devEvts = new ArrayList<>();

        byte[] payload = packet.getPayload();
        short cmd = packet.getCommand();

        if (cmd == CMD_GET_DEVICE_INFO) {
            // Firmware Version and Serial Number
            String firmware1 = readString(payload, 6, 5);
            String firmware2 = readString(payload, 11, 5);
            String serialNumber = readString(payload, 16, 16);
            devEvts.add(buildVersionInfo(firmware1, firmware2, serialNumber));

            // Battery Info
            handleBatteryInfo(devEvts, payload[2], payload[3]);

            //-------------------------------------------------------------------------
            // Active Noise Cancelling [63 to 70 (7 bytes)]
            String ANC_mode = getANCmode(payload[64], payload[67]);
            String ambient_mode = getAmbientMode(payload[70]);
            int noiseCancellingLevel = getNoiseCancellingLevel(payload[65]);
            boolean wind_noise_reduction = payload[68] == 0x01;
            turn_ANC_settings_on_off(ANC_mode);
            //-------------------------------------------------------------------------
            boolean touchTone = payload[72] == 0x01;
            boolean surround3d = payload[74] == 0x01;
            boolean low_battery = payload[77] == 0x01;
            // AutoPowerOff duration=0 means never (disabled); 1=10min, 2=20min, 3=30min, 4=60min.
            final boolean autoPowerEnabled = payload[75] == 0x01;
            final int autoPowerDuration = autoPowerEnabled ? (payload[76] & 0xFF) + 1 : 0;

            getDevicePrefs().getPreferences().edit()
                    .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE, ANC_mode)
                    .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL, ambient_mode)
                    .putInt(DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL, noiseCancellingLevel)
                    .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION, wind_noise_reduction)

                    .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_3D_SURROUND, surround3d)
                    .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_BATTERY_LOW_TONE, low_battery)
                    .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE, touchTone)
                    .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF, String.valueOf(autoPowerDuration))
                    .apply();

            LOG.debug("Message: CMD_GET_DEVICE_INFO");

        }else if(cmd == CMD_SESSION_INIT) {
            LOG.debug("Message: Initialisation finished!");
        }else if(cmd == CMD_NOTIFY_BATTERY_INFO){
            handleBatteryInfo(devEvts, payload[0], payload[1]);
            LOG.debug("Message: CMD_NOTIFY_BATTERY_INFO");
        }else {
            LOG.debug("cmd          :" + cmd);
            LOG.debug("Payload      :" + hexdump(payload));
            LOG.debug("Respond Data:" + hexdump(responseData));
        }
        return devEvts.toArray(new GBDeviceEvent[devEvts.size()]);
    }

    /**
     * It greys out some ANC settings on the UI, depending on the
     * selected ANC_mode.
     * The (de)activation is useful because some settings do not make
     * sense in all modes.
     */
    private void turn_ANC_settings_on_off(String ANC_mode){
        boolean ambient_mode;
        boolean ANC_sound_level;
        boolean wind_noise_reduction;
        switch (ANC_mode) {
            case OFF -> {
                ambient_mode = true;
                ANC_sound_level = true;
                wind_noise_reduction = true;
            }
            case TRANSPARENCY, ADAPTIVE_ANC -> {
                ambient_mode = true;
                ANC_sound_level = true;
                wind_noise_reduction = false;
            }
            case NOISE_CANCELLING -> {
                ambient_mode = true;
                ANC_sound_level = false;
                wind_noise_reduction = false;
            }
            case AMBIENT_SOUND -> {
                ambient_mode = false;
                ANC_sound_level = true;
                wind_noise_reduction = false;
            }
            default -> {
                // Unknown state (should never happen), but if so, allow the user to select
                // all options so they can make the best out of it.
                ambient_mode = false;
                ANC_sound_level = false;
                wind_noise_reduction = false;
            }
        }
        getDevicePrefs().getPreferences().edit()
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_A30I_DISABLE_OPTION_AMBIENT_MODE, ambient_mode)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_A30I_DISABLE_OPTION_AMBIENT_SOUND_LEVEL, ANC_sound_level)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_A30I_DISABLE_OPTION_WIND_NOISE_REDUCTION, wind_noise_reduction)
                .apply();
    }

    /**
     * It returns the current ANC level.
     */
    private int getNoiseCancellingLevel(byte noise_level){
        return switch (noise_level) {
            case 0x51 -> 5;
            case 0x45 -> 4;
            case 0x35 -> 3;
            case 0x25 -> 2;
            case 0x15 -> 1;
            default -> 1;
        };
    }

    /**
     * It returns the current ambient mode.
     */
    private String getAmbientMode(byte ambient_mode){
        return switch (ambient_mode) {
            case 0x00 -> TRANSPORT;
            case 0x01 -> OUTDOOR;
            case 0x02 -> INDOOR;
            default -> "Unknown Mode";
        };
    }

    /**
     * It returns the current ANC Mode.
     */
    private String getANCmode(byte ANC_select, byte ANC_mode){
        return switch (ANC_select) {
            case 0x00 -> switch (ANC_mode) {
                case 0x00 -> NOISE_CANCELLING;
                case 0x01 -> ADAPTIVE_ANC;
                case 0x02 -> AMBIENT_SOUND;
                default -> "Unknown Mode";
            };
            case 0x01 -> TRANSPARENCY;
            case 0x02 -> OFF;
            default -> "Unknown Mode";
        };
    }

    /**
     * It calculates the Battery Level for both In-Ear speaker.
     */
    private void handleBatteryInfo(List<GBDeviceEvent> devEvts, byte batteryLeft, byte batteryRight) {
        int batteryLeftLevel = (batteryLeft + 1) * 10;
        int batteryRightLevel = (batteryRight + 1) * 10;
        devEvts.add(buildBatteryInfo(battery_earphone_left, batteryLeftLevel));
        devEvts.add(buildBatteryInfo(battery_earphone_right, batteryRightLevel));
    }

    @Nullable
    @Override
    public byte[] encodeSendConfiguration(String config) {
        Prefs prefs = getDevicePrefs();

        switch (config) {
            // Control
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE:
                // It enables/disables the sound if the user touches the device button.
                final boolean touchTone = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE, false);
                return encodeBooleanCommand(CMD_SET_TOUCH_TONE, touchTone);

            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_3D_SURROUND:
                // It enables/disables the 3D-surround sound.
                final boolean surround3d = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_3D_SURROUND, false);
                return encodeBooleanCommand(CMD_SET_3D_SURROUND, surround3d);

            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_BATTERY_LOW_TONE:
                // It enables/disables the low battery tone of the device at 20% and 10%.
                final boolean low_battery = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_BATTERY_LOW_TONE, false);
                return encodeBooleanCommand(CMD_SET_BATTERY_LOW_TONE, low_battery);

            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF:
                // IIt sets, after which time the speakers turn themselves off after
                // they are disconnected from the phone: supported values:
                // Disabled, 10 min, 20 min, 30 min, 60 min
                int duration = Integer.parseInt(prefs.getString(PREF_SOUNDCORE_AUTO_POWER_OFF, "3"));
                return encodeAutoPowerOff(CMD_SET_AUTO_POWER_OFF, duration, (byte) 0x03);

            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_FIND_LEFT_EARBUD:
                // It enables/disables a loud sound on the left earbud.
                // IMPORTANT: Warn the user before enabling it, so they
                // can remove their headphones from their ears to prevent
                // potential hearing damage.
                boolean find_left_earbud = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_FIND_LEFT_EARBUD, 0) == 0;
                return encode_find_earbud(true, find_left_earbud);

            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_FIND_RIGHT_EARBUD:
                // It enables/disables a loud sound on the right earbud.
                // IMPORTANT: Warn the user before enabling it, so they
                // can remove their headphones from their ears to prevent
                // potential hearing damage.
                boolean find_right_earbud = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_FIND_RIGHT_EARBUD, 0) == 0;
                return encode_find_earbud(false, find_right_earbud);

            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE, DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL,
                 DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL, DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION:
                // It sets the ANC mode. It always sends the same command (with different values),
                // independent of which part of the ANC setting was changed.
                String ANC_mode = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE, OFF);
                String ambient_mode = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL, TRANSPORT);
                int noiseCancellingLevel = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL, 0);
                boolean wind_noise_reduction = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION, false);

                turn_ANC_settings_on_off(ANC_mode);
                byte[] ANC_result = build_ANC_mode_value(ANC_mode, ambient_mode, noiseCancellingLevel, wind_noise_reduction);
                if(ANC_result != null) {
                    return ANC_result;
                }else {
                    // An unsupported action was selected; do not send the result to the device.
                    return super.encodeSendConfiguration(config);
                }

            default:
                LOG.debug("Unsupported CONFIG: " + config);
        }
        return super.encodeSendConfiguration(config);
    }

    /**
     * It builds the content for the ANC command.
     * It always needs all information, independent of the mode.
     * @return The content for the ANC command; it returns null if an unknown value was requested.
     */
    private byte[] build_ANC_mode_value(String ANC_mode, String ambient_mode, int noiseCancellingLevel, boolean wind_noise_reduction){
        ByteBuffer buf = ByteBuffer.allocate(7);
        byte mode; // ANC mode seems to be twice in the array.

        switch (ANC_mode) {
            case OFF -> mode = 0x02;
            case TRANSPARENCY -> mode = 0x01;
            case NOISE_CANCELLING, ADAPTIVE_ANC, AMBIENT_SOUND -> mode = 0x00;
            default -> {
                return null;
            }
        }

        buf.put(mode);

        switch (noiseCancellingLevel){
            case 5 -> buf.put((byte) 0x51);
            case 4 -> buf.put((byte) 0x45);
            case 3 -> buf.put((byte) 0x35);
            case 2 -> buf.put((byte) 0x25);
            case 1 -> buf.put((byte) 0x15);
            default -> {
                return null;
            }
        }

        buf.put(mode); // Probably a duplicated value cached from before.

        switch (ANC_mode) {
            case ADAPTIVE_ANC -> buf.put((byte) 0x01);
            case AMBIENT_SOUND -> buf.put((byte) 0x02);
            case NOISE_CANCELLING, OFF, TRANSPARENCY -> buf.put((byte) 0x00);
            default -> {
                return null;
            }
        }

        if (wind_noise_reduction) {
            buf.put((byte) 0x01);
        }else {
            buf.put((byte) 0x00);
        }

        buf.put((byte) 0x00); // Byte seems to be unused.

        switch (ambient_mode){
            case OUTDOOR -> buf.put((byte) 0x01);
            case INDOOR -> buf.put((byte) 0x02);
            case TRANSPORT -> buf.put((byte) 0x00);
            default -> {
                return null;
            }
        }
        return encodeCommand(CMD_SET_AUDIO_MODE, buf.array());
    }


    /**
     * If disable_find_device_tone is false, one of the earbuds will play a loud sound
     * (depending on: is_left_earbud).
     * IMPORTANT: Warn the user before setting disable_find_device_tone to false, so
     * they can remove their headphones from their ears, to prevent potential hearing damage.
     */
    private byte[] encode_find_earbud(boolean is_left_earbud, boolean disable_find_device_tone){
        if(disable_find_device_tone){
            return encodeCommand(CMD_SET_FIND_DEVICE, new byte[]{
                    0x00, // left
                    0x00, // right
                    0x00
            });
        }else {
            if (is_left_earbud) {
                return encodeCommand(CMD_SET_FIND_DEVICE, new byte[]{
                        0x01, // left
                        0x00, // right
                        0x00
                });
            } else {
                return encodeCommand(CMD_SET_FIND_DEVICE, new byte[]{
                        0x00, // left
                        0x01, // right
                        0x00
                });
            }
        }
    }

    /** Sent after CMD_GET_DEVICE_INFO, to finalise the session with the device. */
    public byte[] encodeSessionInitRequest() {
        return encodeRequest(CMD_SESSION_INIT);
    }

    /** Request more device info during init; currently not used because
     * it is unclear which additional information are in the returned message. */
    public byte[] encodeSessionInitMoreInfoRequest() {
        return encodeRequest(CMD_GET_UNKNOWN_DATA_0105);
    }
}
