/*  Copyright (C) 2026 David Girón

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.nxwear

internal data class NxWearPacket(
    val raw: ByteArray,
    val kind: Kind,
    val deviceType: Int? = null,
    val pageCount: Int? = null,
    val sequence: Int? = null,
    val command: Int? = null,
    val dataType: Int? = null,
    val declaredLength: Int? = null,
    val continuationIndex: Int? = null,
    val pagesPresent: Int = 0,
    val payload: ByteArray = byteArrayOf(),
) {
    data class TimedReading(val timestampMillis: Long, val value: Int)

    enum class Kind {
        FRAMED,
        CONTINUATION,
        RAW,
    }

    val isComplete: Boolean
        get() = kind == Kind.FRAMED && payload.size == declaredLength && pagesPresent == pageCount?.plus(1)

    fun append(continuation: NxWearPacket): NxWearPacket? {
        val length = declaredLength ?: return null
        if (kind != Kind.FRAMED || isComplete || continuation.kind != Kind.CONTINUATION ||
            continuation.continuationIndex != pagesPresent ||
            pagesPresent + continuation.pagesPresent > requireNotNull(pageCount) + 1) return null
        return copy(
            payload = (payload + continuation.payload).copyOf(minOf(length, payload.size + continuation.payload.size)),
            pagesPresent = pagesPresent + continuation.pagesPresent,
        )
    }

    fun timedReadings(expectedDataType: Int): List<TimedReading>? {
        if (kind != Kind.FRAMED || !isComplete || command != 0x31 || dataType != expectedDataType ||
            payload.size < 8 || (payload.size - 3) % 5 != 0) return null
        return (3 until payload.size step 5).map { offset ->
            TimedReading(payload.le32(offset) * 1000L, payload[offset + 4].toInt() and 0xff)
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val FIRST_PAGE_HEADER_SIZE = 10
        private const val CONTINUATION_DATA_SIZE = PAGE_SIZE - 1

        fun expectedFragmentCount(declaredLength: Int): Int {
            require(declaredLength >= 0)
            val continuationBytes = (declaredLength - (PAGE_SIZE - FIRST_PAGE_HEADER_SIZE)).coerceAtLeast(0)
            return 1 + (continuationBytes + CONTINUATION_DATA_SIZE - 1) / CONTINUATION_DATA_SIZE
        }

        fun frame(sequence: Int, command: Int, dataType: Int, payload: ByteArray, deviceType: Int = 0x0b): ByteArray {
            require(sequence in 0..0xff)
            require(command in 0..0xff)
            require(dataType in 0..0xff)
            require(deviceType in 0..0xff)
            require(payload.size <= 0xffff)

            val pageCount = expectedFragmentCount(payload.size) - 1
            require(pageCount <= 0xff)
            val result = ByteArray(PAGE_SIZE * (pageCount + 1))
            result[1] = deviceType.toByte()
            result[2] = pageCount.toByte()
            result[3] = sequence.toByte()
            result[4] = command.toByte()
            result[5] = dataType.toByte()
            result[8] = payload.size.toByte()
            result[9] = (payload.size shr 8).toByte()

            var copied = minOf(payload.size, PAGE_SIZE - FIRST_PAGE_HEADER_SIZE)
            payload.copyInto(result, FIRST_PAGE_HEADER_SIZE, 0, copied)
            for (pageIndex in 1..pageCount) {
                val pageOffset = pageIndex * PAGE_SIZE
                result[pageOffset] = pageIndex.toByte()
                val next = minOf(payload.size, copied + CONTINUATION_DATA_SIZE)
                payload.copyInto(result, pageOffset + 1, copied, next)
                copied = next
            }
            return result
        }

        fun ack(sequence: Int, dataType: Int, commandId: Int = 0x04): ByteArray =
            frame(sequence, commandId, dataType, byteArrayOf(1))

        fun command(dataType: Int, enabled: Boolean): ByteArray {
            return frame(0, 0x01, dataType, byteArrayOf(if (enabled) 1 else 0))
        }

        fun command(dataType: Int, payload: Int): ByteArray {
            require(payload in 0..0xff) { "Command payload must fit in one byte: $payload" }
            return frame(0, 0x01, dataType, byteArrayOf(payload.toByte()))
        }

        fun command(dataType: Int, payload: ByteArray): ByteArray {
            return frame(0, 0x01, dataType, payload)
        }

        fun parse(bytes: ByteArray): NxWearPacket {
            val raw = bytes.copyOf()
            if (bytes.isNotEmpty() && bytes.size % PAGE_SIZE == 0 && bytes[0] != 0.toByte()) {
                val firstIndex = bytes[0].toInt() and 0xff
                val pagesPresent = bytes.size / PAGE_SIZE
                if (firstIndex + pagesPresent - 1 > 0xff) return NxWearPacket(raw, Kind.RAW)
                val payload = ByteArray(pagesPresent * CONTINUATION_DATA_SIZE)
                for (pageOffset in 0 until pagesPresent) {
                    if ((bytes[pageOffset * PAGE_SIZE].toInt() and 0xff) != firstIndex + pageOffset) {
                        return NxWearPacket(raw, Kind.RAW)
                    }
                    bytes.copyInto(
                        payload,
                        pageOffset * CONTINUATION_DATA_SIZE,
                        pageOffset * PAGE_SIZE + 1,
                        (pageOffset + 1) * PAGE_SIZE,
                    )
                }
                return NxWearPacket(
                    raw = raw,
                    kind = Kind.CONTINUATION,
                    continuationIndex = firstIndex,
                    pagesPresent = pagesPresent,
                    payload = payload,
                )
            }
            if (bytes.size < FIRST_PAGE_HEADER_SIZE || bytes[0] != 0.toByte() || bytes.size % PAGE_SIZE != 0) {
                return NxWearPacket(raw, Kind.RAW)
            }

            val pageCount = bytes[2].toInt() and 0xff
            val pagesPresent = bytes.size / PAGE_SIZE
            if (pagesPresent > pageCount + 1) return NxWearPacket(raw, Kind.RAW)
            for (pageIndex in 1 until pagesPresent) {
                if ((bytes[pageIndex * PAGE_SIZE].toInt() and 0xff) != pageIndex) {
                    return NxWearPacket(raw, Kind.RAW)
                }
            }

            val declaredLength = bytes.le16(8)
            val capacity = (PAGE_SIZE - FIRST_PAGE_HEADER_SIZE) + pageCount * CONTINUATION_DATA_SIZE
            if (declaredLength > capacity) return NxWearPacket(raw, Kind.RAW)
            val payload = ByteArray(minOf(declaredLength, PAGE_SIZE - FIRST_PAGE_HEADER_SIZE +
                (pagesPresent - 1) * CONTINUATION_DATA_SIZE))
            var copied = minOf(payload.size, PAGE_SIZE - FIRST_PAGE_HEADER_SIZE)
            bytes.copyInto(payload, 0, FIRST_PAGE_HEADER_SIZE, FIRST_PAGE_HEADER_SIZE + copied)
            for (pageIndex in 1 until pagesPresent) {
                val next = minOf(payload.size, copied + CONTINUATION_DATA_SIZE)
                bytes.copyInto(payload, copied, pageIndex * PAGE_SIZE + 1, pageIndex * PAGE_SIZE + 1 + next - copied)
                copied = next
            }

            return NxWearPacket(
                raw = raw,
                kind = Kind.FRAMED,
                deviceType = bytes[1].toInt() and 0xff,
                pageCount = pageCount,
                sequence = bytes[3].toInt() and 0xff,
                command = bytes[4].toInt() and 0xff,
                dataType = bytes[5].toInt() and 0xff,
                declaredLength = declaredLength,
                pagesPresent = pagesPresent,
                payload = payload,
            )
        }
    }
}

internal fun ByteArray.le16(offset: Int): Int {
    require(offset >= 0 && offset <= size - 2) { "le16 offset $offset is outside byte array of size $size" }
    return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}

internal fun ByteArray.le32(offset: Int): Long {
    require(offset >= 0 && offset <= size - 4) { "le32 offset $offset is outside byte array of size $size" }
    return (this[offset].toLong() and 0xff) or
        ((this[offset + 1].toLong() and 0xff) shl 8) or
        ((this[offset + 2].toLong() and 0xff) shl 16) or
        ((this[offset + 3].toLong() and 0xff) shl 24)
}

internal fun ByteArray.be32(offset: Int): Long {
    require(offset >= 0 && offset <= size - 4) { "be32 offset $offset is outside byte array of size $size" }
    return ((this[offset].toLong() and 0xff) shl 24) or
        ((this[offset + 1].toLong() and 0xff) shl 16) or
        ((this[offset + 2].toLong() and 0xff) shl 8) or
        (this[offset + 3].toLong() and 0xff)
}

internal object NxWearDataType {
    const val BATTERY = 0x03
    const val REALTIME_SPORT = 0x04
    const val HISTORY_SPORT = 0x05
    const val SLEEP = 0x06
    const val HEART_RATE = 0x07
    const val HISTORY_HEART_RATE = 0x08
    const val DEVICE_SYNC = 0x09
    const val EXERCISE_HEART_RATE = 0x11
    const val SPO2 = 0x14
    const val HEART_RATE_CONTROL = 0x15
    const val MANUAL_HEART_RATE = 0x18
    const val HISTORY_SPO2 = 0x28
    const val HISTORY_HRV = 0x2a
    const val STRESS = 0x2d
    const val HISTORY_TEMPERATURE = 0x2f
    const val CLOCK = 0x68
    const val MESSAGE_NOTICE = 0x6b
    const val FACTORY_RESET = 0x6d
    const val BOOTSTRAP = 0x6e
    const val SHAKE_CAMERA = 0x74
    const val CALL_CONTROL = 0x75
    const val POWER_OFF = 0x77
    const val WRIST_WAKE = 0x7f
    const val DETECTION_SETTINGS = 0x80
    const val DEVICE_LOG = 0xfa
}
