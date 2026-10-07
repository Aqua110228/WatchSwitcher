package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmRegion
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.CardoResponse
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.Services
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.SpecificServices
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.SubscribeState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.configDecodedFieldsToString
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.decodeSubscribeState
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.deviceStateToString
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.ControlRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.ControlSubset
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.FmTuneMode
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.fmRadioToggle
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests.fmradio.fmRadioTune
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.junit.Assert.assertEquals
import org.junit.Test

class CardoSupportTest : TestBase() {

    @Test
    fun testMessages() {
        testConfigMessage(
             bytes(0x40, 0xf, 0x0, 0xc, 0x21, 0x11, 0x4, 0x0, 0x54, 0x0, 0x0, 0x0, 0x5, 0x0, 0x17, 0x1, 0x0),
             """
             selectedLanguage - ENGLISH_US
             isASREnable - true
             isVoicePromptsEnabled - true
             fmRegion - WORLDWIDE
             isFMRDSEnabled - false
             isHFPMixingEnabled - false
             microphoneSensitivity - MEDIUM
             agcSensitivity - 0
             isNoiseGateEnabled - true
             isEcoModeEnabled - false
             isDMCModeEnabled - false
             equalizerProfile - JBL_BASS_BOOST
             deviceLetter - 84
             version - 0
             subVersion - 5
             softwareRevision - 23
             txPowerProfile - 0
             lrSpeakers - false
             """.trimIndent() + "\n"
         )

        testConfigMessage(
            bytes(0x40, 0xc, 0x81, 0x0, 0x32, 0x0, 0x0, 0x82, 0xfa, 0x62, 0x87, 0x1, 0x0, 0x0),
            """
            sensitivity - 50
            isAutomaticVolumeAvailable - true
            isMusicSharingAvailable - true
            isBluetoothICAvailable - true
            isHFPMixingAvailable - true
            isASRAvailable - true
            isDMCAvailable - false
            numberOfSupportedICChannels - 2
            isDynamicICAvailable - false
            isOTAAvailable - true
            isFMAvailable - true
            isPrivateChatAvailable - false
            isEcoModeAvailable - false
            isMobileBridgeAvailable - false
            isLRSpeakersAvailable - true
            isICDMCBridgeAvailable - false
            isAutoOnOffAvailable - false
            isCSLNXTVADLicence - false
            isAdvancedMMIAvailable - true
            """.trimIndent() + "\n"
        )

        testConfigMessage(
            bytes(0x40, 0x12, 0x3, 0x0, 0x0, 0x0, 0x3, 0x0, 0x2, 0x2, 0x0, 0xa8, 0xaa, 0xb9, 0x98, 0x4, 0x6, 0x0, 0x0, 0x0),
            """
            headsetType - 0
            major - 3
            minor - 2
            volumeID - 0
            standByVolume - 10
            groupingVolume - 8
            ag1Volume - 10
            ag2Volume - 10
            fmVolume - 11
            a2dp1Volume - 9
            a2dp2Volume - 9
            intercomBackgroundMusicVolume - PERCENT_90
            mixActiveSpeakerVolume - 4
            isDMCAGCEnabled - false
            isAdvancedMMIEnabled - false
            isRedialASREnabled - false
            isRadioONASREnabled - false
            isAutoOnOffEnabled - false
            """.trimIndent() + "\n"
        )

        testConfigMessage(
            bytes(0x40, 0xa, 0x5, 0xf, 0x7f, 0x84, 0x0, 0xf, 0x0, 0xf, 0x83, 0x80),
            """
            languageList - [ENGLISH_US, ENGLISH_UK, SPANISH, FRENCH, DEUTSCH, JAPANESE, CHINESE, ITALIAN, RUSSIAN, HEBREW, PORTUGUESE]
            equalizer Profiles - [HIGH_VOLUME, BASS_BOOST, VOCAL, OFF, JBL_HIGH_VOLUME, JBL_BASS_BOOST, JBL_VOCAL, JBL_OFF]
            isAccessoriesActivated - true
            """.trimIndent() + "\n"
        )

        testConfigMessage(
            bytes(0x40, 0xe, 0x80, 0x0, 0x22, 0x6a, 0x22, 0x7e, 0x23, 0xa, 0x23, 0x46, 0x23, 0x78, 0x23, 0xbe),
            """
            stat - 0
            stat1 - 8810
            stat2 - 8830
            stat3 - 8970
            stat4 - 9030
            stat5 - 9080
            stat6 - 9150
            """.trimIndent() + "\n"
        )

        testDeviceStateMessage(
            bytes(0x50, 0x1, 0x0, 0x6, 0x23, 0xbe, 0x0),
            """
            state - STAND_BY
            callState - IDLE
            callDirection - UNKNOWN
            fmState - IDLE
            currentSelectedIndex - 6
            currentStation - 9150
            dmcGroupState - READY
            """.trimIndent() + "\n"
        )
    }

    @Test
    fun `decodeMessage realCapture returns only active services`() {
        val payload = bytes(0xD1, 0x03, 0x80)

        val state: SubscribeState = decodeSubscribeState(payload)

        assertEquals(
            setOf(
                Services.CAIP_SRVC_UPDATE,
                Services.CAIP_SRVC_DISCONNECT,
                Services.CAIP_SRVC_BATTERY,
                Services.CAIP_SRVC_STATE
            ),
            state.services
        )
        assertEquals(
            setOf(
                SpecificServices.CAIP_SRVC_MUSIC_SHARING,
                SpecificServices.CAIP_SRVC_IC_MODULE_STATE,
                SpecificServices.CAIP_SRVC_DIRECT_IC_STATE
            ),
            state.specificServices
        )
    }

    @Test
    fun testStringMessage() {
        testDeviceAliasMessage(
            bytes(0x42, 0x8, 0x54, 0x45, 0x53, 0x54, 0x20, 0x4c, 0x53, 0x32),
            "TEST LS2"
        )

        testDeviceSerialNumberMessage(
            bytes(0x43, 0xa, 0x55, 0x43, 0x30, 0x30, 0x30, 0x30, 0x54, 0x45, 0x53, 0x54),
            "UC0000TEST"
        )
    }

     @Test
     fun testControlMessage() {
         val controlRequest = ControlRequest(ControlSubset.FM, bytes(0x00, 0x00))
         println(GB.hexdump(controlRequest.toByteArray()))
         val fm = fmRadioToggle(false)
         println(GB.hexdump(fm.toByteArray()))

         val tuneRequest = fmRadioTune(FmTuneMode.Frequency(9150, CardoFmRegion.WORLDWIDE))
         println(GB.hexdump(tuneRequest.toByteArray()))
     }

    private fun testConfigMessage(message: ByteArray, expectedOutput: String) {
        val response = CardoResponse(message)
        assertEquals(expectedOutput, configDecodedFieldsToString(response.configDecodedFields!!))
    }

    private fun testDeviceStateMessage(message: ByteArray, expectedOutput: String) {
        val response = CardoResponse(message)
        assertEquals(expectedOutput, deviceStateToString(response.deviceState!!))
    }

    private fun testDeviceAliasMessage(message: ByteArray, expectedFriendlyName: String) {
        val response = CardoResponse(message)
        assertEquals(expectedFriendlyName, response.friendlyName)
    }

    private fun testDeviceSerialNumberMessage(message: ByteArray, expectedSerialNumber: String) {
        val response = CardoResponse(message)
        assertEquals(expectedSerialNumber, response.serialNumber)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
