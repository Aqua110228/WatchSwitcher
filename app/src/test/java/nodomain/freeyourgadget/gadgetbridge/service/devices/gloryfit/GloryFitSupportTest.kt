package nodomain.freeyourgadget.gadgetbridge.service.devices.gloryfit

import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class GloryFitSupportTest : TestBase() {
    @Test
    fun testEncodeTemperature() {
        assertEquals(0x00.toByte(), GloryFitSupport.encodeTemperature(0 + 273))
        assertEquals(0x7f.toByte(), GloryFitSupport.encodeTemperature(127 + 273))
        assertEquals(20.toByte(), GloryFitSupport.encodeTemperature(20 + 273))
        assertEquals(0x81.toByte(), GloryFitSupport.encodeTemperature(-1 + 273))
        assertEquals(0xff.toByte(), GloryFitSupport.encodeTemperature(-127 + 273))
    }
}
