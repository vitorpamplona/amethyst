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
import com.vitorpamplona.amethyst.commons.actions.ConcordReceive
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord05Invites.InviteBundleStatus
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.nip01Core.core.Event
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
            val (_, editions) = load(ctx, sc, dataDir)
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
            val (cp, editions) = load(ctx, sc, dataDir)
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
            val loaded = load(ctx, sc, dataDir)
            val (cp, editions) = loaded
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
                    controlRoot =
                        loaded.community.controlRoot
                            .ifBlank { null }
                            ?.hexToByteArray(),
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
            val (cp, editions) = load(ctx, sc, dataDir)
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

    /**
     * The drained Control Plane: the community as stored *after* any adoption, its keys, and the
     * editions to chain onto. [community] matters because adopting a delivered `control_root`
     * rewrites the stored record — a caller that kept the pre-load copy would then fail to pass the
     * secret on in its own Grant (CORD-04 §3).
     */
    private class LoadedControl(
        val community: StoredCommunity,
        val keys: ControlPlaneKeys,
        val editions: List<ControlEdition>,
    ) {
        operator fun component1() = keys

        operator fun component2() = editions
    }

    /**
     * `concord refound COMMUNITY --remove USER[,USER…]` — a CORD-06 Refounding: the hard removal.
     *
     * A ban only strips standing; the removed member keeps every key they ever held, so the room is
     * only truly closed to them by rotating the `community_root` (and, since CORD-02 §2, a fresh
     * `control_root` beside it, so a demoted staffer's retained secret dies with the epoch). The
     * compacted Control Plane is re-sealed at the new epoch and each retained member gets a rekey
     * blob; nobody else can follow.
     *
     * Authority mirrors Amethyst exactly: `hasPermission`, never `effectivePermissions`, so a banned
     * BAN-holder cannot launch one; the owner is never a valid target; and removal takes the same
     * rank rule as a ban (CORD-04 §3) — an admin cannot Refound a peer admin out.
     *
     * **The recipient set is a floor, not a census.** It is the roster ∪ Guestbook ∪ the authors of
     * every channel message we can decrypt ∪ ourselves, minus the removed and already-banned — the
     * same union Amethyst builds, because a member who only ever posted holds no role and leaves no
     * Guestbook motion, and omitting them silently expels them. A member with no trace at all still
     * cannot be re-keyed; `concord recover` is how they get back.
     */
    suspend fun refound(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val removeArg = args.flag("remove") ?: return Output.error("bad_args", "refound <community> --remove USER[,USER…]").let { 2 }
        args.rejectUnknown()
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val removed =
                removeArg
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { ctx.requireUserHex(it).lowercase() }
                    .toSet()
            if (removed.isEmpty()) return Output.error("bad_args", "--remove needs at least one user")

            val loaded = load(ctx, sc, dataDir)
            val (cp, editions) = loaded
            val state = ConcordCommunityState.fold(editions, sc.owner)
            val authority = state.authority
            val me = ctx.signer.pubKey

            if (!ConcordReceive.isAuthorizedRotator(authority, me)) {
                return Output.error("forbidden", "this account cannot refound: a Refounding takes BAN (or ownership), and a banned holder is refused (CORD-06)")
            }
            if (removed.any { authority.isOwner(it) }) {
                return Output.error("forbidden", "the owner is never a valid removal target (CORD-04 §3)")
            }
            // An admin cannot Refound a peer admin out any more than they could ban one.
            if (!authority.isOwner(me) && removed.any { !authority.canActOn(me, it, ConcordPermissions.BAN) }) {
                return Output.error("forbidden", "you do not outrank every member you are removing (CORD-04 §3, equal cannot act on equal)")
            }
            // A Refounding writes the current plane (the pre-rotation bans) and the new one, so on a
            // split epoch it takes the current control_root (CORD-02 §2).
            writeGuard(cp)?.let { return it }

            val relays = ConcordCommands.relaysFor(ctx, sc)

            // 1. Ban the removed on the CURRENT plane, so the compacted snapshot — and therefore the
            //    new epoch — carries the ban. Each edition chains onto the updated banlist head.
            var chain = editions
            val banWraps = mutableListOf<Event>()
            for (target in removed) {
                val banWrap = ConcordModeration.ban(ctx.signer, cp, sc.communityId.hexToByteArray(), target, chain, TimeUtils.now(), owner = sc.owner)
                val ack = ctx.publish(banWrap, relays)
                if (ack.values.none { it.accepted }) {
                    return Output.error("ban_not_published", "the pre-rotation ban for $target was not accepted by any relay; refusing to refound with a banlist that would not survive")
                }
                banWraps += banWrap
                chain = chain + (ConcordActions.controlEditions(listOf(banWrap), cp))
            }

            // 2. Everyone we are keeping. See the note above on why this reaches past the roster.
            val candidates =
                (rosterOf(authority) + guestbookMembersOf(ctx, sc) + channelAuthorsOf(ctx, sc, state) + me)
                    .mapTo(HashSet()) { it.lowercase() }
                    .apply {
                        removeAll(removed)
                        removeAll(authority.bannedMembers().map { it.lowercase() }.toSet())
                    }
            val recipients = boundRecipients(candidates, authority)

            // 3. Build: new root + fresh control_root, compacted plane, per-recipient blobs (staff
            //    get the 136-byte form carrying the secret, everyone else the 104-byte pubkey one).
            val newRoot = RandomInstance.bytes(32)
            val newControlRoot = RandomInstance.bytes(32)
            // Compact from what we KNOW the plane holds: the wraps we drained plus the bans we just
            // published. Re-draining alone would race the relay's indexing, and a relay that has not
            // yet echoed the ban back (or that ACKed and stored nothing) would produce a new epoch
            // whose roster never banned the member we are removing.
            val drained = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(cp.address)) }, pendingOnAuthRequired = true).map { it.second }
            val controlWraps = (drained + banWraps).distinctBy { it.id }
            val build =
                ConcordActions.buildRefounding(
                    rotatorSigner = ctx.signer,
                    communityId = sc.communityId,
                    priorRoot = sc.root.hexToByteArray(),
                    newRoot = newRoot,
                    newControlRoot = newControlRoot,
                    rootEpoch = sc.rootEpoch,
                    priorControlWraps = controlWraps,
                    priorControlKeys = cp,
                    recipientsXOnly = recipients,
                    staffXOnly = authority.staffMembers(),
                    createdAt = TimeUtils.now(),
                    ownerPubKey = sc.owner,
                )

            // 4. The compacted plane (the new epoch's state) then the blobs (the key that opens it).
            build.controlWraps.forEach { ctx.publish(it, relays) }
            build.rekeyWraps.forEach { ctx.publish(it, relays) }

            // 5. Adopt the new epoch ourselves — the same pure rewrite Amethyst uses, banking the
            //    epoch we are leaving for the anti-rollback floor.
            val adopted =
                ConcordReceive.withAdoptedRoot(
                    ConcordCommands.entryFor(loaded.community),
                    newRoot,
                    build.newEpoch,
                    build.newControlKeys.address.hexToByteArray(),
                    newControlRoot,
                )
            val stored = ConcordCommands.storedFrom(loaded.community, adopted)
            ConcordStore(dataDir.concordFile).upsert(stored)

            // 6. Refresh every link we minted, at its OWN coordinate, so it now resolves to the new
            //    epoch. This is the liveness half of stranded recovery (A2): a member this Refounding
            //    left out has no rekey blob and no message to miss, so re-resolving their link is the
            //    only way back — and it only works if the bundle moves with the community instead of
            //    being orphaned at a dead epoch. Minting a fresh link would not help them; the link
            //    they hold is the one that must move.
            //
            //    Safe for every link because recovery is ban-gated at the epoch being left, and step 1
            //    banned everyone being removed — so a removed member's own `recover` is refused even
            //    though their link now resolves.
            val now = TimeUtils.now()
            var refreshed = 0
            val list = ConcordCommands.readInviteList(ctx)
            val tombstoned = list?.tombstones?.mapTo(HashSet()) { it.token } ?: emptySet<String>()
            for (link in list?.entries.orEmpty()) {
                if (link.communityId != stored.communityId) continue
                // An elapsed or retired link can no longer be joined, so re-posting it would only
                // resurrect a dead URL at a live epoch (CORD-05).
                if (link.isExpired(now) || link.token in tombstoned) continue
                runCatching {
                    val token = link.token.hexToByteArray()
                    // Refresh from the link's CURRENT bundle so its own fields — expiry, channel
                    // grants, icon, label — survive the rotation, and so a coordinate whose newest
                    // event is a revocation tombstone is left revoked instead of being re-opened.
                    val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.bundleFilter(link.signerPubKeyHex())) }).map { it.second }
                    val live = ConcordActions.classifyInvite(wraps, token) as? InviteBundleStatus.Live ?: return@runCatching
                    val moved =
                        live.invite.copy(
                            communityRoot = stored.root,
                            rootEpoch = stored.rootEpoch,
                            controlPk = stored.controlPk.ifBlank { null },
                            relays = stored.relays,
                        )
                    ctx.publish(ConcordActions.remintBundleAt(link.signerSk.hexToByteArray(), token, moved, now), relays)
                    refreshed++
                }
            }

            Output.emit(
                mapOf(
                    "community_id" to sc.communityId,
                    "removed" to removed.toList(),
                    "from_epoch" to sc.rootEpoch,
                    "root_epoch" to build.newEpoch,
                    "recipients" to recipients.size,
                    "control_wraps" to build.controlWraps.size,
                    "rekey_wraps" to build.rekeyWraps.size,
                    "invites_refreshed" to refreshed,
                ),
            )
            return 0
        }
    }

    /**
     * How many recipients one Refounding will re-key, mirroring Amethyst's own cap.
     *
     * Two thirds of the recipient union — Guestbook joins and observed channel authors — are
     * attacker-writable: any key can announce a join or post once. Without a bound, padding those
     * sets inflates the cost of the only hard removal Concord has until rotating becomes
     * impractical, so the attack raises the price of its own remedy (B4 in the soft-ban audit).
     */
    private const val MAX_REFOUNDING_RECIPIENTS = 5_000

    /**
     * Caps [candidates], keeping the members whose standing is owner-rooted and therefore cannot be
     * padded from outside. Anything dropped is reported rather than silently truncated — a dropped
     * member is stranded on the dead epoch and their only way back is `concord recover`.
     */
    private fun boundRecipients(
        candidates: Set<String>,
        authority: com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver,
    ): List<String> {
        if (candidates.size <= MAX_REFOUNDING_RECIPIENTS) return candidates.toList()
        val vouched = (authority.roleHolders() + authority.staffMembers()).mapTo(HashSet()) { it.lowercase() }
        val kept = LinkedHashSet<String>()
        candidates.filterTo(kept) { it in vouched }
        for (candidate in candidates) {
            if (kept.size >= MAX_REFOUNDING_RECIPIENTS) break
            kept.add(candidate)
        }
        val dropped = candidates.size - kept.size
        if (dropped > 0) {
            System.err.println("[concord] refounding recipient set trimmed to ${kept.size} of ${candidates.size}: $dropped member(s) will be stranded on the prior epoch")
        }
        return kept.toList()
    }

    /** Owner + everyone holding a role — owner-rooted, so it cannot be padded from outside. */
    private fun rosterOf(authority: com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver): Set<String> = (authority.roleHolders() + authority.staffMembers()).mapTo(HashSet()) { it.lowercase() }

    /** Live Guestbook membership at this epoch (joins minus later leaves, CORD-02 §5). */
    private suspend fun guestbookMembersOf(
        ctx: Context,
        sc: StoredCommunity,
    ): Set<String> =
        runCatching {
            val gb = ConcordActions.guestbookPlane(sc.root.hexToByteArray(), sc.communityId.hexToByteArray(), sc.rootEpoch)
            val relays = ConcordCommands.relaysFor(ctx, sc)
            ctx.registerConcordStreamKeys(relays, listOf(gb.secretKey))
            val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(gb.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
            ConcordActions.guestbookMembers(wraps, gb).mapTo(HashSet()) { it.lowercase() }
        }.getOrDefault(emptySet())

    /**
     * Authors of every channel message we can decrypt. Most members never send a Guestbook motion,
     * so without this a Refounding silently expels everyone who had only ever posted.
     */
    private suspend fun channelAuthorsOf(
        ctx: Context,
        sc: StoredCommunity,
        state: ConcordCommunityState,
    ): Set<String> {
        val out = HashSet<String>()
        val relays = ConcordCommands.relaysFor(ctx, sc)
        for ((channelIdHex, _) in state.channels) {
            runCatching {
                val key = ConcordActions.publicChannel(sc.root.hexToByteArray(), channelIdHex.hexToByteArray(), sc.rootEpoch)
                ctx.registerConcordStreamKeys(relays, listOf(key.secretKey))
                val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(key.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
                ConcordActions.channelMessages(wraps, key, channelIdHex, sc.rootEpoch).mapTo(out) { it.author.lowercase() }
            }
        }
        return out
    }

    /** Drain the control plane and return its keys + current editions to chain onto. */
    private suspend fun load(
        ctx: Context,
        sc: StoredCommunity,
        dataDir: DataDir? = null,
    ): LoadedControl {
        val cp = ConcordCommands.controlPlaneKeysFor(sc)
        val relays = ConcordCommands.relaysFor(ctx, sc)
        // Concord relays serve the plane's kind-1059 only to a connection AUTHed as the stream
        // key — register it so the drain isn't refused (else the fold is empty). On a split epoch
        // that secret is staff-only (CORD-02 §2), and a member simply has nothing to register.
        ctx.registerConcordStreamKeys(relays, listOfNotNull(cp.signer?.secretKey))
        val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(cp.address)) }, pendingOnAuthRequired = true).map { it.second }
        val editions = ConcordActions.controlEditions(wraps, cp)

        // A promotion to staff delivers the Control Plane write key inside the Grant itself
        // (CORD-04 §3), so the fold that seats the role is also when the key arrives. Amethyst
        // drains this on its revision tick; amy has no tick, so the fold a command already does is
        // the moment to adopt — otherwise a CLI-promoted staffer holds a rank it can never write
        // under. Same shared, fail-closed check both clients use.
        if (dataDir != null && !cp.canWrite) {
            val adopted = ConcordCommands.adoptDeliveredControlRoot(ctx, dataDir, sc, editions)
            if (adopted != null) return LoadedControl(adopted.first, adopted.second, ConcordActions.controlEditions(wraps, adopted.second))
        }
        return LoadedControl(sc, cp, editions)
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
