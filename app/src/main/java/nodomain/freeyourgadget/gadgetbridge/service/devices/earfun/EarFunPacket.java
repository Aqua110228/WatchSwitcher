package nodomain.freeyourgadget.gadgetbridge.service.devices.earfun;

import static nodomain.freeyourgadget.gadgetbridge.util.GB.hexdump;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class EarFunPacket {

    /*
     EarFun uses the structure of Gaia packages, a checksum is never used

     0 bytes  1         2        3        4        5        6        7        8          9    len+8
     +--------+---------+--------+--------+--------+--------+--------+--------+ +--------+--------+
     |   SOP  | VERSION | FLAGS  | LENGTH |    VENDOR ID    |   COMMAND ID    | | PAYLOAD   ...   |
     +--------+---------+--------+--------+--------+--------+--------+--------+ +--------+--------+
    */
    private static final Logger LOG = LoggerFactory.getLogger(EarFunPacket.class);
    private static final int HEADER_LENGTH = 8;
    private static final byte START_OF_PACKET = (byte) 0xff;
    public static final byte DEFAULT_VERSION = (byte) 0x04;
    private static final List<Byte> SUPPORTED_VERSIONS = Arrays.asList(DEFAULT_VERSION, (byte) 0x03);
    private static final byte FLAGS_NO_CHECKSUM = (byte) 0x00;
    public static final short VENDOR_ID_V1_V2 = (short) 0x000A;
    public static final short VENDOR_ID_V3 = (short) 0x001D;
    private static final List<Short> SUPPORTED_VENDOR_IDS = Arrays.asList(VENDOR_ID_V1_V2, VENDOR_ID_V3);
    static final int COMMAND_MASK = 0x7FFF;
    static final int ACK_MASK = 0x8000;
    public static final int COMMAND_INTENT_GET = 0x0080;
    public static final int COMMAND_TYPE_MASK = 0x7F00;
    public static final int COMMAND_CODE_MASK = 0x00FF;
    public static final byte TYPE_CONFIGURATION = (byte) 0x01;
    public static final byte TYPE_CONTROL = (byte) 0x02;
    public static final byte TYPE_STATUS = (byte) 0x03;
    public static final byte TYPE_FEATURE = (byte) 0x05;
    public static final byte TYPE_DATA_TRANSFER = (byte) 0x06;
    public static final byte TYPE_DEBUG = (byte) 0x07;
    public static final byte TYPE_NOTIFICATION = (byte) 0x40;
    public static final byte TYPE_ACKNOWLEDGE = (byte) 0x80;

    public enum Command {
        /*
         A Command has the following structure:
         0 bytes  1         2
         +--------+---------+
         |  Type  |   Code  |
         +--------+---------+

         to bring some structure into the commands, we use the following naming conventions:
         REQUEST_.. commands to request a status, payload is usually empty in this case
         RESPONSE_.. commands that are send back from the headphones as response to a REQUEST
         SET_.. commands that can be used to set a value
        */
        REQUEST_RESPONSE_GAIA_API_VERSION((short) 0x0300), // 03 03 01 = GAIA v3, API v3.1
        REQUEST_RESPONSE_APPLICATION_VERSION((short) 0x0304), // 02 00 = APP v2.0
        FACTORY_RESET((short) 0x0305),
        REQUEST_RESPONSE_BATTERY_STATE_LEFT((short) 0x0306),
        REQUEST_RESPONSE_BATTERY_STATE_RIGHT((short) 0x0307),
        COMMAND_REBOOT((short) 0x0308),
        REQUEST_RESPONSE_FIRMWARE_VERSION((short) 0x0309),
        SET_TOUCH_ACTION((short) 0x030A),
        REQUEST_RESPONSE_TOUCH_ACTION((short) 0x030B),
        SET_GAME_MODE((short) 0x0312),
        REQUEST_RESPONSE_GAME_MODE((short) 0x0313),
        SET_AMBIENT_SOUND((short) 0x0314),
        REQUEST_RESPONSE_AMBIENT_SOUND((short) 0x0315),
        SET_DEVICENAME((short) 0x0316),
        REQUEST_RESPONSE_BATTERY_STATE_CASE((short) 0x0317),
        REQUEST_RESPONSE_DEVICE_COLOR((short) 0x0318), // 00 = black
        REQUEST_RESPONSE_CONNECT_TWO_DEVICES((short) 0x0326), // 0001
        SET_CONNECT_TWO_DEVICES((short) 0x0327), // 00, 01
        CONNECT_DEVICE((short) 0x0329), // 00 = enable first device, 01 = enable second device SET
        DISCONNECT_DEVICE((short) 0x032A), // 00 = disable first device, 01 = disable second device SET
        TRIGGER_PAIRING((short) 0x032B),
        REQUEST_RESPONSE_CONNECTED_DEVICES((short) 0x032C), // names of paired devices + something else
        SET_AUDIO_CODEC((short) 0x032E), // 00 = Stable Connection, 01 = aptX, 03 = aptX Adaptive, 13 = aptX Lossless, 08 = LDAC
        REQUEST_RESPONSE_AUDIO_CODEC((short) 0x032F), // 0013
        SET_MICROPHONE_MODE((short) 0x0330), // 00 = auto, 01 = left, 02 = right
        REQUEST_RESPONSE_MICROPHONE_MODE((short) 0x0331), // 0000
        SET_FIND_DEVICE((short) 0x0332), // 00 = off, 01 = left, 02 = right, 03 = both
        REQUEST_RESPONSE_FIND_DEVICE((short) 0x0333), // 0000
        SET_TOUCH_MODE((short) 0x0334), // 00 = both, 01 = none, 02 = right, 03 = left
        REQUEST_RESPONSE_TOUCH_MODE((short) 0x0335), // 0000
        REQUEST_RESPONSE_PHONE_CALL_STATE((short) 0x0337),
        SET_VOICE_PROMPT_VOLUME((short) 0x0338), // 00 (max) - 04 (min)
        REQUEST_RESPONSE_VOICE_PROMPT_VOLUME((short) 0x0339), // 0000 (max) - 0004 (min)
        SET_ANC_MODE((short) 0x033A),
        REQUEST_RESPONSE_ANC_MODE((short) 0x033B),
        SET_TRANSPARENCY_MODE((short) 0x033C),
        REQUEST_RESPONSE_TRANSPARENCY_MODE((short) 0x033D),
        SET_DISABLE_IN_EAR_DETECTION((short) 0x0349), // 00, 01
        REQUEST_RESPONSE_DISABLE_IN_EAR_DETECTION((short) 0x034A), // 0000
        SET_ADVANCED_AUDIO_MODE((short) 0x034B), // 00 = Google Fast Pair, 01 = LE Audio
        REQUEST_RESPONSE_ADVANCED_AUDIO_MODE((short) 0x034C), // 0000
        REQUEST_RESPONSE_AUDIO_MODE((short) 0x034D), // 0003 with "AptX" (any mode) or 0002 with "Stable Connection" and "LDAC" can also be 0000, send if second device connects
        REQUEST_RESPONSE_PLAY_STATE((short) 0x0350), // 01: not playing, 02: playing
        SET_CONTROL_PLAY((short) 0x0351), // 01 = turn off device
        SET_VOICE_PROTECT((short) 0x0353), // 01 = 90 dB, 02 = 80 dB, 03 = 70 dB, 04 = 60 dB, 05 = 50 dB
        REQUEST_RESPONSE_VOICE_PROTECT((short) 0x0354),
        SET_LEFTRIGHT_BALANCE((short) 0x0355), // 50 = default, 0 = left only, 100 = right only
        REQUEST_RESPONSE_LEFTRIGHT_BALANCE((short) 0x0356),
        REQUEST_RESPONSE_PLAY_PHONE_LIST((short) 0x0358),

        // GAIA v3 basic feature
        BASIC_GET_SUPPORTED_FEATURES((short) 0x0001, VENDOR_ID_V3),
        BASIC_REGISTER_NOTIFICATION((short) 0x0007, VENDOR_ID_V3),
        BASIC_SET_TRANSPORT_PARAMETER((short) 0x000D, VENDOR_ID_V3),
        // Response: 0D 01 05 01 08 07 01 03 06 02 07 01 00 05
        // Features: battery v1, music processing v1, audio curation v7, earbud v3, upgrade v2, handset v1, basic v5
        BASIC_RESPONSE_GET_SUPPORTED_FEATURES((short) 0x0101, VENDOR_ID_V3),
        BASIC_RESPONSE_REGISTER_NOTIFICATION((short) 0x0107, VENDOR_ID_V3),
        BASIC_ERROR_REGISTER_NOTIFICATION((short) 0x0187, VENDOR_ID_V3),
        BASIC_ERROR_SET_TRANSPORT_PARAMETER((short) 0x018D, VENDOR_ID_V3),

        // GAIA v3 earbud feature
        EARBUD_NOTIFICATION_UNKNOWN((short) 0x0282, VENDOR_ID_V3), // 01

        // GAIA v3 audio curation feature
        AC_NOTIFICATION_STATE_CHANGE((short) 0x1080, VENDOR_ID_V3), // 0100 -> 0101 if ANC not off
        AC_NOTIFICATION_MODE_CHANGE((short) 0x1081, VENDOR_ID_V3), // 01010000 <- otherwise, 0A010000 <- ANC Transparent
        AC_NOTIFICATION_GAIN_CHANGE((short) 0x1082, VENDOR_ID_V3), // 01010000 -> 01014646
        AC_NOTIFICATION_TOGGLE_CONFIG((short) 0x1083, VENDOR_ID_V3), // 0101, 0205, 03FF
        AC_NOTIFICATION_SCENARIO_CONFIG((short) 0x1084, VENDOR_ID_V3), // 01FF, 02FF, 03FF, 04FF
        AC_NOTIFICATION_DEMO_STATE((short) 0x1085, VENDOR_ID_V3), // 00

        // GAIA v3 non-standard
        SET_EQUALIZER_BAND((short) 0x0E01, VENDOR_ID_V3),
        REQUEST_RESPONSE_EQUALIZER_CONFIG((short) 0x0E02, VENDOR_ID_V3),
        RESPONSE_EQUALIZER_BAND((short) 0x0F81, VENDOR_ID_V3);

        public final short commandId;
        public final short vendorId;
        public final byte version;

        Command(short commandId) {
            this(commandId, VENDOR_ID_V1_V2, DEFAULT_VERSION);
        }

        Command(short commandId, short vendorId) {
            this(commandId, vendorId, DEFAULT_VERSION);
        }

        Command(short commandId, short vendorId, byte version) {
            this.commandId = commandId;
            this.vendorId = vendorId;
            this.version = version;
        }

        public short getCommand() {
            return (short) (commandId & COMMAND_MASK);
        }

        public byte getType() {
            return (byte) ((commandId & COMMAND_TYPE_MASK) >> 8);
        }

        public byte getCode() {
            return (byte) ((commandId & COMMAND_CODE_MASK));
        }

        public boolean isAcknowledgement() {
            return (commandId & ACK_MASK) != 0;
        }

        public boolean isIntentGet() {
            return (commandId & COMMAND_INTENT_GET) != 0;
        }

        public static String describeType(byte type) {
            switch (type) {
                case TYPE_CONFIGURATION:
                    return "configuration";
                case TYPE_CONTROL:
                    return "control";
                case TYPE_STATUS:
                    return "status";
                case TYPE_FEATURE:
                    return "feature";
                case TYPE_DATA_TRANSFER:
                    return "datatransfer";
                case TYPE_DEBUG:
                    return "debug";
                case TYPE_NOTIFICATION:
                    return "notification";
                case TYPE_ACKNOWLEDGE:
                    return "acknowledge";
                default:
                    return "unknown";
            }
        }

        public static String describeCommandId(short commandId) {
            byte type = (byte) ((commandId & COMMAND_TYPE_MASK) >> 8);
            byte code = (byte) ((commandId & COMMAND_CODE_MASK));
            return "Command{" +
                    " commandId=" + hexdumpValue(commandId) +
                    ", type=" + hexdumpValue(type) + String.format(" (%s)", describeType(type)) +
                    ", code=" + hexdumpValue(code) +
                    " }";
        }

        public static Command getCommandById(short commandId) {
            short commandValue = (short) (commandId & COMMAND_MASK);
            return Arrays.stream(Command.values())
                    .filter(command -> command.commandId == commandValue)
                    .findFirst()
                    .orElseGet(() -> {
                        LOG.error("unknown command: {}", describeCommandId(commandId));
                        return null;
                    });
        }

        @NonNull
        @Override
        public String toString() {
            return describeCommandId(commandId);
        }
    }

    private final Command command;
    private final byte[] payload;

    public EarFunPacket(Command command) {
        this(command, new byte[0]);
    }

    public EarFunPacket(Command command, byte payload) {
        this(command, new byte[]{payload});
    }

    public EarFunPacket(Command command, byte[] payload) {
        this.command = command;
        this.payload = payload;
    }

    public Command getCommand() {
        return command;
    }

    public byte[] getPayload() {
        return payload;
    }

    public static EarFunPacket decode(ByteBuffer buf) {
        if (buf.remaining() < HEADER_LENGTH)
            return null;

        if (buf.get() != START_OF_PACKET) {
            LOG.error("Invalid start of packet: {}", hexdump(buf.array()));
            return null;
        }

        byte version = buf.get();
        if (!SUPPORTED_VERSIONS.contains(version)) {
            LOG.error("Invalid version: {} in packet {}", hexdumpValue(version), hexdump(buf.array()));
            return null;
        }

        byte flags = buf.get();
        if (flags != FLAGS_NO_CHECKSUM) {
            LOG.error("Invalid flags: {} in packet {}", hexdumpValue(flags), hexdump(buf.array()));
            return null;
        }
        byte length = buf.get();

        short vendorId = buf.getShort();
        if (!SUPPORTED_VENDOR_IDS.contains(vendorId)) {
            LOG.error("Invalid vendor ID: {} in packet {}", hexdumpValue(vendorId), hexdump(buf.array()));
            return null;
        }

        short commandId = buf.getShort();
        Command command = Command.getCommandById(commandId);
        if (command == null) {
            LOG.error("Received unknown command ID: {} in packet {}", hexdumpValue(commandId), hexdump(buf.array()));
            return null;
        }

        byte[] payload = new byte[length];
        buf.get(payload);

        return new EarFunPacket(command, payload);
    }

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH + payload.length);
        buf.put(START_OF_PACKET);
        buf.put(command.version);
        buf.put(FLAGS_NO_CHECKSUM);
        buf.put((byte) payload.length);
        buf.putShort(command.vendorId);
        buf.putShort(command.commandId);
        buf.put(payload);
        LOG.debug("encoded package: {}", hexdump(buf.array()));
        return buf.array();
    }

    public static String hexdumpValue(byte value) {
        return String.format("%02X", value & 0xFF);
    }

    public static String hexdumpValue(short value) {
        return String.format("%04X", value & 0xFFFF);
    }

    @NonNull
    @Override
    public String toString() {
        return "EarFunGaiaPacket{" +
                " command=" + command.toString() +
                ", length=" + payload.length +
                ", payload=" + hexdump(payload) +
                '}';
    }
}
