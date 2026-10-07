package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoBackgroundVolume
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages.decodeConfigMessage
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumesMaxMinTest: TestBase() {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun volumesPayload(vararg values: Int) = bytes(0x02, *values)

    @Test
    fun `ag1 and ag2 volumes move from max to min together - real capture`() {
        val atMax = decodeConfigMessage(volumesPayload(0x00, 0x98, 0xff, 0xb7, 0x78, 0x04))
        assertEquals(15, atMax.single { it.name == "ag1Volume" }.value)
        assertEquals(15, atMax.single { it.name == "ag2Volume" }.value)

        val atMin = decodeConfigMessage(volumesPayload(0x00, 0x98, 0x00, 0xb7, 0x78, 0x04))
        assertEquals(0, atMin.single { it.name == "ag1Volume" }.value)
        assertEquals(0, atMin.single { it.name == "ag2Volume" }.value)
    }

    @Test
    fun `a2dp1 and a2dp2 volumes move from max to min together - real capture`() {
        val atMax = decodeConfigMessage(volumesPayload(0x00, 0x98, 0x00, 0xbf, 0xf8, 0x04))
        assertEquals(15, atMax.single { it.name == "a2dp1Volume" }.value)
        assertEquals(15, atMax.single { it.name == "a2dp2Volume" }.value)

        val atMin = decodeConfigMessage(volumesPayload(0x00, 0x98, 0x00, 0xb0, 0x08, 0x04))
        assertEquals(0, atMin.single { it.name == "a2dp1Volume" }.value)
        assertEquals(0, atMin.single { it.name == "a2dp2Volume" }.value)
    }

    @Test
    fun `fm volume moves from max to min independently - real capture`() {
        val atMax = decodeConfigMessage(volumesPayload(0x00, 0x98, 0x00, 0xf0, 0x08, 0x04))
        assertEquals(15, atMax.single { it.name == "fmVolume" }.value)

        val atMin = decodeConfigMessage(volumesPayload(0x00, 0x98, 0x00, 0x00, 0x08, 0x04))
        assertEquals(0, atMin.single { it.name == "fmVolume" }.value)
    }

    @Test
    fun `grouping volume moves from max to min independently - real capture`() {
        val atMax = decodeConfigMessage(volumesPayload(0x00, 0x9f, 0x00, 0x00, 0x08, 0x04))
        assertEquals(15, atMax.single { it.name == "groupingVolume" }.value)

        val atMin = decodeConfigMessage(volumesPayload(0x00, 0x90, 0x00, 0x00, 0x08, 0x04))
        assertEquals(0, atMin.single { it.name == "groupingVolume" }.value)
    }

    @Test
    fun `mix active speaker volume moves from mid to zero then one - real capture`() {
        val atZero = decodeConfigMessage(volumesPayload(0x00, 0x90, 0x00, 0x00, 0x08, 0x00))
        assertEquals(0, atZero.single { it.name == "mixActiveSpeakerVolume" }.value)

        val atOne = decodeConfigMessage(volumesPayload(0x00, 0x90, 0x00, 0x00, 0x08, 0x01))
        assertEquals(1, atOne.single { it.name == "mixActiveSpeakerVolume" }.value)
    }

    @Test
    fun `intercom background music volume cycles through percent values - real capture`() {
        val percent100 = decodeConfigMessage(volumesPayload(0x00, 0x90, 0x00, 0x00, 0x00, 0x01))
        assertEquals(
            CardoBackgroundVolume.PERCENT_100,
            percent100.single { it.name == "intercomBackgroundMusicVolume" }.value
        )

        val percent10 = decodeConfigMessage(volumesPayload(0x00, 0x90, 0x00, 0x00, 0x09, 0x01))
        assertEquals(
            CardoBackgroundVolume.PERCENT_10,
            percent10.single { it.name == "intercomBackgroundMusicVolume" }.value
        )
    }
}
