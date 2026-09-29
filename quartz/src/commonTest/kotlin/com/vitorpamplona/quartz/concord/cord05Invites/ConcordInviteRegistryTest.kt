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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.ControlFixtures
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CORD-05 §5: the Invite Registry (`vsk 8`) and the Public/Private mode its aggregate defines. */
class ConcordInviteRegistryTest {
    private val owner = "0f".repeat(32)
    private val alice = "a1".repeat(32)
    private val bob = "b2".repeat(32)
    private val troll = "77".repeat(32)
    private val inviterRole = "11".repeat(32)
    private val link1 = "c1".repeat(32)
    private val link2 = "c2".repeat(32)
    private val link3 = "c3".repeat(32)

    private val cid = ControlFixtures.communityId

    private fun registryEid(creator: String) = ConcordInviteRegistry.coordinateHex(cid, creator)

    private fun edition(
        kind: ControlEntityKind,
        eid: String,
        content: String,
        author: String = owner,
        version: Long = 0,
        prev: ControlEdition? = null,
    ) = ControlEdition(kind, eid.hexToByteArray(), version, prev?.hash, null, content, author, "r-$eid-$version-$author", version)

    private fun registry(
        creator: String,
        content: String,
        version: Long = 0,
        prev: ControlEdition? = null,
        at: String = registryEid(creator),
    ) = edition(ControlEntityKind.INVITE_REGISTRY, at, content, creator, version, prev)

    private fun list(vararg signers: String) = ConcordInviteRegistry.encode(signers.toList())

    private val inviterRoleEdition =
        edition(ControlEntityKind.ROLE, inviterRole, """{"role_id":"$inviterRole","name":"Inviter","position":2,"permissions":"${ConcordPermissions.of(ConcordPermissions.CREATE_INVITE).toWire()}"}""")

    private fun grant(
        member: String,
        vararg roles: String,
        version: Long = 0,
        prev: ControlEdition? = null,
    ) = edition(
        ControlEntityKind.GRANT,
        ControlFixtures.grantEid(member),
        """{"member":"$member","role_ids":[${roles.joinToString(",") { "\"$it\"" }}]}""",
        version = version,
        prev = prev,
    )

    @Test
    fun theCoordinateIsTheSpecDerivation() {
        assertEquals(
            ConcordKeyDerivation.inviteLinksCoordinate(cid, alice.hexToByteArray()).toHexKey(),
            registryEid(alice),
        )
    }

    @Test
    fun theBuilderWritesAnEditionAtTheCreatorsCoordinateWithLocatorsOnly() {
        val rumor = ConcordInviteRegistry.rumor(alice, cid, listOf(link2, link1.uppercase(), link1, "not-a-key"), version = 3, prevHash = ByteArray(32) { 9 }, createdAt = 1_700_000_000)
        val parsed = assertNotNull(ControlEdition.fromRumor(rumor))
        assertEquals(ControlEntityKind.INVITE_REGISTRY, parsed.entityKind)
        assertEquals("8", parsed.vsk)
        assertEquals(registryEid(alice), parsed.entityIdHex)
        assertEquals(3, parsed.version)
        assertEquals(alice, parsed.author)
        // Lowercase, de-duplicated, sorted, junk dropped: a bare array of link-signer pubkeys.
        assertEquals("""["$link1","$link2"]""", parsed.content)
    }

    @Test
    fun theOwnersRegistryMakesTheCommunityPublic() {
        val state = ControlFixtures.fold(listOf(registry(owner, list(link1, link2))), owner)
        assertEquals(mapOf(owner to listOf(link1, link2)), state.inviteRegistries)
        assertEquals(setOf(link1, link2), state.liveInviteLinks)
        assertTrue(state.isPublic)
    }

    @Test
    fun noRegistryOrAnEmptyOneIsPrivate() {
        assertFalse(ControlFixtures.fold(emptyList(), owner).isPublic)
        val emptied = ControlFixtures.fold(listOf(registry(owner, "[]")), owner)
        assertFalse(emptied.isPublic)
        assertEquals(emptyList(), emptied.registryOf(owner))
    }

    @Test
    fun aCreateInviteHolderIsHonoredAndAnUnauthorizedAuthorIsNot() {
        val editions =
            listOf(
                inviterRoleEdition,
                grant(alice, inviterRole),
                registry(alice, list(link1)),
                registry(troll, list(link2)),
            )
        val state = ControlFixtures.fold(editions, owner)
        assertEquals(listOf(link1), state.registryOf(alice))
        assertEquals(emptyList(), state.registryOf(troll), "no CREATE_INVITE, no registry")
        assertEquals(setOf(link1), state.liveInviteLinks)
    }

