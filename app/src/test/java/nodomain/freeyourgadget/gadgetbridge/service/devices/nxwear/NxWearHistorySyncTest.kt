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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NxWearHistorySyncTest {
    @Test
    fun followsDeviceSyncSportAndOptionalHistoryTypesWithMatchingSequences() {
        val sync = NxWearHistorySync()
        sync.resetForInitialization()
        assertNull(sync.takeCompletedFrame())
        assertFalse(sync.acceptFrame(frame(1, NxWearDataType.HISTORY_SPORT)))
        assertTrue(sync.acceptFrame(frame(1, NxWearDataType.DEVICE_SYNC)))
        val device = requireNotNull(sync.takeCompletedFrame())
        assertArrayEquals(byteArrayOf(), device.deviceSyncPayload)
        assertRequest(device, 1, NxWearDataType.DEVICE_SYNC)
        assertEquals(NxWearHistorySync.Step.WAIT_REALTIME_SPORT, sync.step)
        assertTrue(sync.acceptFrame(frame(2, NxWearDataType.REALTIME_SPORT)))
        val sport = requireNotNull(sync.takeCompletedFrame())
        assertTrue(requireNotNull(sport.sportPacket).isComplete)
        assertRequest(sport, 2, NxWearDataType.REALTIME_SPORT)
        assertEquals(NxWearHistorySync.Step.WAIT_HISTORY_DATA, sync.step)
        // Unknown record payloads still advance every supported optional subtype.
        for ((index, subtype) in listOf(NxWearDataType.HISTORY_SPORT, NxWearDataType.HISTORY_TEMPERATURE,
                NxWearDataType.HISTORY_HRV, NxWearDataType.DEVICE_LOG).withIndex()) {
            assertTrue(sync.acceptFrame(frame(index + 3, subtype)))
            val history = requireNotNull(sync.takeCompletedFrame())
            assertEquals(subtype, requireNotNull(history.historyPacket).dataType)
            assertRequest(history, index + 3, subtype)
            assertNull(sync.takeCompletedFrame())
        }
    }

    @Test
    fun reassemblesDeviceSyncBeforeAdvancingAndIgnoresOutOfOrderContinuation() {
        val sync = NxWearHistorySync()
        val payload = ByteArray(70) { it.toByte() }
        val raw = NxWearPacket.frame(0xff, 0x31, NxWearDataType.DEVICE_SYNC, payload)
        assertTrue(sync.acceptFrame(NxWearPacket.parse(raw.copyOfRange(0, 20))))
        assertNull(sync.takeCompletedFrame())
        assertFalse(sync.acceptContinuation(NxWearPacket.parse(raw.copyOfRange(40, 60))))
        assertTrue(sync.acceptContinuation(NxWearPacket.parse(raw.copyOfRange(20, 60))))
        assertNull(sync.takeCompletedFrame())
        assertTrue(sync.acceptContinuation(NxWearPacket.parse(raw.copyOfRange(60, 100))))
        val completed = requireNotNull(sync.takeCompletedFrame())
        assertArrayEquals(payload, completed.deviceSyncPayload)
        assertRequest(completed, 0xff, NxWearDataType.DEVICE_SYNC)
    }

    @Test
    fun acknowledgmentAdvancesAndWrapsInitializationSequenceButNotCompletedStream() {
        val sync = NxWearHistorySync()
        assertTrue(sync.acceptFrame(frame(42, NxWearDataType.DEVICE_SYNC)))
        sync.acknowledge(0xff)
        assertRequest(requireNotNull(sync.takeCompletedFrame()), 0, NxWearDataType.DEVICE_SYNC)
        sync.finish()
        sync.acknowledge(99)
        assertNull(sync.takeCompletedFrame())
        assertFalse(sync.acceptFrame(frame(100, NxWearDataType.DEVICE_SYNC)))
    }

    @Test
    fun coalescedHistoryPagesCompleteOnlyOnce() {
        val sync = historyStream()
        val packet = frame(3, NxWearDataType.HISTORY_SPORT, ByteArray(59) { it.toByte() })
        assertTrue(sync.acceptFrame(packet))
        val completed = requireNotNull(sync.takeCompletedFrame())
        assertArrayEquals(packet.payload, requireNotNull(completed.historyPacket).payload)
        assertRequest(completed, 3, NxWearDataType.HISTORY_SPORT)
        assertNull(sync.takeCompletedFrame())
    }

    @Test
    fun reassemblesHistoryWithStrictPacketAppend() {
        val sync = historyStream()
        val payload = ByteArray(30) { it.toByte() }
        val raw = NxWearPacket.frame(3, 0x31, NxWearDataType.HISTORY_SPO2, payload)
        assertTrue(sync.acceptFrame(NxWearPacket.parse(raw.copyOfRange(0, 20))))
        assertFalse(sync.acceptContinuation(NxWearPacket.parse(raw.copyOfRange(40, 60))))
        assertNull(sync.takeCompletedFrame())
        assertTrue(sync.acceptContinuation(NxWearPacket.parse(raw.copyOfRange(20, 40))))
        assertNull(sync.takeCompletedFrame())
        assertTrue(sync.acceptContinuation(NxWearPacket.parse(raw.copyOfRange(40, 60))))
        assertArrayEquals(payload, requireNotNull(requireNotNull(sync.takeCompletedFrame()).historyPacket).payload)
    }

    @Test
    fun rejectsOversizedHistoryContinuationWithoutAdvancing() {
        val sync = historyStream()
        val raw = NxWearPacket.frame(3, 0x31, NxWearDataType.SLEEP, ByteArray(11))
        assertTrue(sync.acceptFrame(NxWearPacket.parse(raw.copyOfRange(0, 20))))
        val continuation = NxWearPacket.parse(raw.copyOfRange(20, 40))
        assertFalse(sync.acceptContinuation(continuation.copy(pagesPresent = 2)))
        assertNull(sync.takeCompletedFrame())
        assertTrue(sync.acceptContinuation(continuation))
        assertRequest(requireNotNull(sync.takeCompletedFrame()), 3, NxWearDataType.SLEEP)
    }

    @Test
    fun preservesInitializationSportPageCountAcceptanceWhenAppendRejects() {
        val sync = NxWearHistorySync()
        sync.acceptFrame(frame(1, NxWearDataType.DEVICE_SYNC))
        sync.takeCompletedFrame()
        val raw = NxWearPacket.frame(2, 0x31, NxWearDataType.REALTIME_SPORT, ByteArray(11))
        sync.acceptFrame(NxWearPacket.parse(raw.copyOfRange(0, 20)))
        val oversized = NxWearPacket.parse(raw.copyOfRange(20, 40)).copy(pagesPresent = 2)
        assertTrue(sync.acceptContinuation(oversized))
        val completed = requireNotNull(sync.takeCompletedFrame())
        assertNull(completed.sportPacket)
        assertRequest(completed, 2, NxWearDataType.REALTIME_SPORT)
        assertEquals(NxWearHistorySync.Step.WAIT_HISTORY_DATA, sync.step)
    }

    @Test
    fun usesDeclaredLengthRatherThanHeaderPageCountForDeviceSync() {
        val sync = NxWearHistorySync()
        val packet = frame(1, NxWearDataType.DEVICE_SYNC, byteArrayOf(1)).copy(pageCount = 2)
        assertFalse(packet.isComplete)
        assertTrue(sync.acceptFrame(packet))
        assertArrayEquals(byteArrayOf(1), requireNotNull(sync.takeCompletedFrame()).deviceSyncPayload)
    }

    @Test
    fun logsAcknowledgeOnlyAfterAllExpectedPagesAndIgnoreOutOfOrderPages() {
        val sync = NxWearHistorySync()
        sync.finish()
        val raw = NxWearPacket.frame(77, 0x31, NxWearDataType.DEVICE_LOG, ByteArray(30))
        assertNull(sync.acceptLogFrame(NxWearPacket.parse(raw.copyOfRange(0, 20))))
        assertTrue(sync.hasPendingLog)
        assertNull(sync.acceptLogContinuation(NxWearPacket.parse(raw.copyOfRange(40, 60))))
        assertNull(sync.acceptLogContinuation(NxWearPacket.parse(raw.copyOfRange(20, 40))))
        assertEquals(77, sync.acceptLogContinuation(NxWearPacket.parse(raw.copyOfRange(40, 60))))
        assertFalse(sync.hasPendingLog)
        assertNull(sync.acceptLogContinuation(NxWearPacket.parse(raw.copyOfRange(40, 60))))
    }

    @Test
    fun coalescedLogsAcknowledgeImmediatelyAndUsePageCountingNotStrictAppend() {
        val sync = NxWearHistorySync()
        assertEquals(4, sync.acceptLogFrame(frame(4, NxWearDataType.DEVICE_LOG, ByteArray(30))))
        val raw = NxWearPacket.frame(5, 0x31, NxWearDataType.DEVICE_LOG, ByteArray(11))
        assertNull(sync.acceptLogFrame(NxWearPacket.parse(raw.copyOfRange(0, 20))))
        assertEquals(5, sync.acceptLogContinuation(NxWearPacket.parse(raw.copyOfRange(20, 40)).copy(pagesPresent = 2)))
    }

    @Test
    fun finishAndReinitializationClearPendingReassemblyAndLogs() {
        val sync = historyStream()
        val raw = NxWearPacket.frame(9, 0x31, NxWearDataType.SLEEP, ByteArray(30))
        sync.acceptFrame(NxWearPacket.parse(raw.copyOfRange(0, 20)))
        val log = NxWearPacket.frame(10, 0x31, NxWearDataType.DEVICE_LOG, ByteArray(11))
        sync.acceptLogFrame(NxWearPacket.parse(log.copyOfRange(0, 20)))
        sync.finish()
        assertTrue(sync.isComplete)
        assertFalse(sync.hasPendingLog)
        assertFalse(sync.acceptContinuation(NxWearPacket.parse(raw.copyOfRange(20, 60))))
        assertNull(sync.takeCompletedFrame())
        sync.resetForInitialization()
        assertEquals(NxWearHistorySync.Step.WAIT_DEVICE_SYNC, sync.step)
        assertNull(sync.takeCompletedFrame())
        assertTrue(sync.acceptFrame(frame(1, NxWearDataType.DEVICE_SYNC)))
        assertRequest(requireNotNull(sync.takeCompletedFrame()), 1, NxWearDataType.DEVICE_SYNC)
    }

    @Test
    fun manualFetchStartsANewDeviceSyncStream() {
        val sync = historyStream()
        sync.finish()
        sync.beginFetch()
        assertFalse(sync.isComplete)
        assertFalse(sync.acceptFrame(frame(4, NxWearDataType.HISTORY_SPORT)))
        assertTrue(sync.acceptFrame(frame(1, NxWearDataType.DEVICE_SYNC)))
        assertRequest(requireNotNull(sync.takeCompletedFrame()), 1, NxWearDataType.DEVICE_SYNC)
    }

    @Test
    fun routesInterleavedDailyTotalsOutsideHistoryReassembly() {
        val sync = NxWearHistorySync()
        assertFalse(sync.handlesLiveActivity(NxWearDataType.REALTIME_SPORT))
        sync.acceptFrame(frame(1, NxWearDataType.DEVICE_SYNC))
        sync.takeCompletedFrame()
        assertFalse(sync.handlesLiveActivity(NxWearDataType.REALTIME_SPORT))
        sync.acceptFrame(frame(2, NxWearDataType.REALTIME_SPORT))
        sync.takeCompletedFrame()
        assertTrue(sync.handlesLiveActivity(NxWearDataType.REALTIME_SPORT))
        assertFalse(sync.handlesLiveActivity(NxWearDataType.EXERCISE_HEART_RATE))
        // An interleaved live ACK must not end or advance the stored-data state machine.
        sync.acknowledge(3)
        assertEquals(NxWearHistorySync.Step.WAIT_HISTORY_DATA, sync.step)
        assertTrue(sync.acceptFrame(frame(4, NxWearDataType.EXERCISE_HEART_RATE)))
        assertRequest(requireNotNull(sync.takeCompletedFrame()), 4, NxWearDataType.EXERCISE_HEART_RATE)
        sync.finish()
        assertTrue(sync.handlesLiveActivity(NxWearDataType.REALTIME_SPORT))
        assertTrue(sync.handlesLiveActivity(NxWearDataType.EXERCISE_HEART_RATE))
        assertFalse(sync.handlesLiveActivity(NxWearDataType.DEVICE_LOG))
        assertFalse(sync.handlesLiveActivity(null))
    }

    @Test
    fun routesCapturedBasketballHeartRatePagesAfterHistoryWaitEnds() {
        val sync = historyStream()
        sync.finish()
        fun packet(hex: String) = NxWearPacket.parse(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val first = packet("000b0108311100001c000500051286c26a681286")
        assertTrue(sync.handlesLiveActivity(first.dataType))
        assertNull(NxWearHeartRateReading.parse(first))
        val complete = requireNotNull(first.append(packet("01c26a681386c26a681386c26a681386c26a680b")))
        val readings = requireNotNull(NxWearHeartRateReading.parse(complete))
        assertEquals(5, readings.size)
        assertEquals(complete.payload.le32(3) * 1000L, readings.first().timestampMillis)
        assertTrue(readings.all { it.bpm == 104 })
        assertArrayEquals(
            packet("000b000804110000010001000000000000000000").raw,
            NxWearPacket.ack(requireNotNull(complete.sequence), requireNotNull(complete.dataType)),
        )
    }

    private fun historyStream(): NxWearHistorySync = NxWearHistorySync().apply {
        acceptFrame(frame(1, NxWearDataType.DEVICE_SYNC))
        takeCompletedFrame()
        acceptFrame(frame(2, NxWearDataType.REALTIME_SPORT))
        takeCompletedFrame()
    }

    private fun frame(sequence: Int, subtype: Int, payload: ByteArray = byteArrayOf()): NxWearPacket =
        NxWearPacket.parse(NxWearPacket.frame(sequence, 0x31, subtype, payload))

    private fun assertRequest(completed: NxWearHistorySync.CompletedFrame, sequence: Int, subtype: Int) {
        assertEquals(subtype, completed.acknowledgedType)
        assertArrayEquals(NxWearPacket.ack(sequence, subtype), completed.request)
    }
}
