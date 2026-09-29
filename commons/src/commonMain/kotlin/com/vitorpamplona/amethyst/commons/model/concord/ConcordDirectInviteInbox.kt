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
package com.vitorpamplona.amethyst.commons.model.concord

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordDirectInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteVend
import com.vitorpamplona.quartz.concord.cord05Invites.OpenedDirectInvite
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * One parked Direct Invite as the UI renders it (CORD-05 §6): who sent it (seal-verified), what it
 * opens (name/icon preview from the bundle), whether its `expires_at` has passed, and whether it
 * is a [catchUp] — a Private Channel key for a community this account already holds on the same
 * base, which accepting merges in without moving the base or announcing a new Join.
 */
@Immutable
class ConcordDirectInviteView(
    val opened: OpenedDirectInvite,
    val catchUp: Boolean,
    val expired: Boolean,
) {
    val wrapId: HexKey get() = opened.wrapId
    val sender: HexKey get() = opened.sender
    val invite: CommunityInvite get() = opened.invite
    val communityId: HexKey get() = opened.invite.communityId
    val name: String get() = opened.invite.name
    val icon: ImagePointer? get() = opened.invite.icon

    /** Names of the Private Channels the bundle carries (what a catch-up would add). */
    val channelNames: List<String> get() =
        opened.invite.channels
            .filter { it.key.isNotBlank() }
            .map { it.name }
}

/** What accepting a Direct Invite does; see [ConcordDirectInviteInbox.acceptPlan]. */
sealed interface DirectInviteAcceptPlan {
    /** `expires_at` has passed: the preview renders, joining refuses. */
    data object Expired : DirectInviteAcceptPlan

    /** A community we don't hold: run the shared join path. */
    data object Join : DirectInviteAcceptPlan

    /**
     * A held community: store [entry] — the held one plus the newly granted Private Channel keys
     * ([channelIds]). A writer re-applies [channelIds] to the entry it reads inside the List write
     * ([ConcordInviteVend.adoptCatchUp] with `only`), never [entry] itself, which is a snapshot.
     */
    class CatchUp(
        val entry: ConcordCommunityListEntry,
        val channelIds: List<HexKey>,
    ) : DirectInviteAcceptPlan

    /** A held community the bundle adds nothing to (or can't: a different base, or dissolved). */
    data object NothingNew : DirectInviteAcceptPlan

    /** The held community's roster bans us. */
    data object Banned : DirectInviteAcceptPlan

    /** The held community's roster isn't folded yet, so the ban verdict is unknown: wait. */
    data object RosterNotLoaded : DirectInviteAcceptPlan
}

/**
 * The Direct Invite inbox (CORD-05 §6) — headless, shared by the app and `amy`.
 *
 * Wraps arrive from anywhere — a `{"kinds":[1059],"#p":[me],"#k":["3313"]}` sweep
 * ([com.vitorpamplona.amethyst.commons.actions.ConcordActions.directInvitesFilter]), or the general
 * NIP-17 giftwrap pipeline, which honours an untagged invite all the same — and are [offer]ed here.
 * The inbox opens each wrap once (two NIP-44 decrypts), dedupes by wrap id, drops a wrap whose NIP-40
 * `expiration` has passed, validates the bundle exactly like a fetched one, and parks it in
 * [pending]. **Nothing** else happens: no relay connection, no icon fetch, no Join, until the user
 * accepts (the caller's join path) or [decline]s.
 *
 * Declined wrap ids are remembered ([declined], restorable via [restoreDeclined]) so a re-delivered
 * wrap never resurfaces. [newestWrapCreatedAt] is the sweep cursor; query from [since], which
 * rewinds it by NIP-59's two-day backdate window.
 */