    @Test
    fun aRegistryAtAnotherCreatorsCoordinateIsIgnored() {
        // Alice holds CREATE_INVITE but writes into Bob's coordinate: the coordinate binds to the author.
        val editions =
            listOf(
                inviterRoleEdition,
                grant(alice, inviterRole),
                registry(alice, list(link1), at = registryEid(bob)),
            )
        val state = ControlFixtures.fold(editions, owner)
        assertTrue(state.inviteRegistries.isEmpty())
        assertFalse(state.isPublic)
    }

    @Test
    fun malformedContentFallsBackToThePreviousEditionAndJunkEntriesAreDropped() {
        val v0 = registry(owner, list(link1))
        val broken = registry(owner, """{"links":["$link2"]}""", version = 1, prev = v0)
        assertEquals(listOf(link1), ControlFixtures.fold(listOf(v0, broken), owner).registryOf(owner), "not an array: the head stays at v0")

        val notJson = registry(owner, "[$link2", version = 1, prev = v0)
        assertEquals(listOf(link1), ControlFixtures.fold(listOf(v0, notJson), owner).registryOf(owner))

        // An array is well-formed whatever it holds; only 64-hex string entries survive, lowercased.
        val mixed = registry(owner, """["$link2", 42, null, "abc", "${link3.uppercase()}", "$link2", ["$link1"]]""", version = 1, prev = v0)
        assertEquals(listOf(link2, link3), ControlFixtures.fold(listOf(v0, mixed), owner).registryOf(owner))

        assertNull(ConcordInviteRegistry.decodeOrNull("nope"))
        assertNull(ConcordInviteRegistry.decodeOrNull("""{"a":1}"""))
    }

    @Test
    fun theAggregateSpansCreatorsAndDrivesThePublicHelpers() {
        val editions =
            listOf(
                inviterRoleEdition,
                grant(alice, inviterRole),
                grant(bob, inviterRole),
                registry(owner, list(link1)),
                registry(alice, list(link2)),
                registry(bob, "[]"),
            )
        val state = ControlFixtures.fold(editions, owner)
        assertEquals(setOf(link1, link2), state.liveInviteLinks)
        assertTrue(state.isPublic)

        // Banning alice leaves the owner's link: still Public. Banning her with the owner gone would not.
        assertTrue(state.isPublic(listOf(alice)))
        assertFalse(state.isPublic(listOf(alice, owner)))

        // Foreign links are anyone's but the viewer's (and the excluded).
        assertTrue(state.hasForeignLiveLinks(owner))
        assertFalse(state.hasForeignLiveLinks(owner, listOf(alice)))
        assertFalse(state.hasForeignLiveLinks(bob, listOf(alice, owner)))

        // Retiring one of two live links keeps it Public; retiring both flips it Private.
        assertFalse(state.retiringWouldPrivatize(listOf(link1)))
        assertTrue(state.retiringWouldPrivatize(listOf(link1, link2)))
        assertFalse(ControlFixtures.fold(emptyList(), owner).retiringWouldPrivatize(listOf(link1)), "already Private: nothing flips")
        // A dissolved community is never Refounded (CORD-02 §9): the last retire is just a retire.
        assertFalse(state.withDissolved(true).retiringWouldPrivatize(listOf(link1, link2)))
    }

    @Test
    fun aCreatorWhoLosesCreateInviteDropsOut() {
        val g0 = grant(alice, inviterRole)
        val before = listOf(inviterRoleEdition, g0, registry(alice, list(link1)))
        assertTrue(ControlFixtures.fold(before, owner).isPublic)

        // The owner strips alice's roles: her registry is no longer honored, the link no longer counts.
        val g1 = grant(alice, version = 1, prev = g0)
        val after = ControlFixtures.fold(before + g1, owner)
        assertEquals(emptyList(), after.registryOf(alice))
        assertFalse(after.isPublic)
    }

    @Test
    fun aBannedCreatorDropsOut() {
        val editions =
            listOf(
                inviterRoleEdition,
                grant(alice, inviterRole),
                registry(alice, list(link1)),
                edition(ControlEntityKind.BANLIST, ControlFixtures.banlistEid(), """["$alice"]"""),
            )
        assertFalse(ControlFixtures.fold(editions, owner).isPublic)
    }

    @Test
    fun aBanRefoundsOnlyWhenTheCommunityIsPrivateWithoutTheTargetsLinks() {
        val editions = listOf(inviterRoleEdition, grant(alice, inviterRole), registry(alice, list(link1)))
        val state = ControlFixtures.fold(editions, owner)
        assertFalse(state.banRequiresRefounding(listOf(bob)), "Public: the Banlist alone")
        assertTrue(state.banRequiresRefounding(listOf(alice)), "banning the only link creator leaves it Private")
        assertTrue(ControlFixtures.fold(emptyList(), owner).banRequiresRefounding(listOf(bob)), "Private: a ban Refounds")
    }

