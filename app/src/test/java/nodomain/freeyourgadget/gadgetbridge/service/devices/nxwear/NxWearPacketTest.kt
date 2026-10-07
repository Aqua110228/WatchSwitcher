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
import org.junit.Assert.assertNull
import org.junit.Test
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind

class NxWearPacketTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun shakeCameraControlMatchesCaptureAndRecognizesWatchEvents() {
        assertArrayEquals(bytes("000b000001740000010001000000000000000000"), NxWearProtocol.encodeShakeCamera(true))
        assertArrayEquals(bytes("000b000001740000010000000000000000000000"), NxWearProtocol.encodeShakeCamera(false))

        val event = NxWearPacket.parse(bytes("000b000031740000010001004ac9b7028020c26a"))
        assertEquals(true, NxWearProtocol.isShakeCameraEvent(event))
        assertEquals(false, NxWearProtocol.isShakeCameraEvent(event.copy(command = 0x34)))
        assertArrayEquals(
            NxWearPacket.frame(0, 0x04, NxWearDataType.SHAKE_CAMERA, byteArrayOf(1)),
            NxWearPacket.ack(requireNotNull(event.sequence), NxWearDataType.SHAKE_CAMERA),
        )
    }

    @Test
    fun reassemblesExerciseHeartRateHistoryBeforeAcknowledging() {
        val frames = listOf(
            "000b0303311100003f000c000c03b7be6a5904b7",
            "01be6a5904b7be6a5904b7be6a5805b7be6a5805",
            "02b7be6a5805b7be6a5806b7be6a5806b7be6a58",
            "0306b7be6a5807b7be6a5707b7be6a577f000001",
        ).map { NxWearPacket.parse(bytes(it)) }
        var packet = frames.first()
        assertNull(NxWearHeartRateReading.parse(packet))
        for (continuation in frames.drop(1)) packet = requireNotNull(packet.append(continuation))

        val readings = NxWearHeartRateReading.parse(packet)
        assertEquals(true, packet.isComplete)
        assertEquals(12, readings?.size)
        assertEquals(NxWearHeartRateReading(0x6abeb703L * 1000L, 89), readings?.first())
        assertEquals(NxWearHeartRateReading(0x6abeb707L * 1000L, 87), readings?.last())
        assertEquals(true, NxWearProtocol.isHistoryDataType(requireNotNull(packet.dataType)))
        assertArrayEquals(bytes("000b000304110000010001000000000000000000"),
            NxWearPacket.ack(requireNotNull(packet.sequence), requireNotNull(packet.dataType)))
    }

    @Test
    fun parsesHistoricalSportRecordsFromFragmentedFrame() {
        val frames = listOf(
            "000b0205310500002b00020002b7d4be6a300900",
            "010047060000f22801001f010000000000000000",
            "02000000000000000000000000000068b9be6a4e",
        ).map { NxWearPacket.parse(bytes(it)) }
        var packet = frames.first()
        for (continuation in frames.drop(1)) packet = requireNotNull(packet.append(continuation))

        val readings = NxWearSportReading.parse(packet)
        assertEquals(listOf(NxWearSportReading(0x6abed4b7L * 1000L, 2352, 1607, 76018, 287)), readings)
        assertArrayEquals(bytes("000b000504050000010001000000000000000000"),
            NxWearPacket.ack(requireNotNull(packet.sequence), requireNotNull(packet.dataType)))
    }

    @Test
    fun recognizesTemperatureHistoryAndWaitsForEveryDiagnosticLogFrame() {
        assertEquals(true, NxWearProtocol.isHistoryDataType(NxWearDataType.HISTORY_TEMPERATURE))
        assertEquals(60_000L, NxWearProtocol.historyTimeoutForSubtype(NxWearDataType.HISTORY_TEMPERATURE))
        assertEquals(5_000L, NxWearProtocol.historyTimeoutForSubtype(NxWearDataType.DEVICE_LOG))
        for (sequence in 6..0x49) {
            val ack = NxWearPacket.parse(NxWearPacket.ack(sequence, NxWearDataType.DEVICE_LOG))
            assertEquals(sequence, ack.sequence)
            assertEquals(0x04, ack.command)
            assertEquals(NxWearDataType.DEVICE_LOG, ack.dataType)
        }
    }

    @Test
    fun rejectsOutOfOrderAndExcessHistoryContinuations() {
        val first = NxWearPacket.parse(bytes("000b010231040000170001000100000000000000"))
        val next = NxWearPacket.parse(bytes("0100000000000000000000000000020b00800100"))
        val wrong = next.copy(continuationIndex = 2)
        assertNull(first.append(wrong))
        assertNull(first.append(next.copy(pagesPresent = 2)))
        val complete = requireNotNull(first.append(next))
        assertEquals(true, complete.isComplete)
        assertNull(complete.append(next))
    }

    @Test
    fun parsesWalkingRecordFromCompleteSportFrame() {
        val payload = bytes("010001e57fbe6a400000002d0000000a0a00002f000000")
        val packet = NxWearPacket.parse(NxWearPacket.frame(0x1d, 0x31, NxWearDataType.REALTIME_SPORT, payload))

        val readings = NxWearSportReading.parse(packet)
        assertEquals(1, readings?.size)
        assertEquals(64, readings?.single()?.steps)
        assertEquals(45, readings?.single()?.distance) // meters
        assertEquals(2570, readings?.single()?.calories) // milli-kcal
        assertEquals(47, readings?.single()?.duration) // seconds
    }

    @Test
    fun mapsObservedBatteryStatusValues() {
        assertEquals(BatteryState.BATTERY_NORMAL, NxWearProtocol.batteryStateForStatus(0x00))
        assertEquals(BatteryState.BATTERY_CHARGING, NxWearProtocol.batteryStateForStatus(0x01))
        assertEquals(BatteryState.BATTERY_CHARGING_FULL, NxWearProtocol.batteryStateForStatus(0x02))
        assertEquals(BatteryState.UNKNOWN, NxWearProtocol.batteryStateForStatus(0xff))
    }

    @Test
    fun recognizesOptionalHistoryDataBeforeBatteryChanges() {
        val historyFrame = NxWearPacket.parse(bytes("000b01033105000017000100012189be6aab0700"))
        val continuation = NxWearPacket.parse(bytes("01001305000038d300005a040000000b00800100"))
        val historyAck = bytes("000b000304050000010001000000000000000000")
        val unplugged = NxWearPacket.parse(bytes("000b00063103000002004a00000001000903ff00"))
        val charging = NxWearPacket.parse(bytes("000b00073103000002004a01000001000903ff00"))

        assertEquals(NxWearDataType.HISTORY_SPORT, historyFrame.dataType)
        assertEquals(2, NxWearPacket.expectedFragmentCount(requireNotNull(historyFrame.declaredLength)))
        assertEquals(1, continuation.continuationIndex)
        assertEquals(true, NxWearProtocol.isHistoryDataType(requireNotNull(historyFrame.dataType)))
        assertEquals(true, NxWearProtocol.isHistoryDataType(NxWearDataType.DEVICE_LOG))
        assertArrayEquals(historyAck, NxWearPacket.ack(3, NxWearDataType.HISTORY_SPORT))
        assertEquals(listOf(0x4a, 0x00), unplugged.payload.map { it.toInt() and 0xff })
        assertEquals(listOf(0x4a, 0x01), charging.payload.map { it.toInt() and 0xff })
    }

    @Test
    fun parsesFirstPageHeaderAndTrimsPadding() {
        val raw = bytes("000b00113103000002000e01000001000903ff00")
        val packet = NxWearPacket.parse(raw)

        assertEquals(NxWearPacket.Kind.FRAMED, packet.kind)
        assertEquals(0x0b, packet.deviceType)
        assertEquals(0, packet.pageCount)
        assertEquals(0x11, packet.sequence)
        assertEquals(0x31, packet.command)
        assertEquals(0x03, packet.dataType)
        assertEquals(2, packet.declaredLength)
        assertEquals(1, packet.pagesPresent)
        assertEquals(true, packet.isComplete)
        assertArrayEquals(bytes("0e01"), packet.payload)
        assertArrayEquals(raw, packet.raw)
    }

    @Test
    fun identifiesContinuationIndexAndData() {
        val raw = bytes("01050003418e005d574205000303010b00800100")
        val packet = NxWearPacket.parse(raw)

        assertEquals(NxWearPacket.Kind.CONTINUATION, packet.kind)
        assertEquals(1, packet.continuationIndex)
        assertNull(packet.sequence)
        assertNull(packet.command)
        assertNull(packet.dataType)
        assertArrayEquals(raw.copyOfRange(1, raw.size), packet.payload)
        assertArrayEquals(raw, packet.raw)
    }

    @Test
    fun recognizesLaterActivityContinuationIndexes() {
        for (index in 0x05..0x06) {
            val raw = ByteArray(20).apply { this[0] = index.toByte() }
            val packet = NxWearPacket.parse(raw)

            assertEquals(NxWearPacket.Kind.CONTINUATION, packet.kind)
            assertEquals(index, packet.continuationIndex)
            assertArrayEquals(raw, packet.raw)
        }
    }

    @Test
    fun parsesCoalescedContinuationPages() {
        val full = NxWearPacket.frame(7, 0x31, 0x09, ByteArray(70) { it.toByte() })
        val raw = full.copyOfRange(20, full.size)
        val packet = NxWearPacket.parse(raw)

        assertEquals(NxWearPacket.Kind.CONTINUATION, packet.kind)
        assertEquals(1, packet.continuationIndex)
        assertEquals(4, packet.pagesPresent)
        assertEquals(76, packet.payload.size)
        assertArrayEquals(full.copyOfRange(21, 40), packet.payload.copyOfRange(0, 19))

        raw[20] = 0x07.toByte()
        assertEquals(NxWearPacket.Kind.RAW, NxWearPacket.parse(raw).kind)
    }

    @Test
    fun preservesMalformedAndUnrecognizedPackets() {
        val malformed = bytes("000b0011")
        val packet = NxWearPacket.parse(malformed)
        assertEquals(NxWearPacket.Kind.RAW, packet.kind)
        assertArrayEquals(malformed, packet.raw)

        val unknown = bytes("ff010203040506")
        assertArrayEquals(unknown, NxWearPacket.parse(unknown).raw)
    }

    @Test
    fun readsUnsignedLittleEndianLength() {
        assertEquals(0x1234, bytes("3412").le16(0))
        assertEquals(0xff80, bytes("0080ff").le16(1))
    }

    @Test
    fun readsUnsigned32BitPayloadValues() {
        val payload = bytes("0080fffe")

        assertEquals(0xfeff8000L, payload.le32(0))
        assertEquals(0x0080fffeL, payload.be32(0))
        assertEquals(0x6abe70f9L, bytes("01f970be6a02").le32(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncated32BitPayloadValue() {
        bytes("010203").le32(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedBigEndianPayloadValue() {
        bytes("01020304").be32(1)
    }

    @Test
    fun buildsObservedHealthDetectionPackets() {
        assertArrayEquals(bytes("000b000001800000080000003c00000000000000"),
            NxWearProtocol.encodeDetectionSettings(false, 60, false))
        assertArrayEquals(bytes("000b000001800000080001003c00000000000000"),
            NxWearProtocol.encodeDetectionSettings(true, 60, false))
        assertArrayEquals(bytes("000b000001800000080001003c00010000000000"),
            NxWearProtocol.encodeDetectionSettings(true, 60, true))
        assertArrayEquals(bytes("000b000001800000080000003c00010000000000"),
            NxWearProtocol.encodeDetectionSettings(false, 60, true))
        assertArrayEquals(bytes("000b000001800000080001001e00010000000000"),
            NxWearProtocol.encodeDetectionSettings(true, 30, true))
    }

    @Test
    fun buildsObservedClockPacketsWithLittleEndianTimeFields() {
        val epochMillis = 0x6abe37ecL * 1000L
        assertArrayEquals(bytes("000b0000016800000900ec37be6a201c00000000"),
            NxWearProtocol.encodeClockSettings(epochMillis, 7200, false))
        assertArrayEquals(bytes("000b0000016800000900ec37be6a201c00000100"),
            NxWearProtocol.encodeClockSettings(epochMillis, 7200, true))
    }

    @Test
    fun buildsObservedWristWakePackets() {
        assertArrayEquals(bytes("000b0000017f00000600000001173b0500000000"),
            NxWearProtocol.encodeWristWakeSettings(false, "00:01", "23:59", 5))
        assertArrayEquals(bytes("000b0000017f00000600010c2215370800000000"),
            NxWearProtocol.encodeWristWakeSettings(true, "12:34", "21:55", 8))
    }

    @Test
    fun buildsObservedAcknowledgementPackets() {
        assertArrayEquals(bytes("000b000034800000010001000000000000000000"), NxWearPacket.frame(0, 0x34, 0x80, bytes("01")))
        assertArrayEquals(bytes("000b001104030000010001000000000000000000"), NxWearPacket.ack(0x0011, NxWearDataType.BATTERY))
    }

    @Test
    fun buildsIncomingCallAndEndFrames() {
        val incoming = bytes("000b0100016b00001d005b7cbe6a01020107636f016e74616374020c2b3334363030313233343536")
        val ended = bytes("000b000001750000010002000000000000000000")

        assertArrayEquals(incoming, NxWearProtocol.encodeIncomingCall(0x6abe7c5bL * 1000L, "contact", "+34600123456"))
        assertArrayEquals(ended, NxWearProtocol.encodeCallEnded())
        assertEquals(NxWearDataType.MESSAGE_NOTICE, NxWearPacket.parse(incoming).dataType)
        assertEquals(29, NxWearPacket.parse(incoming).payload.size)
    }

    @Test
    fun boundsIncomingCallFieldsWithoutSplittingUtf8() {
        val frame = NxWearProtocol.encodeIncomingCall(0x6abe7c5bL * 1000L, "é".repeat(100), "1".repeat(300))
        val packet = NxWearPacket.parse(frame)

        assertEquals(true, packet.isComplete)
        assertEquals(230, packet.payload.size)
        assertEquals(52, packet.payload[7].toInt() and 0xff)
        assertEquals(168, packet.payload[61].toInt() and 0xff)
    }

    @Test
    fun includesCallerNameAndFallsBackToNumber() {
        val named = NxWearPacket.parse(NxWearProtocol.encodeIncomingCall(0x6abe7c5bL * 1000L, "Alice", "123"))
        val unnamed = NxWearPacket.parse(NxWearProtocol.encodeIncomingCall(0x6abe7c5bL * 1000L, null, "123"))

        assertArrayEquals(bytes("0105416c6963650203313233"), named.payload.copyOfRange(6, named.payload.size))
        assertArrayEquals(bytes("0107636f6e746163740203313233"), unnamed.payload.copyOfRange(6, unnamed.payload.size))
    }

    @Test
    fun buildsObservedPowerOffPacket() {
        val frame = bytes("000b000001770000010000000000000000000000")
        val packet = NxWearPacket.parse(NxWearProtocol.encodePowerOff())

        assertArrayEquals(frame, packet.raw)
        assertEquals(NxWearPacket.Kind.FRAMED, packet.kind)
        assertEquals(0x01, packet.command)
        assertEquals(0x77, packet.dataType)
        assertEquals(1, packet.declaredLength)
        assertArrayEquals(bytes("00"), packet.payload)
    }

    @Test
    fun buildsManualHeartRateCommandsAndAcknowledgement() {
        val start = bytes("000b000001180000010001000000000000000000")
        val stop = bytes("000b000001180000010000000000000000000000")
        val acknowledgement = bytes("000b000904070000010001000000000000000000")

        assertArrayEquals(start, NxWearProtocol.encodeManualHeartRate(true))
        assertArrayEquals(stop, NxWearProtocol.encodeManualHeartRate(false))
        assertArrayEquals(acknowledgement, NxWearPacket.ack(9, NxWearDataType.HEART_RATE))
    }

    @Test
    fun acknowledgesObservedManualHeartRateStreamWithMatchingSequences() {
        // Consecutive notifications and phone acknowledgements from nx-wear-heart2.txt.
        val frames = listOf(
            "000b00053107000008000100011279c16a470000" to "000b000504070000010001000000000000000000",
            "000b00063107000008000100011379c16a470000" to "000b000604070000010001000000000000000000",
            "000b00073107000008000100011379c16a470000" to "000b000704070000010001000000000000000000",
        )
        for ((frame, acknowledgement) in frames) {
            val packet = NxWearPacket.parse(bytes(frame))
            val reading = requireNotNull(NxWearHeartRateReading.parse(packet)).single()

            assertEquals(71, reading.bpm)
            assertArrayEquals(bytes(acknowledgement), NxWearPacket.ack(requireNotNull(packet.sequence), NxWearDataType.HEART_RATE))
        }
    }

    @Test
    fun doesNotTreatManualHeartRateCommandAcknowledgementAsReading() {
        val frame = bytes("000b000034180000010001000000000000000000")
        val packet = NxWearPacket.parse(frame)

        assertEquals(0x34, packet.command)
        assertEquals(NxWearDataType.MANUAL_HEART_RATE, packet.dataType)
        assertNull(NxWearHeartRateReading.parse(packet))
    }

    @Test
    fun parsesManualHeartRateReading() {
        val frame = bytes("000b0009310700000800010001de6ebe6a470000")
        val packet = NxWearPacket.parse(frame)
        val readings = NxWearHeartRateReading.parse(packet)

        assertEquals(9, packet.sequence)
        assertEquals(1, readings?.size)
        assertEquals(1790865118000L, readings?.single()?.timestampMillis)
        assertEquals(71, readings?.single()?.bpm)
    }

    @Test
    fun parsesCapturedHistoricalHeartRateReading() {
        val frame = bytes("000b00033107000008000100013f90be6a4c0700")
        val packet = NxWearPacket.parse(frame)
        val reading = NxWearHeartRateReading.parse(packet)?.single()

        assertEquals(3, packet.sequence)
        assertEquals(0x6abe903fL * 1000L, reading?.timestampMillis)
        assertEquals(76, reading?.bpm)
    }

    @Test
    fun rejectsTruncatedManualHeartRateReading() {
        val frame = NxWearPacket.frame(9, 0x31, 0x07, bytes("010001de6ebe6a"))
        val packet = NxWearPacket.parse(frame)

        assertNull(NxWearHeartRateReading.parse(packet))
    }

    @Test
    fun buildsStressMeasurementAndAcknowledgements() {
        val start = bytes("000b0000012d0000010001000000000000000000")
        val controlAck = bytes("000b005404150000010001000000000000000000")
        val readingAck = bytes("000b0055042d0000010001000000000000000000")
        val syncAck = bytes("000b005604fa0000010001000000000000000000")

        assertArrayEquals(start, NxWearProtocol.encodeStressMeasurement(true))
        assertArrayEquals(controlAck, NxWearPacket.ack(0x54, NxWearDataType.HEART_RATE_CONTROL))
        assertArrayEquals(readingAck, NxWearPacket.ack(0x55, NxWearDataType.STRESS))
        assertArrayEquals(syncAck, NxWearPacket.ack(0x56, NxWearDataType.DEVICE_LOG))
    }

    @Test
    fun buildsBloodOxygenMeasurementAndAcknowledgements() {
        val start = bytes("000b000001140000050001000000000000000000")
        val controlAck = bytes("000b000904150000010001000000000000000000")
        val readingAck = bytes("000b000a04140000010001000000000000000000")

        assertArrayEquals(start, NxWearProtocol.encodeSpo2Measurement(true))
        assertArrayEquals(controlAck, NxWearPacket.ack(9, NxWearDataType.HEART_RATE_CONTROL))
        assertArrayEquals(readingAck, NxWearPacket.ack(10, NxWearDataType.SPO2))
    }

    @Test
    fun buildsOutdoorRunningPhoneExerciseStartAndStop() {
        val start = NxWearPacket.parse(bytes("000b0000018200000a0005010000000000000000"))
        val stop = NxWearPacket.parse(bytes("000b0000018200000a0005005f00000004000000"))

        assertEquals(0x82, start.dataType)
        assertEquals(5, start.payload[0].toInt() and 0xff)
        assertEquals(1, start.payload[1].toInt() and 0xff)
        assertArrayEquals(start.raw, NxWearSportSession.encode(5, NxWearSportSession.START))
        assertArrayEquals(stop.raw, NxWearSportSession.encode(5, NxWearSportSession.STOP, 95, 4))
    }

    @Test
    fun mapsEveryPhoneExerciseSportToActivityKind() {
        val expected = mapOf(
            5 to ActivityKind.OUTDOOR_RUNNING,
            4 to ActivityKind.INDOOR_RUNNING,
            2 to ActivityKind.OUTDOOR_CYCLING,
            3 to ActivityKind.INDOOR_CYCLING,
            7 to ActivityKind.OUTDOOR_WALKING,
            11 to ActivityKind.BASKETBALL,
            14 to ActivityKind.SOCCER,
            10 to ActivityKind.BADMINTON,
            6 to ActivityKind.SWIMMING,
            12 to ActivityKind.JUMP_ROPING,
            15 to ActivityKind.MOUNTAINEERING,
            9 to ActivityKind.YOGA,
        )

        assertEquals(expected, NxWearSportSession.SPORTS.associate { it.type to it.activityKind })
    }

    @Test
    fun parsesBloodOxygenReadingRatherThanControlStateAsValue() {
        val control = NxWearPacket.parse(bytes("000b00093115000001003100000000000000a0a7"))
        val reading = NxWearPacket.parse(bytes("000b000a3114000008000100010c7abe6a616a42"))
        val readings = reading.timedReadings(NxWearDataType.SPO2)

        assertNull(control.timedReadings(NxWearDataType.SPO2))
        assertEquals(0x31, control.payload[0].toInt() and 0xff)
        assertEquals(1, readings?.size)
        assertEquals(97, readings?.single()?.value)
        assertEquals(0x6abe7a0cL * 1000L, readings?.single()?.timestampMillis)
    }

    @Test
    fun rejectsTruncatedBloodOxygenReading() {
        val packet = NxWearPacket.parse(NxWearPacket.frame(10, 0x31, NxWearDataType.SPO2, bytes("0100010c7abe6a")))

        assertNull(packet.timedReadings(NxWearDataType.SPO2))
    }

    @Test
    fun parsesStressReadingRatherThanControlStateAsScore() {
        val controlFrame = bytes("000b00543115000001005100000000000000a0a7")
        val readingFrame = bytes("000b0055312d00000800010001f970be6a330001")
        val control = NxWearPacket.parse(controlFrame)
        val reading = NxWearPacket.parse(readingFrame)
        val readings = NxWearStressReading.parse(reading)

        assertNull(NxWearStressReading.parse(control))
        assertEquals(0x51, control.payload[0].toInt() and 0xff)
        assertEquals(1, readings?.size)
        assertEquals(51, readings?.single()?.stress)
        assertEquals(0x6abe70f9L * 1000L, readings?.single()?.timestampMillis)
    }

    @Test
    fun parsesMultipleTimedReadingsOnlyForMatchingDataType() {
        val payload = bytes("010001f970be6a33f970be6a34")
        val packet = NxWearPacket.parse(NxWearPacket.frame(0x55, 0x31, NxWearDataType.STRESS, payload))

        assertNull(packet.timedReadings(NxWearDataType.HEART_RATE))
        assertEquals(listOf(
            NxWearPacket.TimedReading(0x6abe70f9L * 1000L, 51),
            NxWearPacket.TimedReading(0x6abe70f9L * 1000L, 52),
        ), packet.timedReadings(NxWearDataType.STRESS))
    }

    @Test
    fun rejectsTruncatedStressReading() {
        val packet = NxWearPacket.parse(NxWearPacket.frame(0x55, 0x31, 0x2d, bytes("010001f970be6a")))

        assertNull(NxWearStressReading.parse(packet))
    }

    @Test
    fun recognizesPostStressSyncContinuation() {
        val first = NxWearPacket.parse(bytes("000b015631fa00000b00010001074a0000000001"))
        val continuation = NxWearPacket.parse(bytes("0120000000000000000000000000000b00800100"))

        assertEquals(0x56, first.sequence)
        assertEquals(0xfa, first.dataType)
        assertEquals(11, first.declaredLength)
        assertEquals(2, NxWearPacket.expectedFragmentCount(first.declaredLength!!))
        assertEquals(1, continuation.continuationIndex)
        assertEquals(0x20, continuation.payload[0].toInt() and 0xff)
    }

    @Test
    fun buildsObservedInitializationBootstrapWithDynamicLittleEndianTimestamp() {
        val settings = NxWearBootstrap(
            userId = 0x000bb5bc,
            isMale = false,
            age = 20,
            heightCm = 170,
            weightKg = 50,
            epochSeconds = 0x68eb53beL,
            timezoneOffsetSeconds = 7200,
            is24Hour = true,
            stepsGoal = 8000,
            distanceGoalMeters = 5000,
            caloriesGoal = 300,
            sleepGoalSeconds = 27000,
            activeTimeGoalSeconds = 10800,
        )
        val captured = bytes("00010300016e000043004100070c0066bcb50b00010114aa32000c0068be53eb68201c0000010800027c01ffff00000400670004006d0113006f401f030000881300002c0100007869302a0500780000")
        assertArrayEquals(captured, NxWearProtocol.encodeInitializationBootstrap(settings))
        val parsed = NxWearPacket.parse(captured)
        assertEquals(0x01, parsed.deviceType)
        assertEquals(0x6e, parsed.dataType)
        assertEquals(67, parsed.declaredLength)
        assertEquals(true, parsed.isComplete)
        assertArrayEquals(bytes("be53eb68"), parsed.payload.copyOfRange(18, 22))
        val next = NxWearProtocol.encodeInitializationBootstrap(settings.copy(epochSeconds = 0x68eb53c0L))
        assertArrayEquals(bytes("68c053eb68"), next.copyOfRange(28, 33))
        assertEquals(0x01, next[20].toInt() and 0xff)
        assertEquals(0x02, next[40].toInt() and 0xff)
        assertEquals(0x03, next[60].toInt() and 0xff)
    }

    @Test
    fun buildsInitializationBootstrapFromProfileAndGoalValues() {
        val settings = NxWearBootstrap(0, true, 34, 182, 81, 0x12345678L, -3600,
            false, 12000, 6500, 450, 28800, 5400)
        val packet = NxWearPacket.parse(NxWearProtocol.encodeInitializationBootstrap(settings))
        val payload = packet.payload

        assertEquals(true, packet.isComplete)
        assertArrayEquals(bytes("000000000022b65100"), payload.copyOfRange(6, 15))
        assertArrayEquals(bytes("78563412f0f1ffff00"), payload.copyOfRange(18, 27))
        assertArrayEquals(bytes("e02e000064190000c201000080701815"), payload.copyOfRange(46, 62))
    }

    @Test
    fun buildsObservedInitializationReadSequencePackets() {
        val captured = listOf(
            "000b000104090000010001000000000000000000",
            "000b000204040000010001000000000000000000",
            "000b000304070000010001000000000000000000",
            "000b000404080000010001000000000000000000",
            "000b000504280000010001000000000000000000",
            "000b0006042a0000010001000000000000000000",
            "000b000704fa0000010001000000000000000000",
        )
        for ((index, frame) in captured.withIndex()) {
            val dataType = NxWearPacket.parse(bytes(frame)).dataType!!
            assertArrayEquals(bytes(frame), NxWearPacket.ack(index + 1, dataType))
        }
        assertArrayEquals(bytes("000b000b04090000010001000000000000000000"),
            NxWearPacket.ack(0x0b, NxWearDataType.DEVICE_SYNC))
    }

    @Test
    fun calculatesActivityFragmentCountsFromDeclaredDataLength() {
        assertEquals(1, NxWearPacket.expectedFragmentCount(0))
        assertEquals(1, NxWearPacket.expectedFragmentCount(10))
        assertEquals(2, NxWearPacket.expectedFragmentCount(11))
        assertEquals(2, NxWearPacket.expectedFragmentCount(29))
        assertEquals(3, NxWearPacket.expectedFragmentCount(30))
        assertEquals(5, NxWearPacket.expectedFragmentCount(70))
        assertEquals(2, NxWearPacket.expectedFragmentCount(23))
        assertEquals(4, NxWearPacket.expectedFragmentCount(59))
        assertEquals(7, NxWearPacket.expectedFragmentCount(115))
    }

    @Test
    fun genericFrameRoundTripsSequenceAndDeclaredPayload() {
        val raw = NxWearPacket.frame(0x34, 0xab, 0xcd, bytes("010203"))
        assertArrayEquals(bytes("000b0034abcd0000030001020300000000000000"), raw)
        val parsed = NxWearPacket.parse(raw)
        assertEquals(0x34, parsed.sequence)
        assertEquals(0xab, parsed.command)
        assertEquals(0xcd, parsed.dataType)
        assertEquals(3, parsed.declaredLength)
        assertArrayEquals(bytes("010203"), parsed.payload)
    }

    @Test
    fun parsesCoalescedPagesWithoutContinuationIndexes() {
        val payload = ByteArray(67) { it.toByte() }
        val raw = NxWearPacket.frame(0, 0x01, 0x6e, payload, deviceType = 0x01)
        val packet = NxWearPacket.parse(raw)

        assertEquals(80, raw.size)
        assertEquals(3, packet.pageCount)
        assertEquals(4, packet.pagesPresent)
        assertEquals(true, packet.isComplete)
        assertArrayEquals(payload, packet.payload)
        assertEquals(1, raw[20].toInt())
        assertEquals(2, raw[40].toInt())
        assertEquals(3, raw[60].toInt())
    }

    @Test
    fun recognizesIncompleteFirstPageAndRejectsOutOfOrderCoalescedPages() {
        val raw = NxWearPacket.frame(0, 0x31, 0x09, ByteArray(70))
        val firstPage = NxWearPacket.parse(raw.copyOfRange(0, 20))
        assertEquals(NxWearPacket.Kind.FRAMED, firstPage.kind)
        assertEquals(4, firstPage.pageCount)
        assertEquals(70, firstPage.declaredLength)
        assertEquals(10, firstPage.payload.size)
        assertEquals(false, firstPage.isComplete)

        raw[40] = 0x03.toByte()
        assertEquals(NxWearPacket.Kind.RAW, NxWearPacket.parse(raw).kind)
    }

    @Test
    fun rejectsDeclaredLengthBeyondAvailablePageCapacity() {
        val raw = NxWearPacket.frame(0, 0x31, 0x03, bytes("0e01"))
        raw[8] = 0x0b.toByte()

        assertEquals(NxWearPacket.Kind.RAW, NxWearPacket.parse(raw).kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfBoundsSequence() {
        NxWearPacket.frame(0x100, 0x01, 0x03, byteArrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfBoundsLe16() {
        bytes("00").le16(0)
    }
}
