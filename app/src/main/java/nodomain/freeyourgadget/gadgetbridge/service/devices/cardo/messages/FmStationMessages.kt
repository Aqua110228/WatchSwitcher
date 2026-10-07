package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

data class FmStationList(val scanStatus: Int, val frequencies: List<Int>)

fun decodeFmStationList(decodedFields: List<DecodedField>): FmStationList {
    val byName = decodedFields.associate { it.name to it.value }
    val scanStatus = byName["stat"] as? Int ?: 0
    val frequencies = (1..6).map { i -> byName["stat$i"] as? Int ?: 0 }
    return FmStationList(scanStatus, frequencies)
}
