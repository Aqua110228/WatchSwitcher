/*  Copyright (C) 2026 Toby Murray

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.una

import nodomain.freeyourgadget.gadgetbridge.devices.una.UnaConstants
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions

data class UnaAttributeRequest(val attributeId: Int, val maxLength: Int)

sealed interface UnaCansCommand {
    data class RequestAttributes(val uid: Int, val requested: List<UnaAttributeRequest>) : UnaCansCommand
    data class ExecuteAction(val uid: Int, val positive: Boolean) : UnaCansCommand
}

/**
 * Wire encoding for CANS. No BLE or Android dependencies, so it is testable directly against
 * captured bytes.
 */
object UnaCansProtocol {
    private const val EVENT_SIZE = 7
    private const val RESPONSE_HEADER_SIZE = 5
    private const val REQUEST_HEADER_SIZE = 5
    private const val REQUEST_ENTRY_SIZE = 3

    /** 01 <action> <uid:u32LE> <category>. */
    fun buildEvent(uid: Int, action: Int, category: Int): ByteArray =
        byteArrayOf(UnaConstants.NOTIFICATION_EVENT.toByte(), action.toByte()) +
            BLETypeConversions.fromUint32(uid) +
            byteArrayOf(category.toByte())

    /** 03 <uid:u32LE> then <attributeId> <length:u16LE> <value> per attribute. */
    fun buildAttributeResponse(uid: Int, attributes: List<Pair<Int, ByteArray>>): ByteArray {
        var out = byteArrayOf(UnaConstants.SERVER_REQUEST_ATTRIBUTES.toByte()) +
            BLETypeConversions.fromUint32(uid)
        for ((attributeId, value) in attributes) {
            out += byteArrayOf(attributeId.toByte()) +
                BLETypeConversions.fromUint16(value.size) +
                value
        }
        return out
    }

    /** A bare error code, with no uid to say which request it refuses. */
    fun buildErrorResponse(errorCode: Int): ByteArray = byteArrayOf(errorCode.toByte())

    /** Fragments carry no header of their own, so order is all that holds a response together. */
    fun fragment(payload: ByteArray, maxPacketSize: Int): List<ByteArray> {
        if (maxPacketSize <= 0) return listOf(payload)
        return payload.asList().chunked(maxPacketSize) { it.toByteArray() }
    }

    /** Parses a command from the watch, or null if it is not one this understands. */
    fun parseCommand(data: ByteArray): UnaCansCommand? {
        if (data.size < REQUEST_HEADER_SIZE) return null
        val uid = BLETypeConversions.toUint32(data, 1)
        return when (data[0].toInt() and 0xFF) {
            UnaConstants.SERVER_REQUEST_ATTRIBUTES -> {
                val requested = mutableListOf<UnaAttributeRequest>()
                var offset = REQUEST_HEADER_SIZE
                while (offset + REQUEST_ENTRY_SIZE <= data.size) {
                    requested.add(
                        UnaAttributeRequest(
                            attributeId = data[offset].toInt() and 0xFF,
                            maxLength = BLETypeConversions.toUint16(data, offset + 1),
                        )
                    )
                    offset += REQUEST_ENTRY_SIZE
                }
                UnaCansCommand.RequestAttributes(uid, requested)
            }
            UnaConstants.SERVER_EXECUTE_POSITIVE_ACTION -> UnaCansCommand.ExecuteAction(uid, true)
            UnaConstants.SERVER_EXECUTE_NEGATIVE_ACTION -> UnaCansCommand.ExecuteAction(uid, false)
            else -> null
        }
    }
}
