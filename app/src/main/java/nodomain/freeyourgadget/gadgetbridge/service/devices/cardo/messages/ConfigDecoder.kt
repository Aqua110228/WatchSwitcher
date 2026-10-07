package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.messages

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.BitReader
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.CardoField
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol.InfoType
import org.jetbrains.annotations.TestOnly

data class DecodedField(val name: String, val value: Any?)

fun decodeConfigMessage(payload: ByteArray): List<DecodedField> {
    val result = mutableListOf<DecodedField>()
    var index = 0
    while (index < payload.size) {
        val infoType = InfoType.fromByte(payload[index])
        val length = infoType.payloadLength
        check(index + 1 + length <= payload.size) {
            "Incomplete payload for infoType: ${infoType.typeId}"
        }
        val infoPayload = payload.copyOfRange(index + 1, index + 1 + length)
        val reader = BitReader(infoPayload)
        for (field in infoType.fields) {
            @Suppress("UNCHECKED_CAST")
            val value = reader.read(field as CardoField<Any?>)
            result += DecodedField(field.name, value)
        }
        index += 1 + length
    }
    return result
}

@TestOnly
fun configDecodedFieldsToString(fields: List<DecodedField>): String =
    fields.filterNot { it.name.startsWith("skip") }
        .joinToString(separator = "") { "${it.name} - ${it.value}${System.lineSeparator()}" }
