package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol

class BitReader(private val bytes: ByteArray) {
    private var currentBit = 0
    private val totalBits = bytes.size * 8

    fun <T> read(field: CardoField<T>): T {
        check(currentBit + field.bitSize <= totalBits) {
            "Bit structure exceeds available bits: field='${field.name}' " +
                    "requires ${field.bitSize} bit starting from $currentBit, available $totalBits"
        }
        var value = 0L
        repeat(field.bitSize) {
            val byteIndex = currentBit / 8
            val bitIndex = 7 - (currentBit % 8)
            val bitValue = (bytes[byteIndex].toInt() shr bitIndex) and 1
            value = (value shl 1) or bitValue.toLong()
            currentBit++
        }
        return field.decode(value)
    }

    fun bitsConsumed(): Int = currentBit
}

class BitWriter(private val totalBytes: Int) {
    private val result = ByteArray(totalBytes)
    private var currentBit = 0

    fun <T> write(field: CardoField<T>, value: T) {
        val raw = field.encode(value)
        check(currentBit + field.bitSize <= totalBytes * 8) {
            "Bit structure exceeds target buffer: field='${field.name}'"
        }
        repeat(field.bitSize) { j ->
            val byteIndex = currentBit / 8
            val bitIndex = 7 - (currentBit % 8)
            val numBits = field.bitSize
            val bitValue = (raw shr (numBits - 1 - j)) and 1L
            if (bitValue == 1L) {
                result[byteIndex] = (result[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
            currentBit++
        }
    }

    fun toByteArray(): ByteArray = result
}
