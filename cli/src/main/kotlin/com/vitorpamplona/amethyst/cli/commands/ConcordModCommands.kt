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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.stores.ConcordStore
import com.vitorpamplona.amethyst.cli.stores.StoredCommunity
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils

/** `amy concord roles|role|grant|ban|unban` — Control Plane roles & moderation (CORD-04). */
object ConcordModCommands {
    /** Lists the community's live roles and current banlist. */
    suspend fun roles(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val handle = Args(rest).positional(0, "community")
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (_, editions) = load(ctx, sc)
            val state = ConcordCommunityState.fold(editions, sc.owner)
            Output.emit(
                mapOf(
                    "roles" to
                        state.roles.map { (id, r) ->
                            mapOf("id" to id, "name" to r.name, "position" to r.position, "permissions" to r.permissions)
                        },
                    "banned" to ConcordModeration.currentBanned(editions, sc.communityId.hexToByteArray()).toList(),
                ),
            )
            return 0
        }
    }

    /** Defines a new role: `role <community> <name> <position> PERM...` (perms by name, e.g. BAN KICK). */
    suspend fun defineRole(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val name = args.positional(1, "name")
        val position = args.positional(2, "position").toLongOrNull() ?: return Output.error("bad_args", "position must be an integer").let { 2 }
        val permBits = args.positional.drop(3).mapNotNull { permByName(it) }
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (cp, editions) = load(ctx, sc)
            val roleId = RandomInstance.bytes(32)
            val role = RoleEntity(name = name, position = position, permissions = ConcordPermissions.of(*permBits.toIntArray()).toWire())
            val wrap = ConcordModeration.defineRole(ctx.signer, cp, roleId, role, editions, TimeUtils.now())
            val acked = ctx.publish(wrap, ConcordCommands.relaysFor(ctx, sc)).filterValues { it }.keys
            Output.emit(mapOf("role_id" to roleId.toHexKey(), "name" to name, "position" to position, "published_to" to acked.map { it.url }))
            return 0
        }
    }

    /** Grants a role to a member: `grant <community> <user> <roleId>`. */
    suspend fun grant(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val userRef = args.positional(1, "user")
        val roleId = args.positional(2, "roleId")
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val member = ctx.requireUserHex(userRef)
            val (cp, editions) = load(ctx, sc)
            val wrap = ConcordModeration.grant(ctx.signer, cp, sc.communityId.hexToByteArray(), member, listOf(roleId), editions, TimeUtils.now())
            val acked = ctx.publish(wrap, ConcordCommands.relaysFor(ctx, sc)).filterValues { it }.keys
            Output.emit(mapOf("member" to member, "roles" to listOf(roleId), "published_to" to acked.map { it.url }))
            return 0
        }
    }

    /** Bans a member: `ban <community> <user>`. */
    suspend fun ban(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = banOrUnban(dataDir, rest, ban = true)

    /** Unbans a member: `unban <community> <user>`. */
    suspend fun unban(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = banOrUnban(dataDir, rest, ban = false)

    private suspend fun banOrUnban(
        dataDir: DataDir,
        rest: Array<String>,
        ban: Boolean,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val userRef = args.positional(1, "user")
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val member = ctx.requireUserHex(userRef)
            val (cp, editions) = load(ctx, sc)
            val cid = sc.communityId.hexToByteArray()
            val wrap =
                if (ban) {
                    ConcordModeration.ban(ctx.signer, cp, cid, member, editions, TimeUtils.now())
                } else {
                    ConcordModeration.unban(ctx.signer, cp, cid, member, editions, TimeUtils.now())
                }
            val acked = ctx.publish(wrap, ConcordCommands.relaysFor(ctx, sc)).filterValues { it }.keys
            Output.emit(mapOf("member" to member, "banned" to ban, "published_to" to acked.map { it.url }))
            return 0
        }
    }

    /** Drain the control plane and return its key + current editions to chain onto. */
    private suspend fun load(
        ctx: Context,
        sc: StoredCommunity,
    ): Pair<GroupKey, List<ControlEdition>> {
        val cp = ConcordActions.controlPlane(sc.root.hexToByteArray(), sc.communityId.hexToByteArray(), sc.rootEpoch)
        val relays = ConcordCommands.relaysFor(ctx, sc)
        // Concord relays serve the plane's kind-1059 only to a connection AUTHed as the derived
        // stream key — register the control key so the drain isn't refused (else the fold is empty).
        ctx.registerConcordStreamKeys(relays, listOf(cp.secretKey))
        val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(cp.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
        return cp to ConcordActions.controlEditions(wraps, cp)
    }

    private fun permByName(name: String): Int? =
        when (name.uppercase()) {
            "MANAGE_ROLES" -> ConcordPermissions.MANAGE_ROLES
            "MANAGE_CHANNELS" -> ConcordPermissions.MANAGE_CHANNELS
            "MANAGE_METADATA" -> ConcordPermissions.MANAGE_METADATA
            "KICK" -> ConcordPermissions.KICK
            "BAN" -> ConcordPermissions.BAN
            "MANAGE_MESSAGES" -> ConcordPermissions.MANAGE_MESSAGES
            "CREATE_INVITE" -> ConcordPermissions.CREATE_INVITE
            else -> null
        }
}
