package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoEqualizerProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoMicrophoneSensitivity
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.decodeConfigMessage
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigAudioModeTest: TestBase() {

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun configPayload(vararg values: Int) = bytes(0x00, *values)

    @Test
    fun `equalizer profile cycles through JBL variants - real capture`() {
        val expected = mapOf(
            configPayload(0x0c, 0x21, 0x13) to CardoEqualizerProfile.JBL_OFF,
            configPayload(0x0c, 0x21, 0x11) to CardoEqualizerProfile.JBL_BASS_BOOST,
            configPayload(0x0c, 0x21, 0x10) to CardoEqualizerProfile.JBL_HIGH_VOLUME,
            configPayload(0x0c, 0x21, 0x12) to CardoEqualizerProfile.JBL_VOCAL,
        )
        for ((payload, profile) in expected) {
            val decoded = decodeConfigMessage(payload)
            assertEquals(profile, decoded.single { it.name == "equalizerProfile" }.value)
        }
    }

    @Test
    fun `agc sensitivity changes independently of equalizer profile - real capture`() {
        val expected = mapOf(
            configPayload(0x0c, 0x25, 0x11) to 2,
            configPayload(0x0c, 0x27, 0x11) to 3,
            configPayload(0x0c, 0x23, 0x11) to 1,
        )
        for ((payload, agc) in expected) {
            val decoded = decodeConfigMessage(payload)
            assertEquals(agc, decoded.single { it.name == "agcSensitivity" }.value)
            assertEquals(CardoEqualizerProfile.JBL_BASS_BOOST, decoded.single { it.name == "equalizerProfile" }.value)
        }
    }

    @Test
    fun `microphone sensitivity cycles through all three levels - real capture`() {
        val expected = mapOf(
            configPayload(0x0c, 0x45, 0x11) to CardoMicrophoneSensitivity.HIGH,
            configPayload(0x0c, 0x05, 0x11) to CardoMicrophoneSensitivity.LOW,
            configPayload(0x0c, 0x25, 0x11) to CardoMicrophoneSensitivity.MEDIUM,
        )
        for ((payload, sensitivity) in expected) {
            val decoded = decodeConfigMessage(payload)
            assertEquals(sensitivity, decoded.single { it.name == "microphoneSensitivity" }.value)
        }
    }

    @Test
    fun `isASREnable toggles off while other fields remain stable - real capture`() {
        val decoded = decodeConfigMessage(configPayload(0x04, 0x25, 0x11))
        assertEquals(false, decoded.single { it.name == "isASREnable" }.value)
        assertEquals(CardoEqualizerProfile.JBL_BASS_BOOST, decoded.single { it.name == "equalizerProfile" }.value)
        assertEquals(2, decoded.single { it.name == "agcSensitivity" }.value)
    }
}
