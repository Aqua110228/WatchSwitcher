package nodomain.freeyourgadget.gadgetbridge.service.devices.eightbitdo

import nodomain.freeyourgadget.gadgetbridge.util.CheckSums
import nodomain.freeyourgadget.gadgetbridge.util.HidKey
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * 8BitDo Micro keyboard-mode protocol. Every command transfers a slice of a
 * blob, with the same envelope in both directions:
 *
 * ```
 * request:  04 TYPE 00 P1 P2  | length u16 | crc u16 | total u32 | offset u32 | data
 * response: 04 STATUS TYPE 00 | length u16 | crc u16 | total u32 | offset u32 | data
 * ```
 *
 * The crc is CRC-16/MODBUS of the data. The keymap is a [KEYMAP_SIZE]-byte blob transferred
 * in [CHUNK_LENGTH]-byte slices.
 */
object EightBitDoProtocol {
    const val TYPE_KEYMAP_WRITE = 0x01
    const val TYPE_KEYMAP_READ = 0x02
    const val TYPE_BUTTON_STATE = 0x03
    const val TYPE_KEYMAP_COMMIT = 0x06
    const val TYPE_FIRMWARE = 0x0b
    const val TYPE_REALTIME = 0x50
    const val TYPE_HELLO = 0x5a

    const val CHUNK_LENGTH = 45
    const val KEYMAP_SIZE = CHUNK_LENGTH * 4
    const val MAX_KEYS_PER_SLOT = 4

    val CHUNK_OFFSETS: List<Int> = (0 until KEYMAP_SIZE step CHUNK_LENGTH).toList()

    private const val MAGIC = 0x04
    private const val REQUEST_HEADER_SIZE = 5
    private const val RESPONSE_HEADER_SIZE = 4
    private const val ENVELOPE_SIZE = 12

    private const val FIRMWARE_VERSION_ADDRESS = 0x01017040
    private const val FIRMWARE_VERSION_SIZE = 4
    private const val BUTTON_STATE_SIZE = 12
    private const val REALTIME_P2 = 0x05
    private const val KEYMAP_COMMIT_P1 = 0x5b

    private const val SLEEP_FLAG_INDEX = 3
    private const val MARKER_INDEX = 4
    private const val SLOTS_INDEX_FIRST = 12
    private const val SLOTS_INDEX_SECOND = 52
    private const val SLOTS_PER_GROUP = 8
    private const val SLOT_SIZE = 4

    private val SLEEP_FLAG_PREFIX = byteArrayOf(0xa0.toByte(), 0x27, 0x00)
    private val MARKER = byteArrayOf(0x11, 0x09, 0x20, 0x20, 0x11, 0x09, 0x20, 0x20)

    /**
     * A decoded response. [data] is empty for an ACK.
     */
    @Suppress("ArrayInDataClass")
    data class Response(
        val status: Int,
        val type: Int,
        val total: Int,
        val offset: Int,
        val data: ByteArray,
    )

    /**
     * The keymap of the controller: the disable-sleep flag and the HID codes for every button.
     */
    data class Keymap(
        val disableSleep: Boolean,
        val mapping: Map<EightBitDoButton, List<HidKey>>,
    )

    fun encodeHello(): ByteArray = encodeRequest(TYPE_HELLO, total = 1, data = ByteArray(1))

    fun encodeFirmwareQuery(): ByteArray = encodeRequest(
        TYPE_FIRMWARE,
        total = FIRMWARE_VERSION_SIZE,
        offset = FIRMWARE_VERSION_ADDRESS,
        data = ByteArray(FIRMWARE_VERSION_SIZE),
    )

    fun encodeKeymapCommit(): ByteArray = encodeRequest(TYPE_KEYMAP_COMMIT, p1 = KEYMAP_COMMIT_P1)

    fun encodeRealtime(enabled: Boolean): ByteArray =
        encodeRequest(TYPE_REALTIME, p1 = if (enabled) 1 else 0, p2 = REALTIME_P2)

    fun encodeRealtimeEmptyButtonState(): ByteArray =
        encodeRequest(TYPE_BUTTON_STATE, total = BUTTON_STATE_SIZE, data = ByteArray(BUTTON_STATE_SIZE))

    fun encodeKeymapRead(chunkOffset: Int): ByteArray {
        require(chunkOffset in CHUNK_OFFSETS) { "Invalid chunk offset $chunkOffset" }
        return encodeRequest(
            TYPE_KEYMAP_READ,
            total = KEYMAP_SIZE,
            offset = chunkOffset,
            data = ByteArray(CHUNK_LENGTH)
        )
    }

    /**
     * The write request for the chunk of [buffer] that starts at [chunkOffset].
     */
    fun encodeKeymapWrite(chunkOffset: Int, buffer: ByteArray): ByteArray {
        require(chunkOffset in CHUNK_OFFSETS) { "Invalid chunk offset $chunkOffset" }
        require(buffer.size == KEYMAP_SIZE) { "Keymap buffer must have $KEYMAP_SIZE bytes" }
        return encodeRequest(
            TYPE_KEYMAP_WRITE,
            total = KEYMAP_SIZE,
            offset = chunkOffset,
            data = buffer.copyOfRange(chunkOffset, chunkOffset + CHUNK_LENGTH),
        )
    }