class ConcordDirectInviteInbox(
    private val signer: NostrSigner,
) {
    private val mutex = Mutex()

    /** Wrap ids already handled this session (opened, refused, or expired), oldest first. */
    private val seen = LinkedHashSet<HexKey>()

    private val _pending = MutableStateFlow<Map<HexKey, OpenedDirectInvite>>(emptyMap())

    /** Parked invites by wrap id, as opened. See [visible] for what a UI should show. */
    val pending: StateFlow<Map<HexKey, OpenedDirectInvite>> = _pending.asStateFlow()

    private val _declined = MutableStateFlow<Set<HexKey>>(emptySet())

    /** Wrap ids the user declined; persisted by the front end so they stay declined across restarts. */
    val declined: StateFlow<Set<HexKey>> = _declined.asStateFlow()

    /** The newest wrap `created_at` offered so far (the sweep cursor), or null on a cold inbox. */
    @Volatile
    var newestWrapCreatedAt: Long? = null
        private set

    /** The `since` for the next sweep: the cursor rewound by the backdate window (null = everything). */
    fun since(): Long? = ConcordDirectInvite.inboxSince(newestWrapCreatedAt)

    /** Replaces the declined set — used to restore it from disk at startup. Drops any pending one. */
    fun restoreDeclined(wrapIds: Set<HexKey>) {
        _declined.value = wrapIds
        _pending.update { current -> current.filterKeys { it !in wrapIds } }
    }

    /**
     * Considers one kind-1059 [wrap] addressed to us. Returns the parked invite (new or already
     * pending), or null when it isn't one: not a direct invite for us, a forgery, an invalid
     * bundle, an expired handoff, or a wrap the user already declined. Never throws.
     */
    suspend fun offer(
        wrap: Event,
        nowSecs: Long = TimeUtils.now(),
    ): OpenedDirectInvite? = admit(wrap, nowSecs) { ConcordDirectInvite.open(wrap, signer) }

    /**
     * [offer] for a pipeline that already peeled [wrap] down to its kind-13 [seal] (the NIP-17
     * giftwrap inbox). [wrap] only lends its id, `created_at` and tags, so a content-stripped copy
     * is fine; the seal is re-opened with the anti-spoofing check the generic unseal skips.
     */
    suspend fun offerSeal(
        wrap: Event,
        seal: Event,
        nowSecs: Long = TimeUtils.now(),
    ): OpenedDirectInvite? = admit(wrap, nowSecs) { ConcordDirectInvite.openSeal(wrap.id, seal, signer) }

    private suspend fun admit(
        wrap: Event,
        nowSecs: Long,
        open: suspend () -> OpenedDirectInvite?,
    ): OpenedDirectInvite? {
        if (wrap.kind != GiftWrapEvent.KIND) return null
        mutex.withLock {
            val newest = newestWrapCreatedAt
            if (newest == null || wrap.createdAt > newest) newestWrapCreatedAt = wrap.createdAt
            _pending.value[wrap.id]?.let { return it }
            if (wrap.id in _declined.value || wrap.id in seen) return null
            remember(wrap.id)
        }
        // An expired handoff is never decrypted or surfaced (NIP-40 on the wrap mirrors expires_at).
        if (ConcordDirectInvite.isWrapExpired(wrap, nowSecs)) return null
        val opened = open() ?: return null
        mutex.withLock {
            if (wrap.id in _declined.value) return null
            _pending.update { it + (wrap.id to opened) }
        }
        return opened
    }

    /** The parked invite behind [wrapId], if any. */
    fun get(wrapId: HexKey): OpenedDirectInvite? = _pending.value[wrapId.lowercase()] ?: _pending.value[wrapId]

    /** Discards [wrapId] for good (CORD-05 §6 "declining means discarding them"). False if not pending. */
    fun decline(wrapId: HexKey): Boolean {
        val id = get(wrapId)?.wrapId ?: return false
        _pending.update { it - id }
        _declined.update { it + id }
        return true
    }

    /** Drops [wrapId] after it was accepted; this session will not re-park it. */
    fun resolve(wrapId: HexKey) {
        _pending.update { it - wrapId }
    }

    private fun remember(wrapId: HexKey) {
        if (seen.size >= SEEN_CAP) {
            val drop = seen.take(SEEN_CAP / 2)
            seen.removeAll(drop.toSet())
        }
        seen.add(wrapId)
    }

    companion object {
        /** Cap on remembered wrap ids; the oldest half is shed past it (a sweep re-dedupes deeper). */
        const val SEEN_CAP = 4096

        /**
         * What accepting [opened] should do (CORD-05 §6), given the community entry this account
         * already [held] (if any) and its folded [heldState]:
         *  - past `expires_at` → [DirectInviteAcceptPlan.Expired] ("`expires_at` refuses a late join");
         *  - not held → [DirectInviteAcceptPlan.Join] (the shared join path, which still ban-gates
         *    against the community's own Control Plane);
         *  - held on the SAME base with new Private Channel keys → [DirectInviteAcceptPlan.CatchUp],
         *    the held entry with only those keys ADDED — never moving the base, never replacing a
         *    held key (Armada `catchUpChannelIds`) — and only from a sender who is staff in the held
         *    fold, for channels it knows as live Private Channels
         *    ([ConcordInviteVend.admissibleCatchUpIds]); refused when the held roster bans [me], and
         *    while it isn't folded ([DirectInviteAcceptPlan.RosterNotLoaded]);
         *  - held otherwise (nothing new, a different base, dissolved) → [DirectInviteAcceptPlan.NothingNew].
         */
        fun acceptPlan(
            opened: OpenedDirectInvite,
            held: ConcordCommunityListEntry?,
            heldState: ConcordCommunityState?,
            me: HexKey,
            nowMs: Long = TimeUtils.nowMillis(),
        ): DirectInviteAcceptPlan {
            if (opened.isExpired(nowMs)) return DirectInviteAcceptPlan.Expired
            if (held == null) return DirectInviteAcceptPlan.Join
            if (ConcordInviteVend.catchUpChannelIds(held, opened.invite).isEmpty()) return DirectInviteAcceptPlan.NothingNew
            if (heldState == null) return DirectInviteAcceptPlan.RosterNotLoaded
            // Death wins every race (CORD-02 §9): a dissolved community takes no new keys.
            if (heldState.dissolved) return DirectInviteAcceptPlan.NothingNew
            if (heldState.authority.isBanned(me)) return DirectInviteAcceptPlan.Banned
            val ids = ConcordInviteVend.admissibleCatchUpIds(held, opened.invite, heldState.authority, heldState.privateChannelIds, opened.sender)
            if (ids.isEmpty()) return DirectInviteAcceptPlan.NothingNew
            val adopted = ConcordInviteVend.adoptCatchUp(held, opened.invite, ids) ?: return DirectInviteAcceptPlan.NothingNew
            return DirectInviteAcceptPlan.CatchUp(adopted, ids)
        }

        /**
         * What a UI shows out of [pending], given the communities this account already holds
         * ([joined]): newest first, with
         *  - an invite for a community already held on the SAME base that carries a Private Channel
         *    key it lacks kept as a [ConcordDirectInviteView.catchUp];
         *  - any other invite for a held community (nothing new, or a different base — which may
         *    never move the held one) hidden;
         *  - one invite per community (newest `sentAt`, ties by wrap id), catch-ups keyed by their
         *    channel set too since each may vend a key no other wrap carries (Armada
         *    `dedupeParkedInvites`).
         */
        fun visible(
            pending: Collection<OpenedDirectInvite>,
            joined: List<ConcordCommunityListEntry>,
            nowMs: Long = TimeUtils.nowMillis(),
        ): List<ConcordDirectInviteView> {
            val heldById = joined.associateBy { it.id.lowercase() }
            val byKey = LinkedHashMap<String, ConcordDirectInviteView>()
            for (opened in pending) {
                val communityId = opened.invite.communityId.lowercase()
                val held = heldById[communityId]
                val newChannels = ConcordInviteVend.catchUpChannelIds(held, opened.invite)
                if (held != null && newChannels.isEmpty()) continue
                val catchUp = held != null
                val key = if (catchUp) communityId + "|" + newChannels.sorted().joinToString(",") else communityId
                val view = ConcordDirectInviteView(opened, catchUp, opened.isExpired(nowMs))
                val existing = byKey[key]
                if (existing == null ||
                    opened.sentAt > existing.opened.sentAt ||
                    (opened.sentAt == existing.opened.sentAt && opened.wrapId < existing.opened.wrapId)
                ) {
                    byKey[key] = view
                }
            }
            return byKey.values.sortedWith(compareByDescending<ConcordDirectInviteView> { it.opened.sentAt }.thenBy { it.wrapId })
        }
    }
}
