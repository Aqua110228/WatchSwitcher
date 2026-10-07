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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NxWearProtocolTest {
    @Test
    fun packetCommandCreatesSequenceZeroCommandWithBothPayloadForms() {
        assertArrayEquals(
            NxWearPacket.frame(0, 0x01, NxWearDataType.SHAKE_CAMERA, byteArrayOf(1)),
            NxWearPacket.command(NxWearDataType.SHAKE_CAMERA, true),
        )
        assertArrayEquals(
            NxWearPacket.frame(0, 0x01, NxWearDataType.POWER_OFF, byteArrayOf(0)),
            NxWearPacket.command(NxWearDataType.POWER_OFF, 0),
        )
        assertArrayEquals(
            NxWearPacket.frame(0, 0x01, NxWearDataType.SPO2, byteArrayOf(1, 0, 0, 0, 0)),
            NxWearPacket.command(NxWearDataType.SPO2, byteArrayOf(1, 0, 0, 0, 0)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun packetCommandRejectsIntegerPayloadOutsideOneByte() {
        NxWearPacket.command(NxWearDataType.POWER_OFF, 0x100)
    }

    @Test
    fun packetAckSupportsDataAndFixedReplyCommandIds() {
        assertArrayEquals(
            NxWearPacket.frame(3, 0x04, NxWearDataType.REALTIME_SPORT, byteArrayOf(1)),
            NxWearPacket.ack(3, NxWearDataType.REALTIME_SPORT),
        )
        assertArrayEquals(
            NxWearPacket.frame(0, 0x34, NxWearDataType.BOOTSTRAP, byteArrayOf(1)),
            NxWearPacket.ack(0, NxWearDataType.BOOTSTRAP, commandId = 0x34),
        )
    }

    @Test
    fun measurementDispatchPreservesEachCommandPayload() {
        for (enabled in listOf(false, true)) {
            assertArrayEquals(NxWearProtocol.encodeManualHeartRate(enabled),
                NxWearProtocol.encodeMeasurement(NxWearDataType.MANUAL_HEART_RATE, enabled))
            assertArrayEquals(NxWearProtocol.encodeStressMeasurement(enabled),
                NxWearProtocol.encodeMeasurement(NxWearDataType.STRESS, enabled))
            assertArrayEquals(NxWearProtocol.encodeSpo2Measurement(enabled),
                NxWearProtocol.encodeMeasurement(NxWearDataType.SPO2, enabled))
            val spo2 = NxWearPacket.parse(NxWearProtocol.encodeMeasurement(NxWearDataType.SPO2, enabled))
            assertEquals(5, spo2.payload.size)
            assertArrayEquals(byteArrayOf(if (enabled) 1 else 0, 0, 0, 0, 0), spo2.payload)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsUnsupportedMeasurementType() {
        NxWearProtocol.encodeMeasurement(NxWearDataType.HEART_RATE, true)
    }

    @Test
    fun batteryPredicateRequiresCompleteNotificationAndValidLevelButAllowsUnknownStatus() {
        val packet = notification(NxWearDataType.BATTERY, byteArrayOf(100, 0x7f))
        assertTrue(NxWearProtocol.isBatteryNotification(packet))
        assertFalse(NxWearProtocol.isBatteryNotification(packet.copy(command = 0x34)))
        assertFalse(NxWearProtocol.isBatteryNotification(packet.copy(payload = byteArrayOf(101, 0))))
        assertFalse(NxWearProtocol.isBatteryNotification(packet.copy(payload = byteArrayOf(100))))
        assertFalse(NxWearProtocol.isBatteryNotification(packet.copy(pageCount = 1)))
    }

    @Test
    fun candidateSettingsReplyRemainsAnAckNotASettingsReadback() {
        val packet = NxWearPacket.parse(NxWearPacket.ack(3, NxWearDataType.REALTIME_SPORT))
        assertTrue(NxWearProtocol.isCandidateSettingsReply(packet))
        assertFalse(NxWearProtocol.isCandidateSettingsReply(packet.copy(command = 0x31)))
        assertFalse(NxWearProtocol.isCandidateSettingsReply(packet.copy(payload = byteArrayOf(0))))
        assertFalse(NxWearProtocol.isCandidateSettingsReply(packet.copy(pageCount = 1)))
    }

    @Test
    fun fixedAcksRemainDistinctFromDataNotifications() {
        for ((raw, subtype) in listOf(
                NxWearProtocol.SETTINGS_ACK to NxWearDataType.DETECTION_SETTINGS,
                NxWearProtocol.INITIALIZATION_ACK to NxWearDataType.BOOTSTRAP,
                NxWearProtocol.POWER_OFF_ACK to NxWearDataType.POWER_OFF)) {
            val packet = NxWearPacket.parse(raw)
            assertEquals(0x34, packet.command)
            assertEquals(subtype, packet.dataType)
            assertEquals(0, packet.sequence)
            assertArrayEquals(byteArrayOf(1), packet.payload)
        }
    }

    private fun notification(subtype: Int, payload: ByteArray): NxWearPacket =
        NxWearPacket.parse(NxWearPacket.frame(3, 0x31, subtype, payload))
}
