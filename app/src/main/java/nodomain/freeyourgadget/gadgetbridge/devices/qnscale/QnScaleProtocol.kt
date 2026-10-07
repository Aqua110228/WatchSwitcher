/*  Copyright (C) 2026 brigon

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
package nodomain.freeyourgadget.gadgetbridge.devices.qnscale

/**
 * Frame layout for the "QN-Scale" vendor protocol used by a family of Chipsea CS20-based BLE
 * body-composition scales sold under many rebrands (this support was written and tested against
 * an Arboleaf CS20N). Every frame is `[opcode, length, protocolType, ...payload, checksum]`, where
 * `checksum` is the sum of every preceding byte in the frame, mod 256.
 *
 * The base opcode set here mirrors the "QNHandler" driver in openScale (GPL-3.0-licensed,
 * https://github.com/oliexdev/openScale) — the QN-Scale family is otherwise undocumented, and that
 * project is the closest thing to a public spec for it. The history-sync opcodes (0x21/0x22/0x23)
 * below deliberately do NOT match openScale's implementation: that version (two 0xA0 acknowledgement
 * frames, then a 6-byte 0x22 query) never produced a single stored-measurement response from a real
 * CS20N in testing. This file's history-sync frames were instead reverse-engineered from a genuine
 * Android Bluetooth HCI snoop capture of the manufacturer's own Arboleaf app performing a real
 * history sync, decoded and checksum-verified byte-by-byte against that capture.
 */
object QnScaleProtocol {
    /** Seconds between the Unix epoch and 2000-01-01 00:00:00 UTC, the epoch this protocol's timestamps use. */
    const val QN_EPOCH_OFFSET_SECONDS = 946_702_800L

    const val OPCODE_LIVE_WEIGHT = 0x10
    const val OPCODE_SCALE_INFO = 0x12
    const val OPCODE_CONFIG_ACK = 0x14
    const val OPCODE_HISTORY_TRIGGER = 0x21
    const val OPCODE_STORED_MEASUREMENT = 0x23

    fun checksum(buf: ByteArray, from: Int, toInclusive: Int): Byte {
        var sum = 0
        for (i in from..toInclusive) sum = (sum + (buf[i].toInt() and 0xFF)) and 0xFF
        return sum.toByte()
    }

    private fun u16be(a: Byte, b: Byte): Int = ((a.toInt() and 0xFF) shl 8) or (b.toInt() and 0xFF)
    private fun u16le(a: Byte, b: Byte): Int = (a.toInt() and 0xFF) or ((b.toInt() and 0xFF) shl 8)
    private fun u32le(a: Byte, b: Byte, c: Byte, d: Byte): Long =
        (a.toLong() and 0xFF) or ((b.toLong() and 0xFF) shl 8) or
            ((c.toLong() and 0xFF) shl 16) or ((d.toLong() and 0xFF) shl 24)

    sealed interface Frame {
        /** 0x12: the scale's own weight scale factor (100 -> divide raw weight by 100, else by 10). */
        data class ScaleInfo(val scaleFactor: Float, val protocolType: Byte) : Frame

        /** 0x10: a live (in-progress or final) weight reading. [stable] gates whether it's publishable. */
        data class LiveWeight(
            val stable: Boolean,
            val weightKg: Float,
            val resistance1: Int,
            val resistance2: Int,
        ) : Frame

        /** 0x23: one stored measurement, returned in response to a 0x22 history query. */
        data class StoredMeasurement(
            val weightKg: Float,
            val resistance1: Int,
            val deviceEpochSeconds: Long,
        ) : Frame

        /** 0x14: the scale acknowledged our 0x13 config write; reply with a 0x20 time sync. */
        data object ConfigAck : Frame

        /** 0x21: the scale wants a history sync; reply with a 0x22 stored-data query. */
        data object HistoryTrigger : Frame

        data class Unknown(val opcode: Int) : Frame
    }

    /** Parses one notification payload. [weightScaleFactor] should be the value from the most recent [Frame.ScaleInfo]. */
    fun parse(data: ByteArray, weightScaleFactor: Float): Frame {
        if (data.size < 3) return Frame.Unknown(-1)
        return when (data[0].toInt() and 0xFF) {
            OPCODE_LIVE_WEIGHT -> parseLiveWeight(data, weightScaleFactor)
            OPCODE_SCALE_INFO -> parseScaleInfo(data)
            OPCODE_CONFIG_ACK -> Frame.ConfigAck
            OPCODE_HISTORY_TRIGGER -> Frame.HistoryTrigger
            OPCODE_STORED_MEASUREMENT -> parseStoredMeasurement(data, weightScaleFactor)
            else -> Frame.Unknown(data[0].toInt() and 0xFF)
        }
    }

