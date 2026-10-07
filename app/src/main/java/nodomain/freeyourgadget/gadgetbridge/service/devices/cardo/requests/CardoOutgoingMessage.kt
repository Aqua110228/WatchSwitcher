package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.requests

sealed interface CardoOutgoingMessage {
    fun toByteArray(): ByteArray
}

private fun buildMessage(command: Byte, hasLength: Boolean, payload: ByteArray): ByteArray {
    require(payload.size <= 0xFF) {
        "Payload too long: ${payload.size} byte"
    }
    val header = if (hasLength) byteArrayOf(command, payload.size.toByte()) else byteArrayOf(command)
    return header + payload
}


data class InitRequest(val payload: ByteArray = byteArrayOf(0, 0)) : CardoOutgoingMessage {
    override fun toByteArray() = buildMessage(command = 0x03, hasLength = false, payload = payload)
}

data class GetRequest(val infoTypeIds: List<Int>) : CardoOutgoingMessage {
    override fun toByteArray(): ByteArray {
        val payload = infoTypeIds.map { it.toByte() }.toByteArray()
        return buildMessage(command = 0x00, hasLength = true, payload = payload)
    }
}

data class SubscribeRequest(val servicesBitmask: Int, val specificServicesBitmask: Int) : CardoOutgoingMessage {
    override fun toByteArray(): ByteArray {
        val payload = byteArrayOf(
            servicesBitmask.toByte(),
            ((specificServicesBitmask shr 8) and 0xFF).toByte(),
            (specificServicesBitmask and 0xFF).toByte()
        )
        return buildMessage(command = 0x22, hasLength = false, payload = payload)
    }
}

data class SetRequest(val infoTypeId: Int, val fieldPayload: ByteArray) : CardoOutgoingMessage {
    override fun toByteArray(): ByteArray {
        val payload = byteArrayOf(infoTypeId.toByte()) + fieldPayload
        return buildMessage(command = 0x10, hasLength = true, payload = payload)
    }
}

data class FactoryResetRequest(val payload: ByteArray = byteArrayOf(0x05, 0x55)) : CardoOutgoingMessage {
    override fun toByteArray() = buildMessage(command = 0x30, hasLength = false, payload = payload)
}

enum class ControlSubset(val subsetId: Int) {
    FM(0x20)
}

data class ControlRequest(val subset: ControlSubset, val subPayload: ByteArray) : CardoOutgoingMessage {
    override fun toByteArray(): ByteArray {
        val payload = byteArrayOf(subset.subsetId.toByte()) + subPayload
        return buildMessage(command = 0x30, hasLength = false, payload = payload)
    }
}