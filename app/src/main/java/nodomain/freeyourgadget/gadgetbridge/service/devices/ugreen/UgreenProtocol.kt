package nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_ANC_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_DUAL_CONNECT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_EQ_PRESET
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_GAME_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_HIGH_QUALITY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_LAST_ACTIVE_ANC
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_LANG
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_VOLUME
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_SPATIAL_AUDIO
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenAncMode
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenEqualizer
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenPromptLanguage
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.Locale

class UgreenProtocol {
    companion object {
        private val LOG = LoggerFactory.getLogger(UgreenProtocol::class.java)

        val REQ_HEADER = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val RESP_HEADER = byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        val HW_HEADER = byteArrayOf(0x85.toByte(), 0x86.toByte(), 0x87.toByte())

        const val CMD_VERSION: Byte = 1
        const val CMD_DEVICE_STATE: Byte = 4
        const val CMD_EQ: Byte = 5
        const val CMD_DUAL_CONNECT: Byte = 6
        const val CMD_GAME_MODE: Byte = 8
        const val CMD_NOISE_REDUCTION: Byte = 9
        const val CMD_HIGH_QUALITY: Byte = 11
        const val CMD_PROMPT_LANG: Byte = 12
        const val CMD_FACTORY_RESET: Byte = 14
        const val CMD_SOUND_VOLUME: Byte = 17
        const val CMD_SPATIAL_AUDIO: Byte = 18
        const val CMD_WEAR_DETECTION: Byte = 19

        fun encodePacket(cmd: Byte, data: ByteArray): ByteArray {
            val packet = ByteArray(3 + 1 + 1 + data.size + 2)
            packet[0] = REQ_HEADER[0]
            packet[1] = REQ_HEADER[1]
            packet[2] = REQ_HEADER[2]
            packet[3] = cmd
            packet[4] = data.size.toByte()
            System.arraycopy(data, 0, packet, 5, data.size)

            val crcBytes = packet.copyOfRange(3, 5 + data.size)
            val crc = CheckSums.getCRC16ansi(crcBytes)
            packet[5 + data.size] = (crc and 0xFF).toByte()
            packet[5 + data.size + 1] = ((crc ushr 8) and 0xFF).toByte()
            return packet
        }

        fun encodeGetVersion(): ByteArray = encodePacket(CMD_VERSION, byteArrayOf(0))

        fun encodeGetDeviceState(): ByteArray = encodePacket(CMD_DEVICE_STATE, byteArrayOf(0))

        fun encodeSetAnc(mode: UgreenAncMode, activeDepth: UgreenAncMode? = null): ByteArray =
            encodePacket(CMD_NOISE_REDUCTION, byteArrayOf(mode.getRawValue(activeDepth)))

        fun encodeSetEq(eq: UgreenEqualizer): ByteArray =
            encodePacket(CMD_EQ, byteArrayOf(eq.code))

        fun encodeSetPromptLang(lang: UgreenPromptLanguage): ByteArray =
            encodePacket(CMD_PROMPT_LANG, byteArrayOf(lang.code))

        fun encodeSetPromptVolume(volume: Int): ByteArray =
            encodePacket(CMD_SOUND_VOLUME, byteArrayOf(volume.coerceIn(1, 15).toByte()))

        fun encodeSetDualConnect(enabled: Boolean): ByteArray =
            encodePacket(CMD_DUAL_CONNECT, byteArrayOf(if (enabled) 1 else 0))

        fun encodeSetGameMode(enabled: Boolean): ByteArray =
            encodePacket(CMD_GAME_MODE, byteArrayOf(if (enabled) 1 else 0))

        fun encodeSetHighQuality(enabled: Boolean): ByteArray =
            encodePacket(CMD_HIGH_QUALITY, byteArrayOf(if (enabled) 1 else 0))

        fun encodeSetSpatialAudio(enabled: Boolean): ByteArray =
            encodePacket(CMD_SPATIAL_AUDIO, byteArrayOf(if (enabled) 1 else 0))

        fun encodeSetWearDetection(enabled: Boolean): ByteArray =
            encodePacket(CMD_WEAR_DETECTION, byteArrayOf(if (enabled) 1 else 0))

        fun encodeFactoryReset(): ByteArray =
            encodePacket(CMD_FACTORY_RESET, byteArrayOf(0))

        fun hardwareFrameDataLength(cmd: Byte): Int {
            return when (cmd.toInt() and 0xFF) {
                1 -> 3
                8 -> 0
                9 -> 2
                else -> 1
            }
        }

        fun extractBatteryLevel(payload: ByteArray): Int? {
            for (i in 0 until minOf(payload.size, 3)) {
                val level = payload[i].toInt() and 0xFF
                if (level in 0..100) {
                    return level
                }
            }
            return null
        }
    }

