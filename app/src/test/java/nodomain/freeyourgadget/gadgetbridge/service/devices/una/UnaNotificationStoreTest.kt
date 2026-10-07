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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnaNotificationStoreTest {
    private fun request(attributeId: Int, maxLength: Int) = UnaAttributeRequest(attributeId, maxLength)

    @Test
    fun answersEachRoundOfTheWatchsTwoPhaseFetch() {
        val store = UnaNotificationStore()
        store.put(1, mapOf(
            UnaConstants.ATTRIBUTE_TITLE to "Title".toByteArray(),
            UnaConstants.ATTRIBUTE_MESSAGE to "Body".toByteArray(),
        ))
        // Metadata first, then the body separately -- the second must still be answerable.
        val first = store.answer(1, listOf(request(UnaConstants.ATTRIBUTE_TITLE, 255)))!!
        assertArrayEquals("Title".toByteArray(), first.single().second)
        val second = store.answer(1, listOf(request(UnaConstants.ATTRIBUTE_MESSAGE, 2040)))!!
        assertArrayEquals("Body".toByteArray(), second.single().second)
    }

    @Test
    fun truncatesToTheLengthTheWatchAskedFor() {
        val store = UnaNotificationStore()
        store.put(1, mapOf(UnaConstants.ATTRIBUTE_TITLE to "abcdefghij".toByteArray()))
        val answer = store.answer(1, listOf(request(UnaConstants.ATTRIBUTE_TITLE, 4)))!!
        assertArrayEquals("abcd".toByteArray(), answer.single().second)
    }

    @Test
    fun leavesValuesShorterThanTheLimitAlone() {
        val store = UnaNotificationStore()
        store.put(1, mapOf(UnaConstants.ATTRIBUTE_TITLE to "ab".toByteArray()))
        val answer = store.answer(1, listOf(request(UnaConstants.ATTRIBUTE_TITLE, 255)))!!
        assertArrayEquals("ab".toByteArray(), answer.single().second)
    }

    @Test
    fun answersAnAttributeItDoesNotHaveWithAnEmptyValue() {
        // Refusing the whole request over one missing attribute would lose the others.
        val store = UnaNotificationStore()
        store.put(1, mapOf(UnaConstants.ATTRIBUTE_TITLE to "Title".toByteArray()))
        val answer = store.answer(1, listOf(request(UnaConstants.ATTRIBUTE_NEGATIVE_ACTION_LABEL, 16)))!!
        assertEquals(0, answer.single().second.size)
    }

    @Test
    fun forgetsWhatTheWatchHasBeenToldToRemove() {
        val store = UnaNotificationStore()
        store.put(1, mapOf(UnaConstants.ATTRIBUTE_TITLE to "Title".toByteArray()))
        assertTrue(store.contains(1))
        store.remove(1)
        assertFalse(store.contains(1))
        assertNull(store.answer(1, listOf(request(UnaConstants.ATTRIBUTE_TITLE, 255))))
    }

    @Test
    fun evictsTheOldestRatherThanGrowingWithoutBound() {
        val store = UnaNotificationStore(capacity = 2)
        store.put(1, emptyMap())
        store.put(2, emptyMap())
        store.put(3, emptyMap())
        assertEquals(2, store.size)
        assertFalse(store.contains(1))
        assertTrue(store.contains(3))
    }

    @Test
    fun reusingAUidReplacesTheOlderNotification() {
        val store = UnaNotificationStore()
        store.put(1, mapOf(UnaConstants.ATTRIBUTE_TITLE to "First".toByteArray()))
        store.put(1, mapOf(UnaConstants.ATTRIBUTE_TITLE to "Second".toByteArray()))
        assertEquals(1, store.size)
        val answer = store.answer(1, listOf(request(UnaConstants.ATTRIBUTE_TITLE, 255)))!!
        assertArrayEquals("Second".toByteArray(), answer.single().second)
    }
}
