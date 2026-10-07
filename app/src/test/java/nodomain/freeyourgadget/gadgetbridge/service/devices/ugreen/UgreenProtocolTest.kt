package nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_ANC_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_DUAL_CONNECT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_EQ_PRESET
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_GAME_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_HIGH_QUALITY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_LANG
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_PROMPT_VOLUME
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_SPATIAL_AUDIO
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenAncMode
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenEqualizer
import nodomain.freeyourgadget.gadgetbridge.devices.ugreen.hitunemax5c.UgreenPromptLanguage
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UgreenProtocolTest {

    @Test
    fun testEncodePackets() {
        val versionPacket = UgreenProtocol.encodeGetVersion()
        assertEquals(0xAA.toByte(), versionPacket[0])
        assertEquals(0xBB.toByte(), versionPacket[1])
        assertEquals(0xCC.toByte(), versionPacket[2])
        assertEquals(UgreenProtocol.CMD_VERSION, versionPacket[3])
        assertEquals(1.toByte(), versionPacket[4])
        assertEquals(0.toByte(), versionPacket[5])
        verifyCrc(versionPacket)

        val statePacket = UgreenProtocol.encodeGetDeviceState()
        assertEquals(UgreenProtocol.CMD_DEVICE_STATE, statePacket[3])
        verifyCrc(statePacket)

        val ancPacket = UgreenProtocol.encodeSetAnc(UgreenAncMode.DEEP)
        assertEquals(UgreenProtocol.CMD_NOISE_REDUCTION, ancPacket[3])
        assertEquals(1.toByte(), ancPacket[4])
        assertEquals(0xA1.toByte(), ancPacket[5])
        verifyCrc(ancPacket)

        val eqPacket = UgreenProtocol.encodeSetEq(UgreenEqualizer.BASS)
        assertEquals(UgreenProtocol.CMD_EQ, eqPacket[3])
        assertEquals(1.toByte(), eqPacket[4])
        assertEquals(6.toByte(), eqPacket[5])
        verifyCrc(eqPacket)

        val langPacket = UgreenProtocol.encodeSetPromptLang(UgreenPromptLanguage.ENGLISH)
        assertEquals(UgreenProtocol.CMD_PROMPT_LANG, langPacket[3])
        assertEquals(1.toByte(), langPacket[4])
        assertEquals(0.toByte(), langPacket[5])
        verifyCrc(langPacket)

        val volPacket = UgreenProtocol.encodeSetPromptVolume(12)
        assertEquals(UgreenProtocol.CMD_SOUND_VOLUME, volPacket[3])
        assertEquals(1.toByte(), volPacket[4])
        assertEquals(12.toByte(), volPacket[5])
        verifyCrc(volPacket)

        val dualConnectPacket = UgreenProtocol.encodeSetDualConnect(true)
        assertEquals(UgreenProtocol.CMD_DUAL_CONNECT, dualConnectPacket[3])
        assertEquals(1.toByte(), dualConnectPacket[4])
        assertEquals(1.toByte(), dualConnectPacket[5])
        verifyCrc(dualConnectPacket)

        val gameModePacket = UgreenProtocol.encodeSetGameMode(false)
        assertEquals(UgreenProtocol.CMD_GAME_MODE, gameModePacket[3])
        assertEquals(1.toByte(), gameModePacket[4])
        assertEquals(0.toByte(), gameModePacket[5])
        verifyCrc(gameModePacket)

        val spatialAudioPacket = UgreenProtocol.encodeSetSpatialAudio(true)
        assertEquals(UgreenProtocol.CMD_SPATIAL_AUDIO, spatialAudioPacket[3])
        assertEquals(1.toByte(), spatialAudioPacket[4])
        assertEquals(1.toByte(), spatialAudioPacket[5])
        verifyCrc(spatialAudioPacket)

        val highQualityPacket = UgreenProtocol.encodeSetHighQuality(true)
        assertEquals(UgreenProtocol.CMD_HIGH_QUALITY, highQualityPacket[3])
        assertEquals(1.toByte(), highQualityPacket[4])
        assertEquals(1.toByte(), highQualityPacket[5])
        verifyCrc(highQualityPacket)

        val resetPacket = UgreenProtocol.encodeFactoryReset()
        assertEquals(UgreenProtocol.CMD_FACTORY_RESET, resetPacket[3])
        verifyCrc(resetPacket)
    }

    @Test
    fun testEqualizerCodeMapping() {
        assertEquals(0.toByte(), UgreenEqualizer.CLASSIC.code)
        assertEquals(1.toByte(), UgreenEqualizer.JAZZ.code)
        assertEquals(2.toByte(), UgreenEqualizer.ELECTRONIC.code)
        assertEquals(3.toByte(), UgreenEqualizer.POP.code)
        assertEquals(4.toByte(), UgreenEqualizer.CLASSICAL.code)
        assertEquals(5.toByte(), UgreenEqualizer.ROCK.code)
        assertEquals(6.toByte(), UgreenEqualizer.BASS.code)
        assertEquals(7.toByte(), UgreenEqualizer.TREBLE.code)

        assertEquals(UgreenEqualizer.CLASSIC, UgreenEqualizer.fromCode(0))
        assertEquals(UgreenEqualizer.JAZZ, UgreenEqualizer.fromCode(1))
        assertEquals(UgreenEqualizer.ELECTRONIC, UgreenEqualizer.fromCode(2))
        assertEquals(UgreenEqualizer.POP, UgreenEqualizer.fromCode(3))
        assertEquals(UgreenEqualizer.CLASSICAL, UgreenEqualizer.fromCode(4))
        assertEquals(UgreenEqualizer.ROCK, UgreenEqualizer.fromCode(5))
        assertEquals(UgreenEqualizer.BASS, UgreenEqualizer.fromCode(6))
        assertEquals(UgreenEqualizer.TREBLE, UgreenEqualizer.fromCode(7))
    }

    @Test
    fun testPromptLanguageMapping() {
        assertEquals(0.toByte(), UgreenPromptLanguage.ENGLISH.code)
        assertEquals(1.toByte(), UgreenPromptLanguage.CHINESE.code)

        assertEquals(UgreenPromptLanguage.ENGLISH, UgreenPromptLanguage.fromCode(0))
        assertEquals(UgreenPromptLanguage.CHINESE, UgreenPromptLanguage.fromCode(1))
    }

    @Test
    fun testDecodeVersionResponse() {
        val protocol = UgreenProtocol()
        val payload = byteArrayOf(1, 0, 7)
        val responsePacket = buildTestResponse(UgreenProtocol.CMD_VERSION, true, payload)

        val events = protocol.processIncomingBytes(responsePacket)
        assertEquals(1, events.size)
        val versionEvent = events[0] as GBDeviceEventVersionInfo
        assertEquals("1.0.7", versionEvent.fwVersion)
    }

    @Test
    fun testDecodeDeviceStateResponse() {
        val protocol = UgreenProtocol()
        val payload = ByteArray(21)
        payload[0] = 78.toByte() // Battery 78%
        payload[1] = 0xFF.toByte()
        payload[2] = 0xFF.toByte()
        payload[3] = 0xA1.toByte() // Deep ANC
        payload[4] = 0x03.toByte() // Pop EQ (code 3)
        payload[5] = 0x01.toByte() // Dual connect enabled
        payload[6] = 0x01.toByte() // Game mode enabled
        payload[7] = 0x01.toByte() // High quality enabled
        payload[16] = 0x00.toByte() // English (code 0)
        payload[19] = 11.toByte() // Volume 11
        payload[20] = 0x01.toByte() // Spatial audio enabled

        val responsePacket = buildTestResponse(UgreenProtocol.CMD_DEVICE_STATE, true, payload)
        val events = protocol.processIncomingBytes(responsePacket)
        assertEquals(2, events.size)

        val batteryEvent = events.filterIsInstance<GBDeviceEventBatteryInfo>().firstOrNull()
        assertNotNull(batteryEvent)
        assertEquals(78, batteryEvent!!.level)

        val prefsEvent = events.filterIsInstance<GBDeviceEventUpdatePreferences>().firstOrNull()
        assertNotNull(prefsEvent)
        val prefs = prefsEvent!!.preferences
        assertEquals("deep", prefs[PREF_UGREEN_ANC_MODE])
        assertEquals("deep", prefs[nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_LAST_ACTIVE_ANC])
        assertEquals("pop", prefs[PREF_UGREEN_EQ_PRESET])
        assertEquals(false, prefs[PREF_UGREEN_DUAL_CONNECT]) // Inactive/disabled when LDAC is ON
        assertEquals(false, prefs[PREF_UGREEN_GAME_MODE]) // Inactive/disabled when LDAC is ON
        assertEquals(true, prefs[PREF_UGREEN_HIGH_QUALITY])
        assertEquals("english", prefs[PREF_UGREEN_PROMPT_LANG])
        assertEquals(11, prefs[PREF_UGREEN_PROMPT_VOLUME])
        assertEquals(true, prefs[PREF_UGREEN_SPATIAL_AUDIO])
    }

    @Test
    fun testDecodeDeviceStateWithLdacDisabled() {
        val protocol = UgreenProtocol()
        val payload = ByteArray(21)
        payload[5] = 0x01.toByte() // Dual connect enabled
        payload[6] = 0x01.toByte() // Game mode enabled
        payload[7] = 0x00.toByte() // High quality disabled

        val responsePacket = buildTestResponse(UgreenProtocol.CMD_DEVICE_STATE, true, payload)
        val events = protocol.processIncomingBytes(responsePacket)
        val prefsEvent = events.filterIsInstance<GBDeviceEventUpdatePreferences>().firstOrNull()
        assertNotNull(prefsEvent)
        val prefs = prefsEvent!!.preferences
        assertEquals(true, prefs[PREF_UGREEN_DUAL_CONNECT])
        assertEquals(true, prefs[PREF_UGREEN_GAME_MODE])
        assertEquals(false, prefs[PREF_UGREEN_HIGH_QUALITY])
    }

    @Test
    fun testDecodeSwitchResponses() {
        val protocol = UgreenProtocol()

        // Dual connect response
        val dualConnectPacket = buildTestResponse(UgreenProtocol.CMD_DUAL_CONNECT, true, byteArrayOf(1))
        val eventsDual = protocol.processIncomingBytes(dualConnectPacket)
        assertEquals(1, eventsDual.size)
        val prefsDual = (eventsDual[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals(true, prefsDual[PREF_UGREEN_DUAL_CONNECT])

        // Game mode response
        val gameModePacket = buildTestResponse(UgreenProtocol.CMD_GAME_MODE, true, byteArrayOf(0))
        val eventsGame = protocol.processIncomingBytes(gameModePacket)
        assertEquals(1, eventsGame.size)
        val prefsGame = (eventsGame[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals(false, prefsGame[PREF_UGREEN_GAME_MODE])

        // High quality response
        val hqPacket = buildTestResponse(UgreenProtocol.CMD_HIGH_QUALITY, true, byteArrayOf(1))
        val eventsHq = protocol.processIncomingBytes(hqPacket)
        assertEquals(1, eventsHq.size)
        val prefsHq = (eventsHq[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals(true, prefsHq[PREF_UGREEN_HIGH_QUALITY])
        assertEquals(false, prefsHq[PREF_UGREEN_GAME_MODE])
        assertEquals(false, prefsHq[PREF_UGREEN_DUAL_CONNECT])

        // Spatial audio response
        val spatialPacket = buildTestResponse(UgreenProtocol.CMD_SPATIAL_AUDIO, true, byteArrayOf(1))
        val eventsSpatial = protocol.processIncomingBytes(spatialPacket)
        assertEquals(1, eventsSpatial.size)
        val prefsSpatial = (eventsSpatial[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals(true, prefsSpatial[PREF_UGREEN_SPATIAL_AUDIO])
    }

    @Test
    fun testDecodeHardwareNotificationsForSwitches() {
        val protocol = UgreenProtocol()

        // Game mode notification (cmd=4, value=1)
        val hwGame = byteArrayOf(
            0x85.toByte(), 0x86.toByte(), 0x87.toByte(),
            0x00, 0x04,
            0x01
        )
        val eventsGame = protocol.processIncomingBytes(hwGame)
        assertEquals(1, eventsGame.size)
        val prefsGame = (eventsGame[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals(true, prefsGame[PREF_UGREEN_GAME_MODE])

        // Spatial audio notification (cmd=10, value=0)
        val hwSpatial = byteArrayOf(
            0x85.toByte(), 0x86.toByte(), 0x87.toByte(),
            0x00, 0x0A,
            0x00
        )
        val eventsSpatial = protocol.processIncomingBytes(hwSpatial)
        assertEquals(1, eventsSpatial.size)
        val prefsSpatial = (eventsSpatial[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals(false, prefsSpatial[PREF_UGREEN_SPATIAL_AUDIO])

        // Dual connect notification (cmd=12, value=1)
        val hwDual = byteArrayOf(
            0x85.toByte(), 0x86.toByte(), 0x87.toByte(),
            0x00, 0x0C,
            0x01
        )
        val eventsDual = protocol.processIncomingBytes(hwDual)
        assertEquals(1, eventsDual.size)
        val prefsDual = (eventsDual[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals(true, prefsDual[PREF_UGREEN_DUAL_CONNECT])
    }

    @Test
    fun testDecodeHardwareNotification() {
        val protocol = UgreenProtocol()
        // 0x85, 0x86, 0x87, seq=0, cmd=1 (battery), left=88, right=0, box=0
        val hwBattery = byteArrayOf(
            0x85.toByte(), 0x86.toByte(), 0x87.toByte(),
            0x00, 0x01,
            88.toByte(), 0, 0
        )
        val events = protocol.processIncomingBytes(hwBattery)
        assertEquals(1, events.size)
        val batteryEvent = events[0] as GBDeviceEventBatteryInfo
        assertEquals(88, batteryEvent.level)
    }

    @Test
    fun testStreamingFragmentation() {
        val protocol = UgreenProtocol()
        val payload = byteArrayOf(2, 1, 5)
        val fullPacket = buildTestResponse(UgreenProtocol.CMD_VERSION, true, payload)

        val chunk1 = fullPacket.copyOfRange(0, 4)
        val chunk2 = fullPacket.copyOfRange(4, fullPacket.size)

        val events1 = protocol.processIncomingBytes(chunk1)
        assertTrue(events1.isEmpty())

        val events2 = protocol.processIncomingBytes(chunk2)
        assertEquals(1, events2.size)
        val versionEvent = events2[0] as GBDeviceEventVersionInfo
        assertEquals("2.1.5", versionEvent.fwVersion)
    }

    @Test
    fun testBatteryExtractionFallback() {
        assertEquals(85, UgreenProtocol.extractBatteryLevel(byteArrayOf(0xFF.toByte(), 85.toByte(), 0xFF.toByte())))
        assertEquals(90, UgreenProtocol.extractBatteryLevel(byteArrayOf(90.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertEquals(72, UgreenProtocol.extractBatteryLevel(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 72.toByte())))
        assertEquals(null, UgreenProtocol.extractBatteryLevel(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertEquals(null, UgreenProtocol.extractBatteryLevel(ByteArray(0)))

        // Device state where byte 0 is 0xFF and byte 1 is 82%
        val protocol = UgreenProtocol()
        val payload = ByteArray(20)
        payload[0] = 0xFF.toByte()
        payload[1] = 82.toByte()
        payload[2] = 0xFF.toByte()

        val responsePacket = buildTestResponse(UgreenProtocol.CMD_DEVICE_STATE, true, payload)
        val events = protocol.processIncomingBytes(responsePacket)
        val batteryEvent = events.filterIsInstance<GBDeviceEventBatteryInfo>().firstOrNull()
        assertNotNull(batteryEvent)
        assertEquals(82, batteryEvent!!.level)
    }

    @Test
    fun testDecodeResponseWithNonMatchingCrc() {
        val protocol = UgreenProtocol()
        val payload = byteArrayOf(1, 2, 3)
        val packet = buildTestResponse(UgreenProtocol.CMD_VERSION, true, payload)
        // Corrupt the CRC bytes
        packet[packet.size - 2] = 0
        packet[packet.size - 1] = 0

        val events = protocol.processIncomingBytes(packet)
        assertEquals(1, events.size)
        val versionEvent = events[0] as GBDeviceEventVersionInfo
        assertEquals("1.2.3", versionEvent.fwVersion)
    }

    @Test
    fun testAncEncodingWithDepth() {
        assertEquals(0xA0.toByte(), UgreenAncMode.OFF.getRawValue(UgreenAncMode.DEEP))
        assertEquals(0xA2.toByte(), UgreenAncMode.TRANSPARENCY.getRawValue(UgreenAncMode.DEEP))
        assertEquals(0xB0.toByte(), UgreenAncMode.OFF.getRawValue(UgreenAncMode.MEDIUM))
        assertEquals(0xB2.toByte(), UgreenAncMode.TRANSPARENCY.getRawValue(UgreenAncMode.MEDIUM))
        assertEquals(0xC0.toByte(), UgreenAncMode.OFF.getRawValue(UgreenAncMode.LIGHT))
        assertEquals(0xC2.toByte(), UgreenAncMode.TRANSPARENCY.getRawValue(UgreenAncMode.LIGHT))
        assertEquals(0xD0.toByte(), UgreenAncMode.OFF.getRawValue(UgreenAncMode.ADAPTIVE))
        assertEquals(0xD2.toByte(), UgreenAncMode.TRANSPARENCY.getRawValue(UgreenAncMode.ADAPTIVE))
        assertEquals(0xD0.toByte(), UgreenAncMode.OFF.getRawValue(null))
        assertEquals(0xD2.toByte(), UgreenAncMode.TRANSPARENCY.getRawValue(null))

        assertEquals(0xA1.toByte(), UgreenAncMode.DEEP.getRawValue())
        assertEquals(0xB1.toByte(), UgreenAncMode.MEDIUM.getRawValue())
        assertEquals(0xC1.toByte(), UgreenAncMode.LIGHT.getRawValue())
        assertEquals(0xD1.toByte(), UgreenAncMode.ADAPTIVE.getRawValue())

        val packetOffDeep = UgreenProtocol.encodeSetAnc(UgreenAncMode.OFF, UgreenAncMode.DEEP)
        assertEquals(0xA0.toByte(), packetOffDeep[5])
        verifyCrc(packetOffDeep)

        val packetTransDeep = UgreenProtocol.encodeSetAnc(UgreenAncMode.TRANSPARENCY, UgreenAncMode.DEEP)
        assertEquals(0xA2.toByte(), packetTransDeep[5])
        verifyCrc(packetTransDeep)
    }

    @Test
    fun testAncDecodingWithDepth() {
        val protocol = UgreenProtocol()

        // Test decoding ANC response with 0xA0 (Deep OFF)
        val packetOffDeep = buildTestResponse(UgreenProtocol.CMD_NOISE_REDUCTION, true, byteArrayOf(0xA0.toByte()))
        val eventsOff = protocol.processIncomingBytes(packetOffDeep)
        assertEquals(1, eventsOff.size)
        val prefsOff = (eventsOff[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals("off", prefsOff[PREF_UGREEN_ANC_MODE])
        assertEquals("deep", prefsOff[nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_LAST_ACTIVE_ANC])

        // Test decoding ANC response with 0xD2 (Adaptive Transparency)
        val packetTransAdaptive = buildTestResponse(UgreenProtocol.CMD_NOISE_REDUCTION, true, byteArrayOf(0xD2.toByte()))
        val eventsTrans = protocol.processIncomingBytes(packetTransAdaptive)
        assertEquals(1, eventsTrans.size)
        val prefsTrans = (eventsTrans[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals("transparency", prefsTrans[PREF_UGREEN_ANC_MODE])
        assertEquals("adaptive", prefsTrans[nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_LAST_ACTIVE_ANC])

        // Test hardware notification with cmd=2 and 0xB2 (Medium Transparency)
        val hwTransMedium = byteArrayOf(
            0x85.toByte(), 0x86.toByte(), 0x87.toByte(),
            0x00, 0x02,
            0xB2.toByte()
        )
        val eventsHw = protocol.processIncomingBytes(hwTransMedium)
        assertEquals(1, eventsHw.size)
        val prefsHw = (eventsHw[0] as GBDeviceEventUpdatePreferences).preferences
        assertEquals("transparency", prefsHw[PREF_UGREEN_ANC_MODE])
        assertEquals("medium", prefsHw[nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_UGREEN_LAST_ACTIVE_ANC])
    }

    private fun verifyCrc(packet: ByteArray) {
        val crcCalculated = CheckSums.getCRC16ansi(packet.copyOfRange(3, packet.size - 2))
        val crcInPacket = ((packet[packet.size - 1].toInt() and 0xFF) shl 8) or
                (packet[packet.size - 2].toInt() and 0xFF)
        assertEquals(crcCalculated, crcInPacket)
    }

    private fun buildTestResponse(cmd: Byte, success: Boolean, payload: ByteArray): ByteArray {
        val packet = ByteArray(3 + 1 + 1 + 1 + payload.size + 2)
        packet[0] = 0xDD.toByte()
        packet[1] = 0xEE.toByte()
        packet[2] = 0xFF.toByte()
        packet[3] = cmd
        packet[4] = if (success) 1.toByte() else 0.toByte()
        packet[5] = payload.size.toByte()
        System.arraycopy(payload, 0, packet, 6, payload.size)

        val crc = CheckSums.getCRC16ansi(packet.copyOfRange(3, 6 + payload.size))
        packet[6 + payload.size] = (crc and 0xFF).toByte()
        packet[6 + payload.size + 1] = ((crc ushr 8) and 0xFF).toByte()
        return packet
    }
}
