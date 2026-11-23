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

            // ...and the legacy role, grant and plain private-channel editors are refused, each with a
            // notice the app words (not a generic signer failure).
            val role =
                assertFailsWith<UserNoticeException> {
                    ConcordModeration.defineRole(owner, cp, cid, ByteArray(32) { 9 }, RoleEntity(name = "Mods", position = 1), editions, createdAt = 5L, owner = community.ownerPubKey)
                }
            assertEquals(ConcordNotice.RolesV2Unsupported(ConcordNotice.RolesV2Editor.ROLES), role.notice)
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
}