    private var buffer = ByteArray(0)

    @Synchronized
    fun processIncomingBytes(bytes: ByteArray): List<GBDeviceEvent> {
        val newBuffer = ByteArray(buffer.size + bytes.size)
        System.arraycopy(buffer, 0, newBuffer, 0, buffer.size)
        System.arraycopy(bytes, 0, newBuffer, buffer.size, bytes.size)
        buffer = newBuffer

        val events = mutableListOf<GBDeviceEvent>()
        while (true) {
            val frameResult = extractNextFrame(buffer) ?: break
            val frame = frameResult.first
            val remainingOffset = frameResult.second
            buffer = buffer.copyOfRange(remainingOffset, buffer.size)

            try {
                val frameEvents = decodeFrame(frame)
                events.addAll(frameEvents)
            } catch (e: Exception) {
                LOG.error("Failed to decode frame", e)
            }
        }
        return events
    }

    private fun extractNextFrame(data: ByteArray): Pair<ByteArray, Int>? {
        var index = 0
        while (index + 5 <= data.size) {
            // Check Hardware notification header (0x85, 0x86, 0x87)
            if (data[index] == HW_HEADER[0] &&
                data[index + 1] == HW_HEADER[1] &&
                data[index + 2] == HW_HEADER[2]
            ) {
                val cmd = data[index + 4]
                val totalLen = 5 + hardwareFrameDataLength(cmd)
                if (index + totalLen <= data.size) {
                    val frame = data.copyOfRange(index, index + totalLen)
                    return Pair(frame, index + totalLen)
                }
                return null
            }

            // Check Response header (0xDD, 0xEE, 0xFF)
            if (data[index] == RESP_HEADER[0] &&
                data[index + 1] == RESP_HEADER[1] &&
                data[index + 2] == RESP_HEADER[2]
            ) {
                if (index + 6 > data.size) {
                    return null
                }
                val payloadLen = data[index + 5].toInt() and 0xFF
                val declaredTotal = 8 + payloadLen
                if (index + declaredTotal <= data.size) {
                    val frame = data.copyOfRange(index, index + declaredTotal)
                    val expectedCrc = ((data[index + declaredTotal - 1].toInt() and 0xFF) shl 8) or
                            (data[index + declaredTotal - 2].toInt() and 0xFF)
                    val actualCrc = CheckSums.getCRC16ansi(
                        data.copyOfRange(index + 3, index + declaredTotal - 2)
                    )
                    if (expectedCrc != actualCrc) {
                        LOG.warn("CRC mismatch in response frame: expected 0x{:04X}, calculated 0x{:04X}", expectedCrc, actualCrc)
                    }
                    return Pair(frame, index + declaredTotal)
                }
                return null
            }

            index++
        }
        return null
    }

    fun decodeFrame(frame: ByteArray): List<GBDeviceEvent> {
        val events = mutableListOf<GBDeviceEvent>()
        if (frame.size < 5) return events

        if (frame[0] == HW_HEADER[0] && frame[1] == HW_HEADER[1] && frame[2] == HW_HEADER[2]) {
            val cmd = frame[4]
            val payload = frame.copyOfRange(5, frame.size)
            decodeHardwareReport(cmd, payload, events)
            return events
        }

        if (frame.size >= 8 &&
            frame[0] == RESP_HEADER[0] && frame[1] == RESP_HEADER[1] && frame[2] == RESP_HEADER[2]
        ) {
            val cmd = frame[3]
            val status = frame[4]
            val payloadLen = frame[5].toInt() and 0xFF
            val endPayload = minOf(6 + payloadLen, frame.size - 2)
            val payload = if (endPayload >= 6) frame.copyOfRange(6, endPayload) else ByteArray(0)

            decodeResponse(cmd, status != 0.toByte(), payload, events)
        }
        return events
    }