    /**
     * The keymap buffer for [keymap].
     */
    fun encodeKeymap(keymap: Keymap): ByteArray {
        val buffer = ByteArray(KEYMAP_SIZE)
        SLEEP_FLAG_PREFIX.copyInto(buffer)
        buffer[SLEEP_FLAG_INDEX] = (if (keymap.disableSleep) 1 else 0).toByte()
        MARKER.copyInto(buffer, MARKER_INDEX)
        for (button in EightBitDoButton.entries) {
            val keys = slotKeys(keymap.mapping[button].orEmpty())
            val slotIndex = slotIndex(button)
            keys.forEachIndexed { i, key -> buffer[slotIndex + i] = key.usage.toByte() }
        }
        return buffer
    }

    fun decodeKeymap(buffer: ByteArray): Keymap {
        require(buffer.size == KEYMAP_SIZE) { "Keymap buffer must have $KEYMAP_SIZE bytes" }
        val mapping = EightBitDoButton.entries.associateWith { button ->
            val slotIndex = slotIndex(button)
            buffer.copyOfRange(slotIndex, slotIndex + SLOT_SIZE)
                .takeWhile { it.toInt() != 0 }
                .mapNotNull { HidKey.fromUsage(it.toInt() and 0xff) }
        }
        return Keymap(buffer[SLEEP_FLAG_INDEX].toInt() == 1, mapping)
    }

    /**
     * The keys of one slot as written to the device: distinct, in the given order, at most
     * [MAX_KEYS_PER_SLOT].
     */
    fun slotKeys(keys: List<HidKey>): List<HidKey> = keys.distinct().take(MAX_KEYS_PER_SLOT)

    /**
     * The response in a frame, or null if the frame is not a well-formed response. The crc is
     * not validated for ACKs, as it seems to always be invalid.
     */
    fun decodeResponse(frame: ByteArray): Response? {
        if (frame.size < RESPONSE_HEADER_SIZE + ENVELOPE_SIZE || frame[0].toInt() != MAGIC) {
            return null
        }
        val envelope = ByteBuffer.wrap(frame, RESPONSE_HEADER_SIZE, ENVELOPE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val length = envelope.getShort().toInt() and 0xffff
        val crc = envelope.getShort().toInt() and 0xffff
        val total = envelope.getInt()
        val offset = envelope.getInt()
        val dataIndex = RESPONSE_HEADER_SIZE + ENVELOPE_SIZE
        val data = when {
            frame.size < dataIndex + length -> ByteArray(0)
            else -> frame.copyOfRange(dataIndex, dataIndex + length)
        }
        if (data.isNotEmpty() && CheckSums.getCRC16ansi(data) != crc) {
            return null
        }
        return Response(
            status = frame[1].toInt() and 0xff,
            type = frame[2].toInt() and 0xff,
            total = total,
            offset = offset,
            data = data,
        )
    }

    fun decodeFirmwareVersion(response: Response): String? {
        if (response.type != TYPE_FIRMWARE || response.data.size < FIRMWARE_VERSION_SIZE) {
            return null
        }
        val version = ByteBuffer.wrap(response.data).order(ByteOrder.LITTLE_ENDIAN).getInt()
        return String.format(Locale.ROOT, "%d.%02d", version / 100, version % 100)
    }

    /**
     * The keymap chunk in a response, as a pair of chunk offset and [CHUNK_LENGTH] bytes.
     */
    fun decodeKeymapChunk(response: Response): Pair<Int, ByteArray>? {
        if (response.type != TYPE_KEYMAP_READ || response.offset !in CHUNK_OFFSETS || response.data.size != CHUNK_LENGTH) {
            return null
        }
        return response.offset to response.data
    }

    fun decodeButtonState(response: Response): Set<EightBitDoButton>? {
        if (response.type != TYPE_BUTTON_STATE || response.data.size < 3) {
            return null
        }
        return EightBitDoButton.entries.filter { button ->
            val stateByte = response.data[button.stateByte].toInt() and 0xff
            stateByte and button.stateBit != 0
        }.toSet()
    }

    private fun encodeRequest(
        type: Int,
        p1: Int = 0,
        p2: Int = 0,
        total: Int = 0,
        offset: Int = 0,
        data: ByteArray = ByteArray(0),
    ): ByteArray {
        val request =
            ByteBuffer.allocate(REQUEST_HEADER_SIZE + ENVELOPE_SIZE + data.size).order(ByteOrder.LITTLE_ENDIAN)
        request.put(MAGIC.toByte())
        request.put(type.toByte())
        request.put(0.toByte())
        request.put(p1.toByte())
        request.put(p2.toByte())
        request.putShort(data.size.toShort())
        request.putShort(CheckSums.getCRC16ansi(data).toShort())
        request.putInt(total)
        request.putInt(offset)
        request.put(data)
        return request.array()
    }

    private fun slotIndex(button: EightBitDoButton): Int {
        val group = button.ordinal / SLOTS_PER_GROUP
        val slot = button.ordinal % SLOTS_PER_GROUP
        return (if (group == 0) SLOTS_INDEX_FIRST else SLOTS_INDEX_SECOND) + slot * SLOT_SIZE
    }
}
