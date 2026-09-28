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
package com.vitorpamplona.quartz.nip43RelayMembers.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.EventCommandHandler
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kind
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.claim
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.membersWithRoles
import com.vitorpamplona.quartz.nip43RelayMembers.list.tags.RelayMember
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.roleColor
import com.vitorpamplona.quartz.nip43RelayMembers.roles.roleDescription
import com.vitorpamplona.quartz.nip43RelayMembers.roles.roleLabel
import com.vitorpamplona.quartz.nip43RelayMembers.roles.roleOrder
import com.vitorpamplona.quartz.nip70ProtectedEvts.isProtected
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max

/**
 * Relay-side NIP-43 engine: admits kind 28934 join requests, honours kind
 * 28936 leave requests, and keeps the relay-signed membership events —
 * kind 13534 (member list), 33534 (role definitions), 8000 / 8001
 * (member added / removed) — in step with the [BanStore].
 *
 * **Membership is the NIP-86 pubkey allow list.** A join allow-lists the
 * pubkey, a leave un-allow-lists it, and `allowpubkey` / `unallowpubkey` /
 * `banpubkey` over NIP-86 are membership changes too. Role definitions and
 * assignments are the [BanStore]'s NIP-43 roles; kind 13534 lists every
 * allow-listed pubkey followed by the ids of the roles assigned to it.
 *
 * **Publishing is a reconcile, not a log.** [sync] compares the [BanStore]
 * with what this relay last published and signs only the difference:
 * 8000 / 8001 for each pubkey that entered / left the allow list, a fresh
 * 13534 when the member set or any member's roles changed, a 33534 for each
 * new or edited role, and a NIP-09 kind 5 (`a` tag on the role's address)
 * for each deleted role. So every mutation path — a join, an admin RPC, a
 * hand-edited state file picked up at boot — converges on the same events,
 * and running [sync] twice publishes nothing the second time. The
 * "last published" state is read back from the relay's own store (via
 * [load]) the first time [sync] runs, so a restart doesn't republish.
 *
 * Replaceable events (13534, 33534) are stamped `max(now, previous + 1)` so
 * two changes within the same second still supersede each other instead of
 * tying on `created_at` (where the lower id, not the newer event, would win).
 *
 * Every event is signed with [signer], which must be the key the relay
 * advertises as NIP-11 `self`, and handed to [publish], which must store it
 * *bypassing* the relay's write policies (it is authored by the relay) and
 * fan it out to live subscribers — e.g. `NostrServer.ingest`.
 *
 * Join/leave requests are validated here, ahead of the policy chain (see
 * [EventCommandHandler]): signature, `created_at` within
 * [requestWindowSeconds] of now, a `claim` tag naming one of the
 * [BanStore]'s invite codes (joins), and a NIP-70 `-` tag (leaves). Invite
 * codes are **reusable** until an admin revokes them with `deleteclaim`:
 * NIP-86 lists them as "invite codes currently accepted by the relay" and
 * gives revocation its own method. Neither request is stored or broadcast
 * — they carry the invite code.
 */
