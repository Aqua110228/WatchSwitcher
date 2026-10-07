package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

enum class CardoMessage(val command: Byte, val hasLength: Boolean) {
    GET(0x00.toByte(), true),
    INIT(0x03.toByte(), false),
    SET(0x10.toByte(), true),
    SUBSCRIBE(0x22.toByte(), false),
    CONTROL(0x30.toByte(), false),
    CONFIG(0x40.toByte(), true),
    DEVICE_ALIAS(0x42.toByte(), true),
    DEVICE_SERIAL_NUMBER(0x43.toByte(), true),
    DEVICE_STATE(0x50.toByte(), false),
    BATTERY_STATUS(0x51.toByte(), false);

    companion object {
        fun getByCommand(cmd: Byte): CardoMessage? = entries.find { it.command == cmd }
    }
}