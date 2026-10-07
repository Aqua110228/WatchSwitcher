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

/** B8 initialization/history sequencing and reassembly. BLE, timers and persistence stay in support. */
internal class NxWearHistorySync {
    enum class Step { WAIT_DEVICE_SYNC, WAIT_REALTIME_SPORT, WAIT_HISTORY_DATA, COMPLETE }

    data class CompletedFrame(
        val deviceSyncPayload: ByteArray? = null,
        val sportPacket: NxWearPacket? = null,
        val historyPacket: NxWearPacket? = null,
        val acknowledgedType: Int,
        val request: ByteArray,
    )

    var step = Step.WAIT_DEVICE_SYNC
        private set
    private var fragments = 0
    private var expectedFragments = 0
    private var nextSequence = 1
    private var currentSubtype: Int? = null
    private var deviceSyncPayload: ByteArray? = null
    private var deviceSyncLength = 0
    private var sportPacket: NxWearPacket? = null
    private var historyPacket: NxWearPacket? = null
    private var logSequence: Int? = null
    private var logPages = 0
    private var expectedLogPages = 0

    val isComplete: Boolean get() = step == Step.COMPLETE
    val hasPendingLog: Boolean get() = logSequence != null

    fun handlesLiveActivity(dataType: Int?): Boolean = when (dataType) {
        NxWearDataType.REALTIME_SPORT -> isComplete || step == Step.WAIT_HISTORY_DATA
        NxWearDataType.EXERCISE_HEART_RATE -> isComplete
        else -> false
    }

    fun resetForInitialization() {
        finish()
        deviceSyncLength = 0
        beginFetch()
    }

    fun beginFetch() {
        // A manual fetch restarts the stream; connection initialization also clears all buffers.
        step = Step.WAIT_DEVICE_SYNC
        fragments = 0
        expectedFragments = 0
        nextSequence = 1
        currentSubtype = null
        historyPacket = null
    }

    fun finish() {
        step = Step.COMPLETE
        fragments = 0
        expectedFragments = 0
        currentSubtype = null
        deviceSyncPayload = null
        sportPacket = null
        historyPacket = null
        clearLog()
    }

    fun acknowledge(sequence: Int) {
        if (!isComplete) nextSequence = (sequence + 1) and 0xff
    }

    fun expects(dataType: Int): Boolean = when (step) {
        Step.WAIT_DEVICE_SYNC -> dataType == NxWearDataType.DEVICE_SYNC
        Step.WAIT_REALTIME_SPORT -> dataType == NxWearDataType.REALTIME_SPORT
        Step.WAIT_HISTORY_DATA -> NxWearProtocol.isHistoryDataType(dataType)
        Step.COMPLETE -> false
    }

    fun acceptFrame(packet: NxWearPacket): Boolean {
        val subtype = requireNotNull(packet.dataType)
        if (!expects(subtype)) return false
        currentSubtype = subtype
        nextSequence = requireNotNull(packet.sequence)
        val length = packet.declaredLength ?: return false
        expectedFragments = NxWearPacket.expectedFragmentCount(length)
        fragments = packet.pagesPresent
        when (step) {
            Step.WAIT_DEVICE_SYNC -> {
                deviceSyncPayload = packet.payload
                deviceSyncLength = length
            }
            Step.WAIT_REALTIME_SPORT -> sportPacket = packet
            Step.WAIT_HISTORY_DATA -> historyPacket = packet
            Step.COMPLETE -> Unit
        }
        return true
    }

    fun acceptContinuation(packet: NxWearPacket): Boolean {
        if (isComplete || fragments == 0 || packet.continuationIndex != fragments) return false
        if (step == Step.WAIT_HISTORY_DATA) {
            historyPacket = historyPacket?.append(packet) ?: return false
        }
        fragments += packet.pagesPresent
        if (step == Step.WAIT_DEVICE_SYNC) {
            deviceSyncPayload?.let {
                deviceSyncPayload = (it + packet.payload).copyOf(minOf(it.size + packet.payload.size, deviceSyncLength))
            }
        }
        // Preserve initialization's page-count acceptance even if strict sport append rejects a page.
        if (step == Step.WAIT_REALTIME_SPORT) {
            sportPacket?.append(packet)?.let { sportPacket = it }
        }
        return true
    }

    fun takeCompletedFrame(): CompletedFrame? {
        if (isComplete || fragments == 0 || fragments < expectedFragments) return null
        val completedSync = if (step == Step.WAIT_DEVICE_SYNC) {
            deviceSyncPayload?.takeIf { it.size == deviceSyncLength }
        } else null
        val completedSport = if (step == Step.WAIT_REALTIME_SPORT) sportPacket?.takeIf { it.isComplete } else null
        val completedHistory = if (step == Step.WAIT_HISTORY_DATA) {
            historyPacket?.takeIf { it.isComplete } ?: return null
        } else null
        val subtype = when (step) {
            Step.WAIT_DEVICE_SYNC -> NxWearDataType.DEVICE_SYNC
            Step.WAIT_REALTIME_SPORT -> NxWearDataType.REALTIME_SPORT
            Step.WAIT_HISTORY_DATA -> currentSubtype ?: return null
            Step.COMPLETE -> return null
        }
        when (step) {
            Step.WAIT_DEVICE_SYNC -> {
                deviceSyncPayload = null
                deviceSyncLength = 0
                step = Step.WAIT_REALTIME_SPORT
            }
            Step.WAIT_REALTIME_SPORT -> {
                sportPacket = null
                step = Step.WAIT_HISTORY_DATA
            }
            Step.WAIT_HISTORY_DATA -> historyPacket = null
            Step.COMPLETE -> Unit
        }
        fragments = 0
        expectedFragments = 0
        currentSubtype = null
        val sequence = nextSequence
        nextSequence = (sequence + 1) and 0xff
        return CompletedFrame(completedSync, completedSport, completedHistory, subtype, NxWearPacket.ack(sequence, subtype))
    }

    fun acceptLogFrame(packet: NxWearPacket): Int? {
        logSequence = packet.sequence
        logPages = packet.pagesPresent
        expectedLogPages = NxWearPacket.expectedFragmentCount(requireNotNull(packet.declaredLength))
        return takeCompletedLog()
    }

    fun acceptLogContinuation(packet: NxWearPacket): Int? {
        if (packet.continuationIndex != logPages) return null
        logPages += packet.pagesPresent
        return takeCompletedLog()
    }

    private fun takeCompletedLog(): Int? {
        if (logPages < expectedLogPages) return null
        val sequence = logSequence ?: return null
        clearLog()
        return sequence
    }

    private fun clearLog() {
        logSequence = null
        logPages = 0
        expectedLogPages = 0
    }
}
