package nodomain.freeyourgadget.gadgetbridge.service.devices.viatom

import android.bluetooth.BluetoothGattCharacteristic
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.junit.Assert
import org.junit.Test
import java.util.UUID

class F8ScaleTest : TestBase() {
    var support: F8Support = createSupport()

    fun decodeEncode(origString: String, unit: F8Support.WeightUnit) {
        val orig = GB.hexStringToByteArray(origString)
        val decoded = support.processPacket(orig)
        println(decoded)

        val encoded = support.encodeTest(decoded, F8Support.ScaleConfig(10.0, 10.0, unit), orig[0].toInt())

        println("original:" + GB.hexdump(orig))
        println("encoded:" + GB.hexdump(encoded[0]))
        Assert.assertArrayEquals(orig, encoded[0])

    }

    @Test
    fun userDataTestB1() {
        decodeEncode("010f00b169650ba7003c01b31770ae0f13880000", F8Support.WeightUnit.KG)
        decodeEncode("010F00B169765C97003C00B31964AF011B580032", F8Support.WeightUnit.LB)
        decodeEncode("060f00b169650bc3003c01b31770ae0f1388005c", F8Support.WeightUnit.ST_LB)
        decodeEncode("070f00b169650bc9003c01b31770ae0f13880002", F8Support.WeightUnit.KG)

        decodeEncode("000F00B16AB64CD2FED401AF02BC9E05028A001E", F8Support.WeightUnit.KG) //synthetic packet with negative time zone
    }

    private fun createSupport(): F8Support {
        return object : F8Support() {
            override fun getCharacteristic(uuid: UUID?): BluetoothGattCharacteristic? {
                return BluetoothGattCharacteristic(null, 0, 0)
            }
        }
    }
}
