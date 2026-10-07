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

/** The watch fetches a notification's text in more than one round, so it is held until removed. */
internal class UnaNotificationStore(private val capacity: Int = 32) {
    private val held = LinkedHashMap<Int, Map<Int, ByteArray>>()

    fun put(uid: Int, attributes: Map<Int, ByteArray>) {
        held.remove(uid)
        held[uid] = attributes
        while (held.size > capacity) {
            held.remove(held.keys.first())
        }
    }

    fun remove(uid: Int) {
        held.remove(uid)
    }

    fun contains(uid: Int): Boolean = held.containsKey(uid)

    val size: Int get() = held.size

    /** Values truncated to the lengths asked for, or null if this notification is not held. */
    fun answer(uid: Int, requested: List<UnaAttributeRequest>): List<Pair<Int, ByteArray>>? {
        val attributes = held[uid] ?: return null
        return requested.map { request ->
            val value = attributes[request.attributeId] ?: ByteArray(0)
            val limited = if (request.maxLength in 1 until value.size) {
                value.copyOfRange(0, request.maxLength)
            } else {
                value
            }
            request.attributeId to limited
        }
    }
}
