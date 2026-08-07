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
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
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
        val args = Args(rest)
        val handle = args.positional(0, "community")
        args.rejectUnknown()
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
                    // The role-holder roster AFTER the authority fixpoint, so a grant that was
                    // published but dropped on fold (granter didn't outrank the role or the member)
                    // is visibly absent here rather than looking like it landed.
                    "grants" to
                        state.authority.roleHolders().sorted().map { member ->
                            mapOf(
                                "member" to member,
                                "rank" to state.authority.rank(member),
                                "roles" to state.authority.rolesFor(member).map { it.name },
                            )
                        },
                    "banned" to ConcordModeration.currentBanned(editions, sc.communityId.hexToByteArray(), sc.owner).toList(),
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
        args.rejectUnknown()
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (cp, editions) = load(ctx, sc)
            writeGuard(cp)?.let { return it }
            val roleId = RandomInstance.bytes(32)
            val role = RoleEntity(name = name, position = position, permissions = ConcordPermissions.of(*permBits.toIntArray()).toWire())
            val wrap = ConcordModeration.defineRole(ctx.signer, cp, roleId, role, editions, TimeUtils.now(), owner = sc.owner)
            val ack = ctx.publish(wrap, ConcordCommands.relaysFor(ctx, sc))
            RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
            Output.emit(mapOf("role_id" to roleId.toHexKey(), "name" to name, "position" to position) + RawEventSupport.ackFields(ack))
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
        args.rejectUnknown()
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val member = ctx.requireUserHex(userRef)
            val (cp, editions) = load(ctx, sc)
            writeGuard(cp)?.let { return it }
            // A Grant that first makes its member staff must carry the write secret in the same
            // edition (CORD-04 §3); ConcordModeration wraps it pairwise when the granted roles
            // hold a Control-writing bit and we hold the secret to deliver.
            val wrap =
                ConcordModeration.grantWithStaffDelivery(
                    actor = ctx.signer,
                    controlPlane = cp,
                    communityId = sc.communityId.hexToByteArray(),
                    member = member,
                    roleIds = listOf(roleId),
                    current = editions,
                    createdAt = TimeUtils.now(),
                    owner = sc.owner,
                    controlRoot = sc.controlRoot.ifBlank { null }?.hexToByteArray(),
                    epoch = sc.rootEpoch,
                )
            val ack = ctx.publish(wrap, ConcordCommands.relaysFor(ctx, sc))
            RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
            Output.emit(mapOf("member" to member, "roles" to listOf(roleId)) + RawEventSupport.ackFields(ack))
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
        args.rejectUnknown()
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val member = ctx.requireUserHex(userRef)
            val (cp, editions) = load(ctx, sc)
            writeGuard(cp)?.let { return it }
            val cid = sc.communityId.hexToByteArray()
            val wrap =
                if (ban) {
                    ConcordModeration.ban(ctx.signer, cp, cid, member, editions, TimeUtils.now(), owner = sc.owner)
                } else {
                    ConcordModeration.unban(ctx.signer, cp, cid, member, editions, TimeUtils.now(), owner = sc.owner)
                }
            val ack = ctx.publish(wrap, ConcordCommands.relaysFor(ctx, sc))
            RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
            Output.emit(mapOf("member" to member, "banned" to ban) + RawEventSupport.ackFields(ack))
            return 0
        }
    }

    /** Drain the control plane and return its keys + current editions to chain onto. */
    private suspend fun load(
        ctx: Context,
        sc: StoredCommunity,
    ): Pair<ControlPlaneKeys, List<ControlEdition>> {
        val cp = ConcordCommands.controlPlaneKeysFor(sc)
        val relays = ConcordCommands.relaysFor(ctx, sc)
        // Concord relays serve the plane's kind-1059 only to a connection AUTHed as the stream
        // key — register it so the drain isn't refused (else the fold is empty). On a split epoch
        // that secret is staff-only (CORD-02 §2), and a member simply has nothing to register.
        ctx.registerConcordStreamKeys(relays, listOfNotNull(cp.signer?.secretKey))
        val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(cp.address)) }, pendingOnAuthRequired = true).map { it.second }
        return cp to ConcordActions.controlEditions(wraps, cp)
    }

    /**
     * Refuses a moderation command that this account cannot publish: on a split epoch
     * only `control_root` holders can mint a wrap the plane accepts (CORD-02 §2), so a
     * member would otherwise sign an edition every relay and reader drops. Possession is
     * a spam gate, never authority — holding the key still does not make the action
     * honored, which the Roster decides at fold (CORD-04 §5).
     */
    private fun writeGuard(cp: ControlPlaneKeys): Int? {
        if (cp.canWrite) return null
        Output.error("forbidden", "this account holds no control_root for the community, so it cannot publish Control Plane editions (CORD-02 §2) — ask a staff member to grant you a Control-writing role")
        return 1
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
