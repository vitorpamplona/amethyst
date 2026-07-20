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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordCommunityStateTest {
    private val owner = "0f".repeat(32)
    private val alice = "a1".repeat(32)
    private val adminRole = "11".repeat(32)

    private fun edition(
        kind: ControlEntityKind,
        eid: String,
        content: String,
        author: String = owner,
    ) = ControlEdition(kind, eid.hexToByteArray(), 0, null, null, content, author, "r-$eid", 0)

    @Test
    fun foldsMetadataChannelsRolesAndAuthority() {
        val editions =
            listOf(
                edition(ControlEntityKind.METADATA, "00".repeat(32), """{"name":"My Server","description":"hi"}"""),
                edition(ControlEntityKind.CHANNEL, "c1".repeat(32), """{"name":"general","private":false}"""),
                edition(ControlEntityKind.CHANNEL, "c2".repeat(32), """{"name":"voice-lounge","private":false,"voice":true}"""),
                edition(ControlEntityKind.CHANNEL, "c3".repeat(32), """{"name":"old","deleted":true}"""),
                edition(ControlEntityKind.ROLE, adminRole, """{"name":"Admin","position":1,"permissions":"25"}"""),
                edition(ControlEntityKind.GRANT, "ab".repeat(32), """{"member":"$alice","role_ids":["$adminRole"]}"""),
            )

        val state = ConcordCommunityState.fold(editions, owner)

        assertEquals("My Server", state.metadata?.name)
        assertEquals("hi", state.metadata?.description)

        // deleted channel excluded; general + voice channel kept
        assertEquals(2, state.channels.size)
        assertEquals("general", state.channels["c1".repeat(32)]?.definition?.name)
        assertTrue(state.channels["c2".repeat(32)]?.definition?.voice == true)
        assertNull(state.channels["c3".repeat(32)])

        assertNotNull(state.roles[adminRole])
        assertEquals(1L, state.authority.rank(alice))
        assertFalse(state.dissolved)
    }

    /**
     * The per-kind gated folds share the shape that poisoned GRANT chains live: gating used
     * to pre-filter the chain, so one unauthorized edition in the middle of a CHANNEL (or
     * METADATA) chain orphaned every authorized edition above it — the channel frozen at its
     * pre-attack name, permanently, with no way for the owner to rename or delete it.
     */
    @Test
    fun anUnauthorizedChannelOrMetadataEditionMidChainDoesNotOrphanTheEditionsAboveIt() {
        val chan = "c1".repeat(32)
        val meta = "00".repeat(32)
        val troll = "77".repeat(32) // holds no roles at all

        fun chained(
            kind: ControlEntityKind,
            eid: String,
            version: Long,
            prev: ControlEdition?,
            content: String,
            author: String,
        ) = ControlEdition(kind, eid.hexToByteArray(), version, prev?.hash, null, content, author, "r-$eid-$version", version)

        val c0 = chained(ControlEntityKind.CHANNEL, chan, 0, null, """{"name":"general"}""", owner)
        val c1 = chained(ControlEntityKind.CHANNEL, chan, 1, c0, """{"name":"HACKED"}""", troll)
        val c2 = chained(ControlEntityKind.CHANNEL, chan, 2, c1, """{"name":"renamed"}""", owner)

        val m0 = chained(ControlEntityKind.METADATA, meta, 0, null, """{"name":"My Server"}""", owner)
        val m1 = chained(ControlEntityKind.METADATA, meta, 1, m0, """{"name":"HACKED"}""", troll)
        val m2 = chained(ControlEntityKind.METADATA, meta, 2, m1, """{"name":"Renamed Server"}""", owner)

        val state = ConcordCommunityState.fold(listOf(c0, c1, c2, m0, m1, m2), owner)

        assertEquals("renamed", state.channels[chan]?.definition?.name, "the owner's v2 rename must apply")
        assertEquals("Renamed Server", state.metadata?.name, "the owner's v2 metadata edit must apply")
    }

    @Test
    fun dissolutionTombstoneMarksCommunityDissolved() {
        val editions =
            listOf(
                edition(ControlEntityKind.METADATA, "00".repeat(32), """{"name":"Doomed"}"""),
                edition(ControlEntityKind.DISSOLVED, "dd".repeat(32), """{}"""),
            )
        val state = ConcordCommunityState.fold(editions, owner)
        assertTrue(state.dissolved)
    }
}