    private fun parseScaleInfo(data: ByteArray): Frame {
        if (data.size <= 10) return Frame.Unknown(OPCODE_SCALE_INFO)
        val protocolType = data[2]
        // Some units report "10" here while their live-weight bytes are actually scaled by 100;
        // parseLiveWeight's plausible-range fallback catches and corrects that case.
        val factor = if (data[10].toInt() == 1) 100.0f else 10.0f
        return Frame.ScaleInfo(factor, protocolType)
    }

    private fun parseLiveWeight(data: ByteArray, weightScaleFactor: Float): Frame {
        if (data.size < 10) return Frame.Unknown(OPCODE_LIVE_WEIGHT)
        val stable = data[5].toInt() == 1
        val raw = u16be(data[3], data[4])
        val r1 = u16be(data[6], data[7])
        val r2 = u16be(data[8], data[9])

        var weightKg = raw / weightScaleFactor
        if (weightKg <= 5f || weightKg >= 250f) weightKg /= 10.0f

        return Frame.LiveWeight(stable, weightKg, r1, r2)
    }

    /**
     * bytes[5-8] = device timestamp (QN epoch seconds, little-endian), bytes[9,10] = weight
     * (big-endian, same encoding as a live weight frame), byte[11] = stable flag, bytes[12,13] =
     * resistance-1 (little-endian). Verified byte-for-byte against a real capture: raw weight
     * 0x1fc7 matched a live 81.35 kg reading taken moments later, and the timestamp matched the
     * time-sync value sent earlier in the same session, off by a few seconds.
     */
    private fun parseStoredMeasurement(data: ByteArray, weightScaleFactor: Float): Frame {
        if (data.size < 15) return Frame.Unknown(OPCODE_STORED_MEASUREMENT)
        val deviceSeconds = u32le(data[5], data[6], data[7], data[8])
        val raw = u16be(data[9], data[10])
        val r1 = u16le(data[12], data[13])
        var weightKg = raw / weightScaleFactor
        if (weightKg <= 5f || weightKg >= 250f) weightKg /= 10.0f
        return Frame.StoredMeasurement(weightKg = weightKg, resistance1 = r1, deviceEpochSeconds = deviceSeconds)
    }

    /** 0x13 config frame: unit + user height/age/gender, gated by the scale's own protocolType. */
    fun buildConfigFrame(protocolType: Byte, unitByte: Byte, heightCm: Int, ageYears: Int, isMale: Boolean): ByteArray {
        val frame = byteArrayOf(
            0x13, 0x09, protocolType, unitByte, 0x10,
            heightCm.coerceIn(60, 220).toByte(),
            ageYears.coerceIn(6, 80).toByte(),
            if (isMale) 0x00 else 0x01,
            0x00,
        )
        frame[frame.lastIndex] = checksum(frame, 0, frame.lastIndex - 1)
        return frame
    }

    /** 0x20 time sync, sent in reply to a 0x14 config-ack. */
    fun buildTimeSyncFrame(protocolType: Byte, epochSeconds: Long): ByteArray {
        val t = epochSeconds.toInt()
        val frame = byteArrayOf(
            0x20, 0x08, protocolType,
            (t and 0xFF).toByte(),
            ((t ushr 8) and 0xFF).toByte(),
            ((t ushr 16) and 0xFF).toByte(),
            ((t ushr 24) and 0xFF).toByte(),
            0x00,
        )
        frame[frame.lastIndex] = checksum(frame, 0, frame.lastIndex - 1)
        return frame
    }

    /** Acknowledges a stable live-weight frame: fixed `[0x1F, 0x05, protocolType, 0x10, checksum]`. */
    fun buildStableAck(protocolType: Byte): ByteArray {
        val ack = byteArrayOf(0x1F, 0x05, protocolType, 0x10, 0x00)
        ack[ack.lastIndex] = checksum(ack, 0, ack.lastIndex - 1)
        return ack
    }

    /**
     * 0x22 stored-data query, sent in reply to a 0x21 history trigger:
     * `[0x22, 0x04, protocolType, checksum]` — 4 bytes, no payload, and no preceding acknowledgment
     * frames of any kind. (openScale's ported "ES-30M" version of this query — two 0xA0 acks plus a
     * 6-byte query — never got a response on real hardware; see the class doc comment.)
     */
    fun buildStoredDataQuery(protocolType: Byte): ByteArray {
        val frame = byteArrayOf(0x22, 0x04, protocolType, 0x00)
        frame[frame.lastIndex] = checksum(frame, 0, frame.lastIndex - 1)
        return frame
    }

    fun nowAsQnEpochSeconds(): Long = (System.currentTimeMillis() / 1000L) - QN_EPOCH_OFFSET_SECONDS

    fun qnEpochSecondsToMillis(qnEpochSeconds: Long): Long = (qnEpochSeconds + QN_EPOCH_OFFSET_SECONDS) * 1000L
}
