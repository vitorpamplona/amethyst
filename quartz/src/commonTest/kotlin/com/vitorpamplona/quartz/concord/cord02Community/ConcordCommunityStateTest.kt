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
import com.vitorpamplona.quartz.concord.cord04Roles.ControlFixtures
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
                edition(ControlEntityKind.METADATA, ControlFixtures.COMMUNITY_ID_HEX, """{"name":"My Server","description":"hi"}"""),
                edition(ControlEntityKind.CHANNEL, "c1".repeat(32), """{"name":"general","private":false}"""),
                edition(ControlEntityKind.CHANNEL, "c2".repeat(32), """{"name":"voice-lounge","private":false,"voice":true}"""),
                edition(ControlEntityKind.CHANNEL, "c3".repeat(32), """{"name":"old","deleted":true}"""),
                edition(ControlEntityKind.ROLE, adminRole, """{"name":"Admin","position":1,"permissions":"25"}"""),
                edition(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), """{"member":"$alice","role_ids":["$adminRole"]}"""),
            )

        val state = ControlFixtures.fold(editions, owner)

        assertEquals("My Server", state.metadata?.name)
        assertEquals("hi", state.metadata?.description)

        // deleted channel excluded; the other two kept (a legacy `voice` key is just an unknown field)
        assertEquals(2, state.channels.size)
        assertEquals("general", state.channels["c1".repeat(32)]?.definition?.name)
        assertEquals("voice-lounge", state.channels["c2".repeat(32)]?.definition?.name)
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
        val meta = ControlFixtures.COMMUNITY_ID_HEX
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

        val state = ControlFixtures.fold(listOf(c0, c1, c2, m0, m1, m2), owner)

        assertEquals("renamed", state.channels[chan]?.definition?.name, "the owner's v2 rename must apply")
        assertEquals("Renamed Server", state.metadata?.name, "the owner's v2 metadata edit must apply")
    }

    private fun chainedMeta(
        eid: String,
        version: Long,
        prev: ControlEdition?,
        content: String,
        author: String = owner,
        rumorId: String = "m-$eid-$version-$author",
    ) = ControlEdition(ControlEntityKind.METADATA, eid.hexToByteArray(), version, prev?.hash, null, content, author, rumorId, version)

    @Test
    fun metadataAtAnyCoordinateButTheCommunityIdIsIgnored() {
        // CORD-04 §1: the metadata entity's eid IS the community_id. A fresh chain minted at any
        // other coordinate — even owner-signed, even at a far higher version — is not this
        // community's metadata, so it can neither shadow the real chain nor bypass it (S8).
        val real = chainedMeta(ControlFixtures.COMMUNITY_ID_HEX, 1, null, """{"name":"Real"}""")
        val decoy = chainedMeta("99".repeat(32), 50, null, """{"name":"Decoy"}""")
        assertEquals("Real", ControlFixtures.fold(listOf(real, decoy), owner).metadata?.name)
        assertNull(ControlFixtures.fold(listOf(decoy), owner).metadata, "a decoy alone is no metadata at all")
    }

    @Test
    fun metadataPastTheNameOrDescriptionCapFallsBackToThePreviousEdition() {
        // CORD-02 §6 caps are fold gates, as in the reference client: an edition past them is
        // dropped and the entity keeps the edition below it.
        val cid = ControlFixtures.COMMUNITY_ID_HEX
        val v1 = chainedMeta(cid, 1, null, """{"name":"Fine"}""")
        val longName = chainedMeta(cid, 2, v1, """{"name":"${"n".repeat(65)}"}""")
        assertEquals("Fine", ControlFixtures.fold(listOf(v1, longName), owner).metadata?.name)

        // 64 bytes of UTF-8 is the cap, not 64 characters: 33 two-byte characters is 66 bytes.
        val wideName = chainedMeta(cid, 2, v1, """{"name":"${"é".repeat(33)}"}""")
        assertEquals("Fine", ControlFixtures.fold(listOf(v1, wideName), owner).metadata?.name)

        val longDescription = chainedMeta(cid, 2, v1, """{"name":"Fine2","description":"${"d".repeat(10_001)}"}""")
        assertEquals("Fine", ControlFixtures.fold(listOf(v1, longDescription), owner).metadata?.name)

        val atTheCaps = chainedMeta(cid, 2, v1, """{"name":"${"n".repeat(64)}","description":"${"d".repeat(10_000)}"}""")
        assertEquals("n".repeat(64), ControlFixtures.fold(listOf(v1, atTheCaps), owner).metadata?.name)
    }

    @Test
    fun anEqualVersionMetadataForkGoesToTheHigherAuthorityNotTheLowerRumorId() {
        // CORD-04 §1: "authority first, then the lower rumor id". Alice holds MANAGE_METADATA and
        // grinds a rumor id below the owner's at the same version; the owner still wins.
        val cid = ControlFixtures.COMMUNITY_ID_HEX
        val v1 = chainedMeta(cid, 1, null, """{"name":"Genesis"}""")
        val ownerV2 = chainedMeta(cid, 2, v1, """{"name":"Owner's"}""", rumorId = "ffff")
        val aliceV2 = chainedMeta(cid, 2, v1, """{"name":"Alice's"}""", author = alice, rumorId = "0000")
        val editions =
            listOf(
                edition(ControlEntityKind.ROLE, adminRole, """{"name":"Admin","position":1,"permissions":"4"}"""), // MANAGE_METADATA
                edition(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), """{"member":"$alice","role_ids":["$adminRole"]}"""),
                v1,
                aliceV2,
                ownerV2,
            )
        assertEquals("Owner's", ControlFixtures.fold(editions, owner).metadata?.name)

        // And an edition chained onto the owner's sibling extends the chain from there.
        val v3 = chainedMeta(cid, 3, ownerV2, """{"name":"Next"}""", author = alice)
        assertEquals("Next", ControlFixtures.fold(editions + v3, owner).metadata?.name)
    }

    @Test
    fun aControlPlaneVsk10EditionDoesNotDissolve() {
        // CORD-02 §9: the tombstone lives at `dissolved_pk` and must name its community. A vsk-10
        // edition on the Control Plane skips that binding, so it must never seal the community.
        val editions =
            listOf(
                edition(ControlEntityKind.METADATA, ControlFixtures.COMMUNITY_ID_HEX, """{"name":"Doomed"}"""),
                edition(ControlEntityKind.DISSOLVED, "dd".repeat(32), """{}"""),
            )
        val state = ControlFixtures.fold(editions, owner)
        assertFalse(state.dissolved)
        assertTrue(state.withDissolved(true).dissolved)
    }
}