class RelayMembershipServer(
    private val signer: NostrSignerSync,
    val banStore: BanStore,
    /** Stores a relay-signed event, bypassing write policies. Returns whether it was accepted. */
    private val publish: suspend (Event) -> Boolean,
    /** Reads the relay's own store; used once to learn what was published before a restart. */
    private val load: suspend (Filter) -> List<Event>,
    /** Where [requestSync] runs its background reconcile. Null makes [requestSync] a no-op. */
    scope: CoroutineScope? = null,
    /** Name used in the join welcome message, e.g. the relay URL. */
    private val relayName: String? = null,
    /** How far a join/leave request's `created_at` may be from now ("now, plus or minus a few minutes"). */
    val requestWindowSeconds: Long = DEFAULT_REQUEST_WINDOW_SECONDS,
    private val clock: () -> Long = TimeUtils::now,
) : EventCommandHandler {
    /** The relay's NIP-11 `self` pubkey — the author of every event this class publishes. */
    val selfPubKey: HexKey = signer.pubKey

    private val mutex = Mutex()

    // What this relay has published, as last seen. Guarded by [mutex].
    private var loaded = false
    private var publishedMembers: List<RelayMember> = emptyList()
    private var membersCreatedAt = 0L
    private val publishedRoles = HashMap<String, RelayRole>()

    /** Newest `created_at` used per role address, deletions included. */
    private val roleCreatedAt = HashMap<String, Long>()

    private val pokes = Channel<Unit>(Channel.CONFLATED)

    init {
        scope?.launch {
            for (poke in pokes) {
                try {
                    sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("RelayMembershipServer") { "NIP-43 republish failed: ${e.message}" }
                }
            }
        }
    }

    /**
     * Schedules a [sync] on the background scope without waiting for it.
     * Conflated: a burst of mutations collapses into one reconcile. Wire it
     * to [BanStore]'s mutation hook so changes made outside the RPC and
     * join/leave paths are published too.
     */
    fun requestSync() {
        pokes.trySend(Unit)
    }

    override suspend fun handle(
        event: Event,
        ctx: RequestContext,
    ): OkMessage? =
        when (event.kind) {
            RelayJoinRequestEvent.KIND -> join(event)
            RelayLeaveRequestEvent.KIND -> leave(event)
            else -> null
        }

    /** Processes a kind 28934 join request and returns the `OK` to answer it with. */
    suspend fun join(event: Event): OkMessage {
        validateRequest(event)?.let { return it }

        val claim =
            event.tags.claim()
                ?: return OkMessage.rejected(event.id, MachineReadablePrefix.RESTRICTED, "a join request needs a claim tag with an invite code.")

        if (banStore.isBanned(event.pubKey)) {
            return OkMessage.rejected(event.id, MachineReadablePrefix.RESTRICTED, "you are banned from this relay.")
        }
        if (banStore.isAllowedPubkey(event.pubKey)) {
            return OkMessage(event.id, true, MachineReadablePrefix.DUPLICATE.format("you are already a member of this relay."))
        }
        if (!banStore.isValidClaim(claim)) {
            return OkMessage.rejected(event.id, MachineReadablePrefix.RESTRICTED, "that is an invalid invite code.")
        }

        banStore.allowPubkey(event.pubKey, "$JOIN_REASON_PREFIX$claim")
        sync()

        val welcome = relayName?.let { "welcome to $it!" } ?: "welcome!"
        return OkMessage(event.id, true, "info: $welcome")
    }

    /** Processes a kind 28936 leave request and returns the `OK` to answer it with. */
    suspend fun leave(event: Event): OkMessage {
        validateRequest(event)?.let { return it }

        if (!event.tags.isProtected()) {
            return OkMessage.rejected(event.id, MachineReadablePrefix.INVALID, "a leave request must carry a NIP-70 \"-\" tag.")
        }
        if (!banStore.isAllowedPubkey(event.pubKey)) {
            return OkMessage(event.id, true, MachineReadablePrefix.DUPLICATE.format("you are not a member of this relay."))
        }

        banStore.unallowPubkey(event.pubKey)
        sync()

        return OkMessage(event.id, true, "info: you have left this relay.")
    }

    private fun validateRequest(event: Event): OkMessage? {
        if (!event.verify()) {
            return OkMessage.rejected(event.id, MachineReadablePrefix.INVALID, "bad signature or id.")
        }
        if (abs(clock() - event.createdAt) > requestWindowSeconds) {
            return OkMessage.rejected(
                event.id,
                MachineReadablePrefix.INVALID,
                "created_at must be within $requestWindowSeconds seconds of the relay's clock.",
            )
        }
        return null
    }

    /**
     * Publishes whatever the [BanStore] changed since the last publish (see
     * the class docs). Serialized; safe to call from any coroutine.
     */
    suspend fun sync() {
        mutex.withLock {
            if (!loaded) {
                loadPublished()
                loaded = true
            }
            // Roles first, so a 13534 never references a role id whose
            // definition hasn't been published yet.
            syncRoles()
            syncMembers()
        }
    }

    private suspend fun loadPublished() {
        load(Filter(kinds = listOf(RelayMembershipListEvent.KIND), authors = listOf(selfPubKey)))
            .maxByOrNull { it.createdAt }
            ?.let {
                publishedMembers = it.tags.membersWithRoles()
                membersCreatedAt = it.createdAt
            }

        load(Filter(kinds = listOf(RelayRoleEvent.KIND), authors = listOf(selfPubKey)))
            .sortedBy { it.createdAt }
            .forEach { event ->
                val id = event.tags.dTag()
                publishedRoles[id] = roleOf(id, event)
                roleCreatedAt[id] = max(roleCreatedAt[id] ?: 0L, event.createdAt)
            }

        // A deleted role's address stays tombstoned up to the deletion's
        // created_at, so a re-created role must be stamped after it.
        val rolePrefix = "${RelayRoleEvent.KIND}:$selfPubKey:"
        load(Filter(kinds = listOf(DeletionRequestEvent.KIND), authors = listOf(selfPubKey))).forEach { deletion ->
            deletion.tags.forEach { tag ->
                val address = ATag.parseAddressId(tag)
                if (address != null && address.startsWith(rolePrefix)) {
                    val id = address.substring(rolePrefix.length)
                    roleCreatedAt[id] = max(roleCreatedAt[id] ?: 0L, deletion.createdAt)
                }
            }
        }
    }

    private fun roleOf(
        id: String,
        event: Event,
    ) = RelayRole(
        id = id,
        label = event.tags.roleLabel(),
        description = event.tags.roleDescription(),
        color = event.tags.roleColor(),
        order = event.tags.roleOrder(),
    )

    private suspend fun syncRoles() {
        val desired = banStore.listRoles()
        val desiredIds = HashSet<String>(desired.size)

        for (role in desired) {
            desiredIds.add(role.id)
            if (publishedRoles[role.id] == role) continue
            val createdAt = nextCreatedAt(roleCreatedAt[role.id])
            if (publish(signer.sign(RelayRoleEvent.build(role, createdAt)))) {
                publishedRoles[role.id] = role
                roleCreatedAt[role.id] = createdAt
            }
        }

        val deleted = publishedRoles.keys.filter { it !in desiredIds }
        for (id in deleted) {
            val createdAt = nextCreatedAt(roleCreatedAt[id])
            val deletion =
                eventTemplate<DeletionRequestEvent>(DeletionRequestEvent.KIND, "", createdAt) {
                    add(ATag.assemble(RelayRoleEvent.KIND, selfPubKey, id, null))
                    kind(RelayRoleEvent.KIND)
                }
            if (publish(signer.sign(deletion))) {
                publishedRoles.remove(id)
                roleCreatedAt[id] = createdAt
            }
        }
    }

    private suspend fun syncMembers() {
        val desired = banStore.listAllowedPubkeys().map { (pk, _) -> RelayMember(pk, banStore.rolesOf(pk)) }
        if (membersCreatedAt > 0 && desired.toSet() == publishedMembers.toSet()) return

        val before = publishedMembers.mapTo(HashSet()) { it.pubKey }
        val after = desired.mapTo(HashSet()) { it.pubKey }

        // One event per pubkey, as in NIP-43's examples.
        for (member in desired) {
            if (member.pubKey !in before) {
                publish(signer.sign(RelayAddMemberEvent.build(listOf(member.pubKey), clock())))
            }
        }
        for (member in publishedMembers) {
            if (member.pubKey !in after) {
                publish(signer.sign(RelayRemoveMemberEvent.build(listOf(member.pubKey), clock())))
            }
        }

        val createdAt = nextCreatedAt(membersCreatedAt.takeIf { it > 0 })
        if (publish(signer.sign(RelayMembershipListEvent.buildWithRoles(desired, createdAt)))) {
            publishedMembers = desired
            membersCreatedAt = createdAt
        }
    }

    private fun nextCreatedAt(previous: Long?): Long = max(clock(), (previous ?: 0L) + 1)

    companion object {
        /** "Now, plus or minus a few minutes." */
        const val DEFAULT_REQUEST_WINDOW_SECONDS = 300L

        /** Allow-list reason recorded for a pubkey admitted by a join request, followed by the invite code. */
        const val JOIN_REASON_PREFIX = "nip43 join with invite code: "
    }
}
