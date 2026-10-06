/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip10Notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseThreadedEventAccessorsTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val third = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventId2 = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val addressId = "30023:$other:my-article"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private fun note(vararg tags: Array<String>) = TextNoteEvent(id, pk, 1L, arrayOf(*tags), "hi", sig)

    @Test
    fun mentionKeysAreTheKeysOfMentions() {
        val note = note(arrayOf("p", other, relay), arrayOf("p", "short"), arrayOf("p", third))

        assertEquals(listOf(other, third), note.mentionKeys())
        assertEquals(note.mentions().map { it.pubKey }, note.mentionKeys())
    }

    @Test
    fun quotesSplitIntoEventsAndAddresses() {
        val note =
            note(
                arrayOf("q", eventId, relay, other),
                arrayOf("q", addressId, relay),
                arrayOf("q", "not-an-id"),
                arrayOf("e", eventId2),
            )

        assertEquals(listOf(eventId), note.quotedEvents().map { it.eventId })
        assertEquals(other, note.quotedEvents().single().author)
        assertEquals(listOf(addressId), note.quotedAddresses().map { it.address.toValue() })
    }

    @Test
    fun threadEventIdsAreEveryWellFormedEId() {
        val note =
            note(
                arrayOf("e", eventId, relay, "root"),
                arrayOf("e", "short", relay, "reply"),
                arrayOf("e", eventId2, relay, "reply"),
            )

        assertEquals(listOf(eventId, eventId2), note.threadEventIds())
    }

    @Test
    fun threadReferenceIdsSkipForks() {
        val forkedAddress = "30023:$third:forked"
        val note =
            note(
                arrayOf("e", eventId, relay, "root"),
                arrayOf("e", eventId2, relay, "fork"),
                arrayOf("a", addressId, relay),
                arrayOf("a", forkedAddress, relay, "fork"),
                arrayOf("a", "foo:bar"),
            )

        assertEquals(listOf(eventId, addressId), note.threadReferenceIds())
    }

    @Test
    fun referencedAddressesAreEveryWellFormedA() {
        val forkedAddress = "30023:$third:forked"
        val note = note(arrayOf("a", addressId, relay), arrayOf("a", "foo:bar"), arrayOf("a", forkedAddress, relay, "fork"))

        assertEquals(listOf(addressId, forkedAddress), note.referencedAddresses())
    }

    @Test
    fun forkAndNewThreadReadTheNamedAccessors() {
        assertTrue(note(arrayOf("e", eventId, relay, "fork")).isAFork())
        assertTrue(note(arrayOf("a", addressId, relay, "fork")).isAFork())
        assertFalse(note(arrayOf("e", eventId, relay, "root")).isAFork())

        assertTrue(note(arrayOf("p", other)).isNewThread())
        assertFalse(note(arrayOf("e", eventId)).isNewThread())
    }
}