    private fun entry(
        token: String,
        signer: KeyPair,
        community: String = ControlFixtures.COMMUNITY_ID_HEX,
        expiresAt: Long? = null,
    ) = ConcordInviteListEntry(token = token, signerSk = signer.privKey!!.toHexKey(), communityId = community, url = "u", createdAt = 1, expiresAt = expiresAt)

    @Test
    fun theNextRegistryAddsMintsDropsRetiredAndPrunesExpiredOrTombstonedLinks() {
        val live = KeyPair()
        val expired = KeyPair()
        val tombstoned = KeyPair()
        val otherCommunity = KeyPair()
        val doc =
            ConcordInviteListDocument(
                entries =
                    listOf(
                        entry("01", live),
                        entry("02", expired, expiresAt = 100),
                        entry("03", tombstoned),
                        entry("04", otherCommunity, community = "ee".repeat(32)),
                    ),
                tombstones = listOf(ConcordInviteListTombstone("03", ControlFixtures.COMMUNITY_ID_HEX)),
            )
        val livePk = live.pubKey.toHexKey()
        val expiredPk = expired.pubKey.toHexKey()

        // The published registry still lists the expired link and one no list entry backs (link1): the
        // readable list is authoritative, so both go; the recorded live link heals in; a mint adds link2.
        val next = ConcordInviteRegistry.nextLinks(listOf(expiredPk, link1), doc, ControlFixtures.COMMUNITY_ID_HEX, nowSecs = 200, minted = listOf(link2))
        assertEquals(listOf(link2, livePk).sorted(), next)

        // Retiring the recorded live link, with the next mint recorded too, leaves only that mint.
        val withMint = ConcordInviteListDocument(entries = doc.entries + entry("05", KeyPair()), tombstones = doc.tombstones)
        val link3 = withMint.entries.last().signerPubKeyHex()
        assertEquals(listOf(link3), ConcordInviteRegistry.nextLinks(next, withMint, ControlFixtures.COMMUNITY_ID_HEX, nowSecs = 200, retired = listOf(livePk)))

        // Before its expiry the link is still live; an unreadable list prunes nothing.
        assertTrue(expiredPk in ConcordInviteRegistry.nextLinks(emptyList(), doc, ControlFixtures.COMMUNITY_ID_HEX, nowSecs = 50))
        assertEquals(listOf(expiredPk), ConcordInviteRegistry.nextLinks(listOf(expiredPk), null, ControlFixtures.COMMUNITY_ID_HEX, nowSecs = 200))
    }

    @Test
    fun theMemoizedRegistryCoordinateIsTheDerivedOne() {
        val author = KeyPair().pubKey.toHexKey()
        val derived = ConcordInviteRegistry.coordinateHex(ControlFixtures.COMMUNITY_ID_HEX.hexToByteArray(), author)
        repeat(2) {
            assertEquals(derived, AuthorityResolver.inviteLinksCoordinateHex(ControlFixtures.COMMUNITY_ID_HEX.hexToByteArray(), ControlFixtures.COMMUNITY_ID_HEX, author))
        }
        // Keyed by community too: the same author elsewhere is a different coordinate.
        val other = "ab".repeat(32)
        assertEquals(ConcordInviteRegistry.coordinateHex(other.hexToByteArray(), author), AuthorityResolver.inviteLinksCoordinateHex(other.hexToByteArray(), other, author))
    }

    @Test
    fun aRetiredLinkWhoseEntryTheMergeDroppedIsNotResurrectedFromThePublishedRegistry() {
        val retired = KeyPair()
        val kept = KeyPair()
        val retiredPk = retired.pubKey.toHexKey()
        val keptPk = kept.pubKey.toHexKey()
        // After the tombstone merge, the retired link's entry (and its token) is gone from the list:
        // only the tombstone remains, which no longer names the signer.
        val merged =
            ConcordInviteListDocument(
                entries = listOf(entry("0a", kept)),
                tombstones = listOf(ConcordInviteListTombstone("0b", ControlFixtures.COMMUNITY_ID_HEX)),
            )
        // A later, unrelated registry edit (e.g. the next mint) must not carry the retired signer forward.
        assertEquals(listOf(keptPk), ConcordInviteRegistry.nextLinks(listOf(retiredPk, keptPk), merged, ControlFixtures.COMMUNITY_ID_HEX, nowSecs = 200))
    }
}
