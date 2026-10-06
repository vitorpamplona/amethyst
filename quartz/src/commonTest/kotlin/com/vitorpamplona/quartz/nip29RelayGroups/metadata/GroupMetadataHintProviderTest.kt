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
package com.vitorpamplona.quartz.nip29RelayGroups.metadata

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupMetadataHintProviderTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val third = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventId2 = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val addressId = "30023:$other:my-article"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    // The member lists come from the group relay itself: linking every member would record that
    // (often private) relay as a hint for each of them, so the lists link no one.

    @Test
    fun adminsAreNotLinkedAndTheirRolesAreNotRelays() {
        val admins =
            GroupAdminsEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("d", "g"), arrayOf("p", other, "admin", "moderator"), arrayOf("p", "short"), arrayOf("p", "g".repeat(64))),
                "",
                sig,
            )

        assertTrue(admins.linkedPubKeys().isEmpty())
        assertTrue(admins.pubKeyHints().isEmpty())
        // The list itself still reads, keeping only real 64-hex keys.
        assertEquals(listOf(other), admins.admins().map { it.pubKey })
        assertEquals(listOf("admin", "moderator"), admins.admins().single().roles)
    }

    @Test
    fun membersAreNotLinked() {
        val members = GroupMembersEvent(id, pk, 1L, arrayOf(arrayOf("d", "g"), arrayOf("p", other), arrayOf("p", third, relay)), "", sig)

        assertTrue(members.linkedPubKeys().isEmpty())
        assertEquals(listOf(third), members.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(other, third), members.members())
    }

    @Test
    fun participantsAreNotLinked() {
        val participants =
            GroupParticipantsEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("d", "g"), arrayOf("participant", other), arrayOf("participant", "bad")),
                "",
                sig,
            )

        assertTrue(participants.linkedPubKeys().isEmpty())
        assertTrue(participants.pubKeyHints().isEmpty())
        assertEquals(listOf(other), participants.participants())
    }

    @Test
    fun pinsAreLinked() {
        val pinned =
            GroupPinnedEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("d", "g"), arrayOf("e", eventId, relay), arrayOf("a", addressId), arrayOf("e", eventId2)),
                "",
                sig,
            )

        assertEquals(listOf(eventId, eventId2), pinned.linkedEventIds())
        assertEquals(listOf(eventId), pinned.eventHints().map { it.eventId })
        assertEquals(listOf(addressId), pinned.linkedAddressIds())
        assertTrue(pinned.addressHints().isEmpty())
    }

    @Test
    fun roleNamesAndDescriptionsAreSearchable() {
        val roles =
            GroupRolesEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("d", "g"), arrayOf("role", "admin", "Runs the group"), arrayOf("role", "member")),
                "",
                sig,
            )

        assertEquals("admin\nRuns the group\nmember", roles.indexableContent())
        assertEquals(roles.indexableContent(), roles.rejoined())

        val fields = SearchFieldExtractor.extract(roles) as IndexableFields.Tiered
        assertEquals(listOf("admin", "Runs the group", "member"), fields.secondary)
    }

    @Test
    fun roleVisitorSkipsBlankSlotsLikeTheJoinDoes() {
        val roles =
            GroupRolesEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("d", "g"), arrayOf("role", ""), arrayOf("role", "mod", ""), arrayOf("t", "x"), arrayOf("role", "admin", "Runs it")),
                "",
                sig,
            )

        assertEquals("mod\nadmin\nRuns it", roles.indexableContent())
        assertEquals(roles.indexableContent(), roles.rejoined())

        // A visitor that stops after the first field is not handed the rest.
        val seen = mutableListOf<String?>()
        roles.forEachIndexableField {
            seen.add(it)
            false
        }
        assertEquals(listOf<String?>("mod"), seen)
    }

    @Test
    fun metadataIndexesItsTopicsLikeTheEditDoes() {
        val metadata =
            GroupMetadataEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("d", "g"), arrayOf("name", "Bikers"), arrayOf("about", "Two wheels"), arrayOf("t", "cycling")),
                "",
                sig,
            )

        assertEquals("Bikers\nTwo wheels\ncycling", metadata.indexableContent())
        assertEquals(metadata.indexableContent(), metadata.rejoined())
    }
}

private fun SearchableEvent.rejoined(): String =
    buildList {
        forEachIndexableField { f ->
            if (f != null) add(f)
            true
        }
    }.joinToString(indexableSeparator())
