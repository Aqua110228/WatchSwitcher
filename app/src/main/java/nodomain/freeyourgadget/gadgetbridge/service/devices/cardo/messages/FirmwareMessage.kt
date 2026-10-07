package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

data class FirmwareInfo(val headsetType: Int?, val version: String)

fun decodeFirmwareInfo(decodedFields: List<DecodedField>): FirmwareInfo {
    val byName = decodedFields.associate { it.name to it.value }
    val headsetTypeRaw = byName["headsetType"] as? Int
    val major = byName["major"]
    val minor = byName["minor"]
    return FirmwareInfo(
        headsetType = headsetTypeRaw?.takeIf { it != 0 },
        version = "$major.$minor"
    )
}
