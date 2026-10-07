package nodomain.freeyourgadget.gadgetbridge.service.devices.viatom

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class BlePacketManager {
    private val LOG: Logger = LoggerFactory.getLogger(BlePacketManager::class.java)

    private val reassembler = mutableMapOf<Int, AccumulatedPackets>()

    private var outgoingSequenceNumber: Int = 0

    // ==================== ENCODING (Splitting) ====================

    fun splitDataInPackets(
        data: ByteArray,
        scaleConfig: F8Support.ScaleConfig
    ):  List<ByteArray> {
        val currentSequence = outgoingSequenceNumber
        outgoingSequenceNumber = (outgoingSequenceNumber + 1) and 0xFF
        return splitDataInPackets(data, scaleConfig, currentSequence)
    }

    fun splitDataInPackets(
        data: ByteArray,
        scaleConfig: F8Support.ScaleConfig,
        sequenceNumber: Int
    ): List<ByteArray> {
        val totalLength = data.size
        val numPackets = if (totalLength <= PACKET_SIZE) 1
        else 1 + ((totalLength - 1) / PACKET_SIZE)

        val paddedPayload = if (totalLength < numPackets * PACKET_SIZE) {
            data.copyOf(numPackets * PACKET_SIZE)
        } else {
            data
        }

        return List(numPackets) { packetIdx ->
            val offset = packetIdx * PACKET_SIZE
            val chunk = paddedPayload.sliceArray(offset until offset + PACKET_SIZE)

            val checksum = calculateChecksum(chunk, scaleConfig.weightUnit)

            ByteBuffer.allocate(FULL_PACKET_SIZE).apply {
                put(sequenceNumber.toByte())
                put(totalLength.toByte())
                put(packetIdx.toByte())
                put(chunk)
                put(checksum.toByte())
            }.array()
        }
    }

    // ==================== DECODING (Reassembly) ====================

    fun assemblePacketToData(rawPacket: ByteArray): ReassemblyResult {
        val packet = parsePacket(rawPacket)

        if (packet.isComplete()) {
            return ReassemblyResult.Complete(
                sequenceNumber = packet.sequenceNumber,
                data = packet.dataChunk.copyOfRange(0, packet.totalLength)
            )
        }

        val accumulated = reassembler.getOrPut(packet.sequenceNumber) {
            AccumulatedPackets(packet.totalLength)
        }

        accumulated.packets[packet.packetIndex] = packet

        return if (accumulated.isComplete()) {
            val data = accumulated.toByteArray()
            reassembler.remove(packet.sequenceNumber)
            ReassemblyResult.Complete(packet.sequenceNumber, data)
        } else {
            ReassemblyResult.Incomplete(
                sequenceNumber = packet.sequenceNumber,
                received = accumulated.packets.size,
                expected = (packet.totalLength + PACKET_SIZE - 1) / PACKET_SIZE
            )
        }
    }

    fun reset(sequenceNumber: Int) {
        reassembler.remove(sequenceNumber)
    }

    fun resetAll() {
        reassembler.clear()
        outgoingSequenceNumber = 0;
    }


    private fun parsePacket(packet: ByteArray): ParsedPacket {
        require(packet.size == FULL_PACKET_SIZE) {
             LOG.error("Chunk must be exactly 20 bytes long, received: {}", packet.size)}

        return ParsedPacket(
            sequenceNumber = packet[0].toInt() and 0xFF,
            totalLength = packet[1].toInt() and 0xFF,
            packetIndex = packet[2].toInt() and 0xFF,
            dataChunk = packet.copyOfRange(3, 19),
            checksum = packet[19].toInt() and 0xFF
        ).also {
            require(it.isChecksumValid()) {
                LOG.error("Invalid Checksum received: 0x{}", it.checksum.toHexString())
            }
        }
    }

    private fun calculateChecksum(chunk: ByteArray, weightUnit: F8Support.WeightUnit): Int {
        val dataSum = chunk.sumOf { (it.toInt() and 0xFF) } and 0x1F
        return dataSum or ((weightUnit.ordinal shl 5) and 0xE0)
    }

    private data class AccumulatedPackets(
        val totalLength: Int,
        val packets: MutableMap<Int, ParsedPacket> = mutableMapOf()
    ) {
        fun isComplete(): Boolean =
            packets.size * PACKET_SIZE >= totalLength

        fun toByteArray(): ByteArray {
            val result = ByteArray(totalLength)
            var offset = 0

            packets.toSortedMap().values.forEach { packet ->
                val bytesToCopy = minOf(PACKET_SIZE, totalLength - offset)
                packet.dataChunk.copyInto(result, offset, 0, bytesToCopy)
                offset += bytesToCopy
            }

            return result
        }
    }

    companion object {
        const val PACKET_SIZE = 16
        const val FULL_PACKET_SIZE = 20
    }
}

data class ParsedPacket(
    val sequenceNumber: Int,
    val totalLength: Int,
    val packetIndex: Int,
    val dataChunk: ByteArray,
    val checksum: Int
) {
    fun isComplete(): Boolean =
        (packetIndex == 0) && (totalLength <= BlePacketManager.PACKET_SIZE)

    fun isChecksumValid(): Boolean {
        return (checksum and 0x1F) == (dataChunk.sumOf { (it.toInt() and 0xFF) } and 0x1F)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedPacket) return false
        return sequenceNumber == other.sequenceNumber &&
                totalLength == other.totalLength &&
                packetIndex == other.packetIndex &&
                dataChunk.contentEquals(other.dataChunk) &&
                checksum == other.checksum
    }

    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + totalLength
        result = 31 * result + packetIndex
        result = 31 * result + dataChunk.contentHashCode()
        result = 31 * result + checksum
        return result
    }
}

sealed class ReassemblyResult {
    data class Complete(
        val sequenceNumber: Int,
        val data: ByteArray
    ) : ReassemblyResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Complete) return false
            return sequenceNumber == other.sequenceNumber &&
                    data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = sequenceNumber
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class Incomplete(
        val sequenceNumber: Int,
        val received: Int,
        val expected: Int
    ) : ReassemblyResult()
}