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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What is currently wrong — or being fixed — with one joined Concord community, as one value.
 *
 * Everything here describes the community as a whole rather than a single channel, which is why it
 * belongs above the feed instead of in the composer's slot ([com.vitorpamplona.amethyst.commons.model.chats.PostingGate]
 * answers the narrower "may I post here"). A dissolved or stranded community explains an *empty
 * feed*, and an explanation for an empty feed cannot live below it.
 *
 * Deliberately one value, not a set: a screen shows at most one of these. Two stacked banners is how
 * a status surface turns into chrome that users learn to scroll past.
 */
sealed interface ConcordCommunityHealth {
    /** Nothing to say. Renders no banner at all. */
    data object Healthy : ConcordCommunityHealth

    /**
     * A CORD-06 Refounding happened without us: the rotation's chunks were all present at our
     * next-epoch rekey address and none carried a blob for this account, so we are sitting on a dead
     * root at [strandedAtEpoch] while the community moved to [newEpoch].
     *
     * This is the state that was previously invisible — the community simply looked quiet, because
     * every plane address we derive is dead and a dead address returns nothing rather than erroring.
     *
     * [recoverable] is whether an automatic way back exists: the membership must carry the invite
     * link it was joined through (`inviteRef`), which is the only anchor that survives a rotation we
     * were not party to. Without it there is nothing to re-resolve and only a fresh invite helps.
     */
    data class Stranded(
        val strandedAtEpoch: Long,
        val newEpoch: Long,
        val recoverable: Boolean,
    ) : ConcordCommunityHealth

    /**
     * Recovery is running: we are re-resolving the stored invite link to pick up the root we missed.
     * Transient and expected — the honest thing to show while it works is progress, not an error.
     */
    data class CatchingUp(
        val fromEpoch: Long,
    ) : ConcordCommunityHealth

    /**
     * We were left out and cannot get back in on our own: the invite link we joined through no longer
     * resolves to a live bundle ([reason] says why). A human has to send a new invite.
     */
    data class RecoveryFailed(
        val reason: Reason,
    ) : ConcordCommunityHealth {
        enum class Reason {
            /** The owner retired the invite link (a `vsk=9` tombstone). */
            LINK_REVOKED,

            /** The link's `expires_at` has passed. */
            LINK_EXPIRED,

            /** The bundle is unreadable — wrong token, or a newer format than we parse. */
            LINK_UNREADABLE,

            /** The membership carries no invite link at all, so there is nothing to re-resolve. */
            NO_ANCHOR,
        }
    }

    /**
     * The community was sealed read-only by an owner-signed tombstone (CORD-02 §9). Held keys still
     * open the history; nothing new is honored.
     *
     * Also surfaced in the composer's place while a channel is open, but that only covers a channel
     * you have already entered — the banner is what explains the community's own screens.
     */
    data object Dissolved : ConcordCommunityHealth
}

/**
 * Per-community [ConcordCommunityHealth], written by the account's rekey drain and recovery sweep and
 * read by the UI.
 *
 * Held outside `ConcordCommunitySession` on purpose: recovery outcomes outlive any one session
 * instance (a successful merge replaces the entry and rebuilds the session, and a banner reporting
 * that must survive that rebuild), and a community that failed to recover may have no working session
 * at all.
 */
class ConcordCommunityHealthState {
    private val lock = KmpLock()
    private val flows = HashMap<HexKey, MutableStateFlow<ConcordCommunityHealth>>()

    /** This community's live health. Creating the flow on read lets the UI subscribe before any write. */
    fun flowFor(communityId: HexKey): StateFlow<ConcordCommunityHealth> = flowForInternal(communityId)

    fun currentFor(communityId: HexKey): ConcordCommunityHealth = flowForInternal(communityId).value

    fun set(
        communityId: HexKey,
        health: ConcordCommunityHealth,
    ) {
        flowForInternal(communityId).value = health
    }

    /**
     * Clears a community back to [ConcordCommunityHealth.Healthy], but **only** if it currently holds
     * [ifCurrently]. Used to retract a transient state (a catch-up that finished) without stomping a
     * more important one that landed meanwhile.
     */
    fun clearIf(
        communityId: HexKey,
        ifCurrently: (ConcordCommunityHealth) -> Boolean,
    ) {
        val flow = flowForInternal(communityId)
        if (ifCurrently(flow.value)) flow.value = ConcordCommunityHealth.Healthy
    }

    fun clear() =
        lock.withLock {
            flows.values.forEach { it.value = ConcordCommunityHealth.Healthy }
        }

    private fun flowForInternal(communityId: HexKey): MutableStateFlow<ConcordCommunityHealth> =
        lock.withLock {
            flows.getOrPut(communityId) { MutableStateFlow(ConcordCommunityHealth.Healthy) }
        }
}
