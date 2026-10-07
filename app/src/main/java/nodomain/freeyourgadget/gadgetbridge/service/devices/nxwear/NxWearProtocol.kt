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

import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** B8 command payloads and wire-level notification predicates, independent of BLE transport. */
internal object NxWearProtocol {
    val ACTIVITY_SUBTYPES = setOf(
        NxWearDataType.DEVICE_SYNC, NxWearDataType.REALTIME_SPORT, NxWearDataType.HISTORY_SPORT, NxWearDataType.SLEEP,
        NxWearDataType.HEART_RATE, NxWearDataType.EXERCISE_HEART_RATE, NxWearDataType.HISTORY_TEMPERATURE,
        NxWearDataType.HISTORY_HEART_RATE, NxWearDataType.HISTORY_SPO2, NxWearDataType.SPO2, NxWearDataType.HISTORY_HRV,
        NxWearDataType.DEVICE_LOG,
    )
    fun historyTimeoutForSubtype(dataType: Int?): Long =
        if (dataType == NxWearDataType.DEVICE_LOG) 5_000L else 60_000L

    private val HISTORY_DATA_TYPES = ACTIVITY_SUBTYPES - setOf(NxWearDataType.DEVICE_SYNC, NxWearDataType.REALTIME_SPORT)
    fun isHistoryDataType(dataType: Int): Boolean = dataType in HISTORY_DATA_TYPES
    private fun Boolean.toWireByte(): Byte = compareTo(false).toByte()

    fun batteryStateForStatus(status: Int): BatteryState = when (status) {
        0x00 -> BatteryState.BATTERY_NORMAL
        0x01 -> BatteryState.BATTERY_CHARGING
        0x02 -> BatteryState.BATTERY_CHARGING_FULL
        else -> BatteryState.UNKNOWN
    }

    fun encodeInitializationBootstrap(settings: NxWearBootstrap): ByteArray = settings.encode()

    val SETTINGS_ACK = NxWearPacket.ack(0, NxWearDataType.DETECTION_SETTINGS, commandId = 0x34)
    val INITIALIZATION_ACK = NxWearPacket.ack(0, NxWearDataType.BOOTSTRAP, commandId = 0x34)
    val POWER_OFF_ACK = NxWearPacket.ack(0, NxWearDataType.POWER_OFF, commandId = 0x34)

    fun encodePowerOff(): ByteArray = NxWearPacket.command(NxWearDataType.POWER_OFF, 0)

    fun encodeFactoryReset(): ByteArray = NxWearPacket.command(NxWearDataType.FACTORY_RESET, 0)

    fun encodeShakeCamera(enabled: Boolean): ByteArray =
        NxWearPacket.command(NxWearDataType.SHAKE_CAMERA, enabled)

    fun isShakeCameraEvent(packet: NxWearPacket): Boolean =
        packet.kind == NxWearPacket.Kind.FRAMED && packet.command == 0x31 &&
            packet.dataType == NxWearDataType.SHAKE_CAMERA && packet.isComplete

    fun encodeIncomingCall(timeMillis: Long, name: String?, number: String?): ByteArray {
        val seconds = timeMillis / 1000L
        require(seconds in 0..0xffff_ffffL)
        val callerName = name?.takeIf { it.isNotBlank() } ?: "contact"
        val callerNumber = number?.takeIf { it.isNotBlank() } ?: callerName
        val nameBytes = StringUtils.truncateToBytes(callerName, 52)
        val numberBytes = StringUtils.truncateToBytes(callerNumber, 220 - nameBytes.size)
        val timestamp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(seconds.toInt()).array()
        val payload = timestamp + byteArrayOf(1, 2, 1, nameBytes.size.toByte()) + nameBytes +
            byteArrayOf(2, numberBytes.size.toByte()) + numberBytes
        return NxWearPacket.command(NxWearDataType.MESSAGE_NOTICE, payload)
    }

