package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BitWriter
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.CardoField
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.InfoType

fun buildSetRequests(
    fieldName: String,
    newValue: Any,
    currentValues: Map<String, Any>
): List<SetRequest> {
    val overrides = buildMap {
        put(fieldName, newValue)
        if (fieldName == "ag1Volume") put("ag2Volume", newValue)
        if (fieldName == "a2dp1Volume") put("a2dp2Volume", newValue)
    }

    return InfoType.entries
        .filter { it.containsFieldWithName(fieldName) }
        .map { infoType ->
            val writer = BitWriter(infoType.payloadLength)
            for (field in infoType.fields) {
                @Suppress("UNCHECKED_CAST")
                val f = field as CardoField<Any>
                val value = overrides[field.name] ?: currentValues[field.name]
                ?: error("No current value for '${field.name}' (InfoType ${infoType.typeId})")
                writer.write(f, value)
            }
            SetRequest(infoType.typeId, writer.toByteArray())
        }
}