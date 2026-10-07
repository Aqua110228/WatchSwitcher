package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoBackgroundVolume
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoEqualizerProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoFmRegion
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoLanguage
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BitReader
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BitWriter
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BoolField
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.InfoType
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.IntField
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.backgroundVolumeField
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.decodeConfigMessage
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.enumField
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertThrows
import org.junit.Test



fun bytesOf(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

class ConfigSchemaTest: TestBase() {

    @Test
    fun `sensitivity field roundtrip matches real device capture`() {
        val fullPayload = bytesOf(0x81, 0x0, 0x32, 0x0, 0x0, 0x82, 0xfa, 0x62, 0x87, 0x1, 0x0, 0x0)

        val decoded = decodeConfigMessage(fullPayload)

        assertEquals(50, decoded.single { it.name == "sensitivity" }.value)
    }

    @Test
    fun `features bitfield decodes all 15 flags correctly`() {
        val fullPayload = bytesOf(0x81, 0x0, 0x32, 0x0, 0x0, 0x82, 0xfa, 0x62, 0x87, 0x1, 0x0, 0x0)
        val decoded = decodeConfigMessage(fullPayload).associate { it.name to it.value }

        assertEquals(true, decoded["isAutomaticVolumeAvailable"])
        assertEquals(true, decoded["isMusicSharingAvailable"])
        assertEquals(true, decoded["isBluetoothICAvailable"])
        assertEquals(true, decoded["isHFPMixingAvailable"])
        assertEquals(true, decoded["isASRAvailable"])
        assertEquals(false, decoded["isDMCAvailable"])
        assertEquals(2, decoded["numberOfSupportedICChannels"])
        assertEquals(false, decoded["isDynamicICAvailable"])
        assertEquals(true, decoded["isOTAAvailable"])
        assertEquals(true, decoded["isFMAvailable"])
        assertEquals(false, decoded["isPrivateChatAvailable"])
        assertEquals(false, decoded["isEcoModeAvailable"])
        assertEquals(false, decoded["isMobileBridgeAvailable"])
        assertEquals(true, decoded["isLRSpeakersAvailable"])
        assertEquals(false, decoded["isICDMCBridgeAvailable"])
        assertEquals(false, decoded["isAutoOnOffAvailable"])
        assertEquals(false, decoded["isCSLNXTVADLicence"])
        assertEquals(true, decoded["isAdvancedMMIAvailable"])
    }

    @Test
    fun `volumes and firmware decode with correct field values`() {
        val fullPayload = bytesOf(
            0x3, 0x0, 0x0, 0x0, 0x3, 0x0, 0x2,
            0x2, 0x0, 0xa8, 0xaa, 0xb9, 0x98, 0x4,
            0x6, 0x0, 0x0, 0x0
        )
        val decoded = decodeConfigMessage(fullPayload).associate { it.name to it.value }

        assertEquals(0, decoded["volumeID"])
        assertEquals(10, decoded["standByVolume"])
        assertEquals(8, decoded["groupingVolume"])
        assertEquals(10, decoded["ag1Volume"])
        assertEquals(10, decoded["ag2Volume"])
        assertEquals(11, decoded["fmVolume"])
        assertEquals(9, decoded["a2dp1Volume"])
        assertEquals(9, decoded["a2dp2Volume"])
        assertEquals(CardoBackgroundVolume.PERCENT_90, decoded["intercomBackgroundMusicVolume"])
        assertEquals(4, decoded["mixActiveSpeakerVolume"])

        assertEquals(0, decoded["headsetType"])
        assertEquals(3, decoded["major"])
        assertEquals(2, decoded["minor"])

        assertEquals(false, decoded["isDMCAGCEnabled"])
        assertEquals(false, decoded["isAdvancedMMIEnabled"])
        assertEquals(false, decoded["isRedialASREnabled"])
        assertEquals(false, decoded["isRadioONASREnabled"])
        assertEquals(false, decoded["isAutoOnOffEnabled"])
    }

    @Test
    fun `language list and equalizer profiles decode as correct enum sets`() {
        val fullPayload = bytesOf(
            0x5, 0xf, 0x7f,
            0x84, 0x0, 0xf, 0x0, 0xf,
            0x83, 0x80
        )
        val decoded = decodeConfigMessage(fullPayload).associate { it.name to it.value }

        @Suppress("UNCHECKED_CAST")
        val languages = decoded["languageList"] as Set<CardoLanguage>
        assertEquals(
            setOf(
                CardoLanguage.ENGLISH_US, CardoLanguage.ENGLISH_UK, CardoLanguage.SPANISH,
                CardoLanguage.FRENCH, CardoLanguage.DEUTSCH, CardoLanguage.JAPANESE,
                CardoLanguage.CHINESE, CardoLanguage.ITALIAN, CardoLanguage.RUSSIAN,
                CardoLanguage.HEBREW, CardoLanguage.PORTUGUESE
            ),
            languages
        )

        @Suppress("UNCHECKED_CAST")
        val equalizers = decoded["equalizer Profiles"] as Set<CardoEqualizerProfile>
        assertEquals(
            setOf(
                CardoEqualizerProfile.HIGH_VOLUME, CardoEqualizerProfile.BASS_BOOST,
                CardoEqualizerProfile.VOCAL, CardoEqualizerProfile.OFF,
                CardoEqualizerProfile.JBL_HIGH_VOLUME, CardoEqualizerProfile.JBL_BASS_BOOST,
                CardoEqualizerProfile.JBL_VOCAL, CardoEqualizerProfile.JBL_OFF
            ),
            equalizers
        )

        assertEquals(true, decoded["isAccessoriesActivated"])
    }

    @Test
    fun `background volume uses customIndex not ordinal - regression guard`() {
        val field = backgroundVolumeField("intercomBackgroundMusicVolume", 4)

        assertEquals(9L, field.encode(CardoBackgroundVolume.PERCENT_10))
        assertEquals(CardoBackgroundVolume.PERCENT_10, field.decode(9L))

        assertEquals(0L, field.encode(CardoBackgroundVolume.PERCENT_100))
        assertEquals(CardoBackgroundVolume.PERCENT_100, field.decode(0L))
    }

    @Test
    fun `bit reader and writer roundtrip for boolean field`() {
        val field = BoolField("isVoicePromptsEnabled")
        for (value in listOf(true, false)) {
            val writer = BitWriter(1)
            writer.write(field, value)
            val reader = BitReader(writer.toByteArray())
            assertEquals(value, reader.read(field))
        }
    }

    @Test
    fun `bit reader and writer roundtrip for int field at bit boundaries`() {
        val field = IntField("standByVolume", 4)
        for (value in listOf(0, 15)) {
            val writer = BitWriter(1)
            writer.write(field, value)
            val reader = BitReader(writer.toByteArray())
            assertEquals(value, reader.read(field))
        }
    }

    @Test
    fun `bit reader and writer roundtrip for enum field across full range`() {
        val field = enumField<CardoFmRegion>("fmRegion", 1)
        for (region in CardoFmRegion.values()) {
            val writer = BitWriter(1)
            writer.write(field, region)
            val reader = BitReader(writer.toByteArray())
            assertEquals(region, reader.read(field))
        }
    }

    @Test
    fun `reading beyond buffer bounds throws instead of silently corrupting`() {
        val tooShortPayload = bytesOf(0x0)
        val field = IntField("mixActiveSpeakerVolume", 16)
        val reader = BitReader(tooShortPayload)

        assertThrows(IllegalStateException::class.java) {
            reader.read(field)
        }
    }

    @Test
    fun `unknown info type byte throws with the offending value`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            InfoType.fromByte(0x99.toByte())
        }
        assert(exception.message?.contains("153") == true) {
            "The error message should mention the unhandled value (0x99=153), found: ${exception.message}"
        }
    }

    @Test
    fun `no InfoType declares fields exceeding its payloadLength`() {
        for (infoType in InfoType.entries) {
            val totalBits = infoType.fields.sumOf { it.bitSize }
            val availableBits = infoType.payloadLength * 8
            assertTrue(
                "InfoType typeId=${infoType.typeId} (0x${infoType.typeId.toString(16)}): " +
                        "the declared fields require $totalBits bits, but payloadLength=${infoType.payloadLength} " +
                        "bytes only offers $availableBits." +
                        "This would cause a read past the bytes allocated to this InfoType.",
                totalBits <= availableBits
            )
        }
    }

    @Test
    fun `known partially-mapped InfoTypes are exactly the documented ones`() {
        val partiallyMapped = InfoType.entries
            .filter { it.fields.sumOf { f -> f.bitSize } < it.payloadLength * 8 }
            .map { it.typeId }
            .toSet()

        val expectedPartiallyMapped = setOf(
            InfoType.Hardware.typeId,
            InfoType.ConfigOther.typeId,
            InfoType.Sensitivity.typeId,
            InfoType.AccessoriesActivate.typeId,
            InfoType.Features2.typeId
        )

        assertEquals(
            "Partially mapped fields set has changed, update the test",
            expectedPartiallyMapped,
            partiallyMapped
        )
    }

    @Test
    fun `no duplicate typeId across InfoType entries`() {
        val typeIds = InfoType.entries.map { it.typeId }
        assertEquals(typeIds.size, typeIds.toSet().size)
    }
}
