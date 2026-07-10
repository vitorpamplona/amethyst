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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordModerationTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val admin = NostrSignerInternal(KeyPair())
    private val troll = NostrSignerInternal(KeyPair())

    @Test
    fun ownerDefinesRoleGrantsItAndBans() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId

            // Accumulate the community's editions as we publish more.
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            fun add(wrap: com.vitorpamplona.quartz.nip01Core.core.Event) {
                editions += ConcordActions.controlEditions(listOf(wrap), cp)
            }

            // Owner defines an "Admin" role (position 1) that can BAN and KICK.
            val roleId = ByteArray(32) { (it + 1).toByte() }
            val roleIdHex = roleId.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            val adminRole =
                RoleEntity(
                    name = "Admin",
                    position = 1,
                    permissions = ConcordPermissions.of(ConcordPermissions.BAN, ConcordPermissions.KICK).toWire(),
                )
            add(ConcordModeration.defineRole(owner, cp, roleId, adminRole, editions, createdAt = 2L))

            // Owner grants that role to the admin user.
            add(ConcordModeration.grant(owner, cp, communityId, admin.pubKey, listOf(roleIdHex), editions, createdAt = 3L))

            // Owner bans the troll.
            add(ConcordModeration.ban(owner, cp, communityId, troll.pubKey, editions, createdAt = 4L))

            val state: ConcordCommunityState = ConcordCommunityState.fold(editions, community.ownerPubKey)

            // The role exists, the admin holds BAN, and the troll is banned.
            assertTrue(state.roles.containsKey(roleIdHex))
            assertTrue(state.authority.effectivePermissions(admin.pubKey).has(ConcordPermissions.BAN))
            assertTrue(state.authority.isBanned(troll.pubKey))
            assertFalse(state.authority.isBanned(admin.pubKey))

            // Unbanning the troll clears the flag (version chains onto the ban).
            add(ConcordModeration.unban(owner, cp, communityId, troll.pubKey, editions, createdAt = 5L))
            val healed = ConcordCommunityState.fold(editions, community.ownerPubKey)
            assertFalse(healed.authority.isBanned(troll.pubKey))

            // A grant forged by the troll (who outranks nobody) is dropped by the fold.
            val forged = ConcordModeration.grant(troll, cp, communityId, troll.pubKey, listOf(roleIdHex), editions, createdAt = 6L)
            val forgedEditions: List<ControlEdition> = editions + ConcordActions.controlEditions(listOf(forged), cp)
            val afterForgery = ConcordCommunityState.fold(forgedEditions, community.ownerPubKey)
            assertFalse(afterForgery.authority.effectivePermissions(troll.pubKey).has(ConcordPermissions.BAN))
        }
}