    fun encodeCallEnded(): ByteArray =
        NxWearPacket.command(NxWearDataType.CALL_CONTROL, 2)

    fun encodeMeasurement(dataType: Int, enabled: Boolean): ByteArray = when (dataType) {
        NxWearDataType.MANUAL_HEART_RATE -> encodeManualHeartRate(enabled)
        NxWearDataType.SPO2 -> encodeSpo2Measurement(enabled)
        NxWearDataType.STRESS -> encodeStressMeasurement(enabled)
        else -> error("Unsupported measurement type: $dataType")
    }

    fun encodeManualHeartRate(start: Boolean): ByteArray =
        NxWearPacket.command(NxWearDataType.MANUAL_HEART_RATE, start)

    fun encodeStressMeasurement(enabled: Boolean): ByteArray =
        NxWearPacket.command(NxWearDataType.STRESS, enabled)

    fun encodeSpo2Measurement(enabled: Boolean): ByteArray =
        NxWearPacket.command(NxWearDataType.SPO2, byteArrayOf(if (enabled) 1 else 0, 0, 0, 0, 0))

    fun encodeDetectionSettings(heartRateEnabled: Boolean, intervalMinutes: Int, spo2Enabled: Boolean): ByteArray {
        require(intervalMinutes == 30 || intervalMinutes == 60) { "Unsupported detection interval: $intervalMinutes" }
        return NxWearPacket.command(
            NxWearDataType.DETECTION_SETTINGS,
            byteArrayOf(
                heartRateEnabled.toWireByte(), 0, intervalMinutes.toByte(), 0,
                spo2Enabled.toWireByte(), 0, 0, 0,
            ),
        )
    }

    fun encodeClockSettings(timeMillis: Long, timezoneOffsetSeconds: Int, is24Hour: Boolean): ByteArray {
        val epochSeconds = timeMillis / 1000L
        val timezoneOffset = timezoneOffsetSeconds.toShort().toInt()
        // Captured packets store both values least-significant byte first.
        val timeFields = ByteBuffer.allocate(6)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(epochSeconds.toInt())
            .putShort(timezoneOffset.toShort())
            .array()
        return NxWearPacket.command(
            NxWearDataType.CLOCK,
            timeFields + byteArrayOf(0, 0, is24Hour.toWireByte()),
        )
    }

    fun encodeWristWakeSettings(enabled: Boolean, start: String, end: String, durationSeconds: Int): ByteArray {
        require(durationSeconds in 3..10) { "Unsupported wrist-wake duration: $durationSeconds" }
        fun parseTime(value: String): Pair<Int, Int> {
            val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value)
                ?: throw IllegalArgumentException("Invalid wrist-wake time: $value")
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            require(hour in 0..23 && minute in 0..59) { "Invalid wrist-wake time: $value" }
            return hour to minute
        }
        val (startHour, startMinute) = parseTime(start)
        val (endHour, endMinute) = parseTime(end)
        return NxWearPacket.command(
            NxWearDataType.WRIST_WAKE,
            byteArrayOf(
                enabled.toWireByte(), startHour.toByte(), startMinute.toByte(),
                endHour.toByte(), endMinute.toByte(), durationSeconds.toByte(),
            ),
        )
    }

    fun isBatteryNotification(packet: NxWearPacket): Boolean {
        val payload = packet.payload
        return packet.kind == NxWearPacket.Kind.FRAMED &&
            packet.command == 0x31 && packet.dataType == NxWearDataType.BATTERY && packet.isComplete &&
            payload.size == 2 && (payload[0].toInt() and 0xff) <= 100
    }

    fun isCandidateSettingsReply(packet: NxWearPacket): Boolean {
        val payload = packet.payload
        return packet.kind == NxWearPacket.Kind.FRAMED &&
            packet.command == 0x04 && packet.dataType == NxWearDataType.REALTIME_SPORT && packet.isComplete &&
            payload.contentEquals(byteArrayOf(0x01))
    }
}