    private fun decodeHardwareReport(cmd: Byte, payload: ByteArray, events: MutableList<GBDeviceEvent>) {
        when (cmd.toInt() and 0xFF) {
            1 -> {
                val battery = extractBatteryLevel(payload)
                if (battery != null) {
                    val batteryEvent = GBDeviceEventBatteryInfo().apply {
                        batteryIndex = 0
                        level = battery
                        state = BatteryState.BATTERY_NORMAL
                    }
                    events.add(batteryEvent)
                }
            }
            2 -> {
                if (payload.isNotEmpty()) {
                    val noise = payload[0].toInt() and 0xFF
                    val mode = UgreenAncMode.fromRawValue(noise)
                    val depth = UgreenAncMode.depthFromRawValue(noise)
                    val prefsEvent = GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_ANC_MODE, mode.name.lowercase(Locale.ROOT))
                        if (depth != null) {
                            withPreference(PREF_UGREEN_LAST_ACTIVE_ANC, depth.name.lowercase(Locale.ROOT))
                        }
                    }
                    events.add(prefsEvent)
                }
            }
            4 -> {
                if (payload.isNotEmpty()) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_GAME_MODE, (payload[0].toInt() and 0xFF) == 1)
                    })
                }
            }
            5 -> {
                if (payload.isNotEmpty()) {
                    val eq = UgreenEqualizer.fromCode(payload[0].toInt() and 0xFF)
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_EQ_PRESET, eq.name.lowercase(Locale.ROOT))
                    })
                }
            }
            10 -> {
                if (payload.isNotEmpty()) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_SPATIAL_AUDIO, (payload[0].toInt() and 0xFF) == 1)
                    })
                }
            }
            12 -> {
                if (payload.isNotEmpty()) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_DUAL_CONNECT, (payload[0].toInt() and 0xFF) == 1)
                    })
                }
            }
        }
    }

    private fun decodeResponse(cmd: Byte, success: Boolean, payload: ByteArray, events: MutableList<GBDeviceEvent>) {
        when (cmd) {
            CMD_VERSION -> {
                val versionStr = parseVersionString(payload)
                if (versionStr.isNotEmpty()) {
                    events.add(GBDeviceEventVersionInfo().apply {
                        fwVersion = versionStr
                    })
                }
            }
            CMD_DEVICE_STATE -> {
                if (payload.isNotEmpty()) {
                    val battery = extractBatteryLevel(payload)
                    if (battery != null) {
                        events.add(GBDeviceEventBatteryInfo().apply {
                            batteryIndex = 0
                            level = battery
                            state = BatteryState.BATTERY_NORMAL
                        })
                    }

                    val prefsEvent = GBDeviceEventUpdatePreferences()
                    if (payload.size > 3) {
                        val rawNoise = payload[3].toInt() and 0xFF
                        val ancMode = UgreenAncMode.fromRawValue(rawNoise)
                        val depth = UgreenAncMode.depthFromRawValue(rawNoise)
                        prefsEvent.withPreference(PREF_UGREEN_ANC_MODE, ancMode.name.lowercase(Locale.ROOT))
                        if (depth != null) {
                            prefsEvent.withPreference(PREF_UGREEN_LAST_ACTIVE_ANC, depth.name.lowercase(Locale.ROOT))
                        }
                    }
                    if (payload.size > 4) {
                        val eq = UgreenEqualizer.fromCode(payload[4].toInt() and 0xFF)
                        prefsEvent.withPreference(PREF_UGREEN_EQ_PRESET, eq.name.lowercase(Locale.ROOT))
                    }
                    if (payload.size > 5) {
                        prefsEvent.withPreference(PREF_UGREEN_DUAL_CONNECT, (payload[5].toInt() and 0xFF) == 1)
                    }
                    if (payload.size > 6) {
                        prefsEvent.withPreference(PREF_UGREEN_GAME_MODE, (payload[6].toInt() and 0xFF) == 1)
                    }
                    if (payload.size > 7) {
                        val hq = (payload[7].toInt() and 0xFF) == 1
                        prefsEvent.withPreference(PREF_UGREEN_HIGH_QUALITY, hq)
                        if (hq) {
                            prefsEvent.withPreference(PREF_UGREEN_GAME_MODE, false)
                            prefsEvent.withPreference(PREF_UGREEN_DUAL_CONNECT, false)
                        }
                    }
                    if (payload.size > 16) {
                        val lang = UgreenPromptLanguage.fromCode(payload[16].toInt() and 0xFF)
                        prefsEvent.withPreference(PREF_UGREEN_PROMPT_LANG, lang.name.lowercase(Locale.ROOT))
                    }
                    if (payload.size > 19) {
                        val vol = payload[19].toInt() and 0xFF
                        if (vol in 1..15) {
                            prefsEvent.withPreference(PREF_UGREEN_PROMPT_VOLUME, vol)
                        }
                    }
                    if (payload.size > 20) {
                        prefsEvent.withPreference(PREF_UGREEN_SPATIAL_AUDIO, (payload[20].toInt() and 0xFF) == 1)
                    }
                    events.add(prefsEvent)
                }
            }
            CMD_DUAL_CONNECT -> {
                if (payload.isNotEmpty()) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_DUAL_CONNECT, (payload[0].toInt() and 0xFF) == 1)
                    })
                }
            }
            CMD_GAME_MODE -> {
                if (payload.isNotEmpty()) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_GAME_MODE, (payload[0].toInt() and 0xFF) == 1)
                    })
                }
            }
            CMD_NOISE_REDUCTION -> {
                if (payload.isNotEmpty()) {
                    val rawNoise = payload[0].toInt() and 0xFF
                    val ancMode = UgreenAncMode.fromRawValue(rawNoise)
                    val depth = UgreenAncMode.depthFromRawValue(rawNoise)
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_ANC_MODE, ancMode.name.lowercase(Locale.ROOT))
                        if (depth != null) {
                            withPreference(PREF_UGREEN_LAST_ACTIVE_ANC, depth.name.lowercase(Locale.ROOT))
                        }
                    })
                }
            }
            CMD_EQ -> {
                if (payload.isNotEmpty()) {
                    val eq = UgreenEqualizer.fromCode(payload[0].toInt() and 0xFF)
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_EQ_PRESET, eq.name.lowercase(Locale.ROOT))
                    })
                }
            }
            CMD_HIGH_QUALITY -> {
                if (payload.isNotEmpty()) {
                    val hq = (payload[0].toInt() and 0xFF) == 1
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_HIGH_QUALITY, hq)
                        if (hq) {
                            withPreference(PREF_UGREEN_GAME_MODE, false)
                            withPreference(PREF_UGREEN_DUAL_CONNECT, false)
                        }
                    })
                }
            }
            CMD_PROMPT_LANG -> {
                if (payload.isNotEmpty()) {
                    val lang = UgreenPromptLanguage.fromCode(payload[0].toInt() and 0xFF)
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_PROMPT_LANG, lang.name.lowercase(Locale.ROOT))
                    })
                }
            }
            CMD_SOUND_VOLUME -> {
                if (payload.isNotEmpty()) {
                    val vol = payload[0].toInt() and 0xFF
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_PROMPT_VOLUME, vol)
                    })
                }
            }
            CMD_SPATIAL_AUDIO -> {
                if (payload.isNotEmpty()) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_UGREEN_SPATIAL_AUDIO, (payload[0].toInt() and 0xFF) == 1)
                    })
                }
            }
        }
    }

    private fun parseVersionString(payload: ByteArray): String {
        if (payload.size >= 3) {
            val b0 = payload[0].toInt() and 0xFF
            val b1 = payload[1].toInt() and 0xFF
            val b2 = payload[2].toInt() and 0xFF
            if (b0 != 0 || b1 != 0 || b2 != 0) {
                return "$b0.$b1.$b2"
            }
        }
        if (payload.size >= 6) {
            val b3 = payload[3].toInt() and 0xFF
            val b4 = payload[4].toInt() and 0xFF
            val b5 = payload[5].toInt() and 0xFF
            if (b3 != 0 || b4 != 0 || b5 != 0) {
                return "$b3.$b4.$b5"
            }
        }
        val ascii = String(payload, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
        return ascii.ifEmpty { "unknown" }
    }
}
