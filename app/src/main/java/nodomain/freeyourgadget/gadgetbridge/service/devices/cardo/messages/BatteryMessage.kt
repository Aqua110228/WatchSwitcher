package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BitReader
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BoolField
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.IntField

data class BatteryStatus(val isCharging: Boolean, val chargePercent: Int)

private val chargeStatusField = BoolField("charging")
private val batteryChargeField = IntField("batteryCharge", 7)

fun decodeBatteryStatus(payload: ByteArray): BatteryStatus {
    val reader = BitReader(payload)
    val isCharging = reader.read(chargeStatusField)
    val chargePercent = reader.read(batteryChargeField)
    return BatteryStatus(isCharging, chargePercent)
}

