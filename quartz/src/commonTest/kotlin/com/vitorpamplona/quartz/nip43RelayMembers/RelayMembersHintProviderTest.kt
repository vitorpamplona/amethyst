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
package com.vitorpamplona.quartz.nip43RelayMembers

import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelayMembersHintProviderTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val c = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"

    private fun add(
        vararg tags: Array<String>,
        content: String = "",
    ) = RelayAddMemberEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun remove(
        vararg tags: Array<String>,
        content: String = "",
    ) = RelayRemoveMemberEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun list(
        vararg tags: Array<String>,
        content: String = "",
    ) = RelayMembershipListEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun role(
        vararg tags: Array<String>,
        content: String = "",
    ) = RelayRoleEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun rejoin(event: SearchableEvent): String {
        val fields = mutableListOf<String>()
        event.forEachIndexableField {
            if (it != null) fields.add(it)
            true
        }
        return fields.joinToString(event.indexableSeparator())
    }

    @Test
    fun addAndRemoveExposeMemberKeys() {
        val added = add(arrayOf("-"), arrayOf("p", b), arrayOf("p", c, relay), arrayOf("p", "short"))
        assertEquals(listOf(b, c), added.linkedPubKeys())
        assertEquals(listOf(c), added.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(relay), added.pubKeyHints().map { it.relay.url })

        val removed = remove(arrayOf("-"), arrayOf("p", b))
        assertEquals(listOf(b), removed.linkedPubKeys())
        assertTrue(removed.pubKeyHints().isEmpty())
    }

    @Test
    fun membershipListLinksMembersWithoutHints() {
        val event = list(arrayOf("-"), arrayOf("member", b, "admin"), arrayOf("member", c), arrayOf("member", "bad"))
        assertEquals(listOf(b, c), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
    }

    @Test
    fun roleIndexesLabelAndDescription() {
        val event =
            role(
                arrayOf("-"),
                arrayOf("d", "mod"),
                arrayOf("label", "Moderator"),
                arrayOf("description", "Keeps the relay clean"),
                arrayOf("color", "120"),
            )
        assertEquals("Moderator\nKeeps the relay clean", event.indexableContent())
        assertEquals(event.indexableContent(), rejoin(event))

        val bare = role(arrayOf("d", "mod"))
        assertEquals("", bare.indexableContent())
        assertEquals("", rejoin(bare))
    }
}
