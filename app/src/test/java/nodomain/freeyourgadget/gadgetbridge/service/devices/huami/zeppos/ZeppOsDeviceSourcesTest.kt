package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos

import org.junit.Assert.*
import org.junit.Test

class ZeppOsDeviceSourcesTest {
    @Test
    fun testBalance3DeviceSource() {
        // PNP ID 015701AA000301 and deviceSource from the watch's weather request.
        assertEquals(11141379, ZeppOsDeviceSources.resolve(170, 259)?.deviceSource)
    }

    @Test
    fun testNoDuplicateProductIdAndVersion() {
        // We need all (productId, productVersion) pairs to be distinct
        val pairs = ZeppOsDeviceSources.DEVICE_SOURCES.map { Pair(it.productId, it.productVersion) }
        val uniquePairs = pairs.toSet()
        assertEquals(
            "Found duplicate (productId, productVersion) pairs in DEVICE_SOURCES",
            pairs.size,
            uniquePairs.size
        )
    }
}
