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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.notices.ConcordNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeException
import com.vitorpamplona.amethyst.commons.util.notifySignerErrors
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Writing to a Community on Roles v2 (CORD-04 §1, §2): once its owner publishes `@everyone`, every
 * edition we write is marked, and the legacy role, grant and plain private-channel editors, whose
 * shapes a v2 reader drops, are refused rather than published.
 */
class ConcordRolesV2WriteTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val member = NostrSignerInternal(KeyPair())

    @Test
    fun aSwitchedCommunityMarksEveryEditionAndRefusesTheLegacyEditors() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Guild", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val cid = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            fun add(wrap: Event) {
                editions += ConcordActions.controlEditions(listOf(wrap), cp)
            }

            // Legacy: an edition is written unmarked.
            val legacyChannel = ConcordModeration.defineChannel(owner, cp, cid, ByteArray(32) { 7 }, ChannelEntity(name = "lounge"), editions, createdAt = 2L, owner = community.ownerPubKey)
            assertFalse(ConcordActions.controlEditions(listOf(legacyChannel), cp).single().isMarked)
            add(legacyChannel)

            // The owner switches the Community: `@everyone`, marked, at its coordinate.
            val binding = ConcordKeyDerivation.editionBinding(cid)
            val everyone =
                ControlEditionBuilder.rumor(
                    owner.pubKey,
                    ControlEntityKind.RANKED_ROLE,
                    ConcordKeyDerivation.everyoneCoordinate(cid),
                    1,
                    null,
                    """{"permissions":"${ConcordPermissions.EVERYONE_DEFAULT.toWire()}"}""",
                    3L,
                    null,
                    binding,
                )
            add(ConcordStreamEnvelope.wrap(everyone, cp, owner, encrypted = false, createdAt = 3L))
            assertTrue(ConcordCommunityState.fold(editions, cid, community.ownerPubKey).isRolesV2)

            // Now a public channel edition is marked...
            val v2Channel = ConcordModeration.defineChannel(owner, cp, cid, ByteArray(32) { 8 }, ChannelEntity(name = "news"), editions, createdAt = 4L, owner = community.ownerPubKey)
            assertTrue(ConcordActions.controlEditions(listOf(v2Channel), cp).single().isMarked)
            add(v2Channel)
            assertTrue(
                ConcordCommunityState
                    .fold(editions, cid, community.ownerPubKey)
                    .channels.values
                    .any { it.definition.name == "news" },
            )

            // ...a role goes out as a marked vsk-13 Role, keyed by rank, which the v2 fold honors...
            val mods = ConcordModeration.defineRole(owner, cp, cid, ByteArray(32) { 9 }, RoleEntity(name = "Mods", position = 1, permissions = "16"), editions, createdAt = 5L, owner = community.ownerPubKey)
            val modsEdition = ConcordActions.controlEditions(listOf(mods), cp).single()
            assertEquals(ControlEntityKind.RANKED_ROLE, modsEdition.entityKind)
            assertTrue(modsEdition.isMarked)
            add(mods)
            // ...and a second one asked for position 1 goes above it.
            add(ConcordModeration.defineRole(owner, cp, cid, ByteArray(32) { 11 }, RoleEntity(name = "Admins", position = 1, permissions = "1"), editions, createdAt = 6L, owner = community.ownerPubKey))
            val folded = ConcordCommunityState.fold(editions, cid, community.ownerPubKey)
            assertEquals(
                listOf("Admins", "Mods"),
                folded.roles.values
                    .sortedBy { it.position }
                    .map { it.name },
            )
            assertEquals(
                "16",
                folded.roles.values
                    .single { it.name == "Mods" }
                    .permissions,
            )

            // The legacy grant and plain private-channel editors are still refused, each with a
            // notice the app words (not a generic signer failure).
            val grant =
                assertFailsWith<UserNoticeException> {
                    ConcordModeration.grant(owner, cp, cid, member.pubKey, emptyList(), editions, createdAt = 5L, owner = community.ownerPubKey)
                }
            assertEquals(ConcordNotice.RolesV2Unsupported(ConcordNotice.RolesV2Editor.GRANTS), grant.notice)
            val private =
                assertFailsWith<UserNoticeException> {
                    ConcordModeration.defineChannel(owner, cp, cid, ByteArray(32) { 10 }, ChannelEntity(name = "vip", private = true), editions, createdAt = 5L, owner = community.ownerPubKey)
                }
            assertEquals(ConcordNotice.RolesV2Unsupported(ConcordNotice.RolesV2Editor.PRIVATE_CHANNEL), private.notice)

            // launchSigner's reporting hands the user that notice.
            val heard = mutableListOf<UserNotice>()
            notifySignerErrors({ heard += it }) {
                ConcordModeration.grant(owner, cp, cid, member.pubKey, emptyList(), editions, createdAt = 5L, owner = community.ownerPubKey)
            }
            assertEquals(listOf<UserNotice>(ConcordNotice.RolesV2Unsupported(ConcordNotice.RolesV2Editor.GRANTS)), heard)
        }

    @Test
    fun aRoleKeepsItsPlaceWhenTheFoldsPositionsHaveGaps() =
        runTest {
            // The fold numbers v2 Roles by every marked edition's rank key, superseded ones included,
            // so the live positions have gaps (audit 3): a rename that keeps a role's position must
            // keep its place, and a move lands relative to the roles as the editor sees them.
            val community = ConcordCommunityFactory.create(owner, "Guild", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val cid = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()
            var clock = 2L

            fun add(wrap: Event) {
                editions += ConcordActions.controlEditions(listOf(wrap), cp)
            }

            fun folded() = ConcordCommunityState.fold(editions, cid, community.ownerPubKey)

            fun order() =
                folded()
                    .roles.values
                    .filterNot { it.deleted }
                    .sortedBy { it.position }
                    .map { it.name }

            fun positionOf(name: String) =
                folded()
                    .roles.values
                    .single { it.name == name }
                    .position

            val ids = mapOf("A" to ByteArray(32) { 21 }, "B" to ByteArray(32) { 22 }, "C" to ByteArray(32) { 23 }, "D" to ByteArray(32) { 24 })

            suspend fun define(
                name: String,
                position: Long,
                rename: String = name,
            ) = add(ConcordModeration.defineRole(owner, cp, cid, ids.getValue(name), RoleEntity(name = rename, position = position, permissions = "0"), editions, createdAt = clock++, owner = community.ownerPubKey))

            val binding = ConcordKeyDerivation.editionBinding(cid)
            val everyone =
                ControlEditionBuilder.rumor(owner.pubKey, ControlEntityKind.RANKED_ROLE, ConcordKeyDerivation.everyoneCoordinate(cid), 1, null, """{"permissions":"${ConcordPermissions.EVERYONE_DEFAULT.toWire()}"}""", clock++, null, binding)
            add(ConcordStreamEnvelope.wrap(everyone, cp, owner, encrypted = false, createdAt = clock))

            define("A", 1)
            define("B", 2)
            define("C", 3)
            assertEquals(listOf("A", "B", "C"), order())

            define("C", 1)
            assertEquals(listOf("C", "A", "B"), order())
            define("A", positionOf("A"))
            assertEquals(listOf("C", "A", "B"), order())
            // Renaming with the position the editor shows keeps the role where it was.
            define("A", positionOf("A"))
            assertEquals(listOf("C", "A", "B"), order())

            // Moving down onto another role's position takes its place; moving up goes above it.
            define("C", positionOf("B"))
            assertEquals(listOf("A", "B", "C"), order())
            define("C", positionOf("A"))
            assertEquals(listOf("C", "A", "B"), order())
            define("A", positionOf("B"))
            assertEquals(listOf("C", "B", "A"), order())

            // A new role at the lowest position + 1 goes to the bottom.
            val lowest = folded().roles.values.maxOf { it.position }
            define("D", lowest + 1)
            assertEquals(listOf("C", "B", "A", "D"), order())
        }

    @Test
    fun aRankKeyLandsAtTheAskedPositionButNeverAboveItsWriter() {
        val others = listOf("a1" to "B", "a2" to "M", "a3" to "T")
        val top = ConcordModeration.rankKeyFor(1, others, actorKey = null)!!
        assertTrue(top < "B")
        val second = ConcordModeration.rankKeyFor(2, others, actorKey = null)!!
        assertTrue(second > "B" && second < "M")
        val last = ConcordModeration.rankKeyFor(9, others, actorKey = null)!!
        assertTrue(last > "T")
        // A writer holding the "M" role can only place below it, whatever position it asks for.
        val below = ConcordModeration.rankKeyFor(1, others, actorKey = "M")!!
        assertTrue(below > "M" && below < "T")
    }

    @Test
    fun aFoldPositionMapsToItsSlotAmongTheLiveRoles() {
        // Live fold positions 1, 3, 4 (a superseded edition's key took 2).
        // Moving down onto a role's position takes its place; into the gap above it, stays put.
        assertEquals(2L, ConcordModeration.liveSlotOf(3, currentPosition = 1, othersPositions = listOf(3, 4)))
        assertEquals(1L, ConcordModeration.liveSlotOf(2, currentPosition = 1, othersPositions = listOf(3, 4)))
        // Moving up onto a role's position goes above it.
        assertEquals(2L, ConcordModeration.liveSlotOf(3, currentPosition = 4, othersPositions = listOf(1, 3)))
        // A new role past the lowest goes to the bottom; peers count once.
        assertEquals(4L, ConcordModeration.liveSlotOf(5, currentPosition = null, othersPositions = listOf(1, 3, 4)))
        assertEquals(2L, ConcordModeration.liveSlotOf(5, currentPosition = null, othersPositions = listOf(3, 3)))
    }
}
