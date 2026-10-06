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
package com.vitorpamplona.quartz.nip29RelayGroups.moderation

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupModerationHintProviderTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val third = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventId2 = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val addressId = "30023:$other:my-article"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun putUserLinksTheUserWithoutTakingRolesForRelays() {
        val put = GroupPutUserEvent(id, pk, 1L, arrayOf(arrayOf("h", "g"), arrayOf("p", other, "admin"), arrayOf("p", "g".repeat(64))), "", sig)
        assertEquals(listOf(other), put.linkedPubKeys())
        assertTrue(put.pubKeyHints().isEmpty())
    }

    @Test
    fun removeUserLinksTheUsers() {
        val notHex = "g".repeat(64)
        val remove =
            GroupRemoveUserEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("h", "g"), arrayOf("p", other), arrayOf("p", third, relay), arrayOf("p", notHex, relay)),
                "",
                sig,
            )
        // 64 chars is not enough: a key the provider hands over must be hex.
        assertEquals(listOf(other, third), remove.linkedPubKeys())
        assertEquals(listOf(third), remove.pubKeyHints().map { it.pubkey })
    }

    @Test
    fun deleteEventLinksTheDeletedEvents() {
        val delete = GroupDeleteEventEvent(id, pk, 1L, arrayOf(arrayOf("h", "g"), arrayOf("e", eventId)), "", sig)
        assertEquals(listOf(eventId), delete.linkedEventIds())
        assertTrue(delete.eventHints().isEmpty())
    }

    @Test
    fun updatePinListLinksThePins() {
        val update =
            GroupUpdatePinListEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("h", "g"), arrayOf("e", eventId, relay), arrayOf("a", addressId, relay)),
                "",
                sig,
            )

        assertEquals(listOf(eventId), update.linkedEventIds())
        assertEquals(listOf(eventId), update.eventHints().map { it.eventId })
        assertEquals(listOf(addressId), update.linkedAddressIds())
        assertEquals(listOf(relay), update.addressHints().map { it.relay.url })
    }

    @Test
    fun createGroupIsSearchableByNameAndAbout() {
        val create =
            CreateGroupEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("h", "g"), arrayOf("name", "Book Club"), arrayOf("about", "We read"), arrayOf("visibility", "open")),
                "",
                sig,
            )

        assertEquals("Book Club\nWe read", create.indexableContent())
        assertEquals(create.indexableContent(), create.rejoined())

        val fields = SearchFieldExtractor.extract(create) as IndexableFields.Tiered
        assertEquals(listOf("Book Club"), fields.primary)
        assertEquals(listOf("We read"), fields.secondary)
    }
}

private fun SearchableEvent.rejoined(): String =
    buildList {
        forEachIndexableField { f ->
            if (f != null) add(f)
            true
        }
    }.joinToString(indexableSeparator())
