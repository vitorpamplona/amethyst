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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot

/**
 * Stranded recovery (CORD-05/06).
 *
 * A Refounding carries only `(newRoot, newEpoch, rotator)` — there is **no
 * recipient list** — so a member who is simply left out of the rekey recipient
 * set receives nothing and is silently stranded on the dead epoch forever, while
 * everyone else moves on. This is true of any member, the owner included, and
 * cannot be prevented on the receive side.
 *
 * The way out is the invite link the membership was joined through
 * ([ConcordCommunityListEntry.inviteRef]): the community keeps publishing its
 * bundle at that same addressable coordinate, re-minted at the current epoch. So
 * a member who re-resolves their own join link and finds a **higher** epoch than
 * the one they hold knows they were left behind, and can merge forward.
 *
 * This object holds only the pure decision + merge; fetching and unlocking the
 * bundle at the link is the caller's job.
 */
object ConcordStrandedRecovery {
    /**
     * True when [bundle], resolved at [entry]'s stored invite link, proves we were
     * left behind: it must describe the same community and sit at a strictly higher
     * epoch. Same or lower is a no-op (we are current, or the bundle is stale).
     */
    fun isStranded(
        entry: ConcordCommunityListEntry,
        bundle: CommunityInvite,
    ): Boolean =
        entry.inviteRef != null &&
            bundle.communityId.equals(entry.id, ignoreCase = true) &&
            bundle.rootEpoch > entry.rootEpoch

    /**
     * Merges [entry] forward onto the higher-epoch [bundle], or returns null when
     * there is nothing to do ([isStranded] is false) — so the caller can treat null
     * as "stay put" without a second check.
     *
     * The merge is epoch-monotonic (it never moves backwards, by construction of
     * [isStranded]) and preserves two things the naive "adopt the bundle" would
     * destroy:
     *
     * - the [ConcordCommunityListEntry.inviteRef] anchor, so the next Refounding we
     *   are left out of is recoverable too; and
     * - the existing [ConcordCommunityListEntry.heldRoots], plus the root we are
     *   leaving, so prior-epoch history the member legitimately holds stays
     *   derivable instead of going dark on catch-up.
     */
    fun mergeForward(
        entry: ConcordCommunityListEntry,
        bundle: CommunityInvite,
    ): ConcordCommunityListEntry? {
        if (!isStranded(entry, bundle)) return null

        val held = (entry.heldRoots + HeldRoot(entry.rootEpoch, entry.root)).distinctBy { it.epoch }

        return ConcordCommunityListEntry(
            id = entry.id,
            owner = entry.owner,
            ownerSalt = entry.ownerSalt,
            root = bundle.communityRoot,
            rootEpoch = bundle.rootEpoch,
            heldRoots = held,
            privateChannels = entry.privateChannels,
            relays = if (bundle.relays.isNotEmpty()) bundle.relays else entry.relays,
            name = entry.name.ifEmpty { bundle.name },
            addedAt = entry.addedAt,
            inviteRef = entry.inviteRef,
            // We were excluded from the epoch we were sitting on when we found the gap.
            excludedAtEpoch = entry.rootEpoch,
            // Unknown keys another client wrote are data we hold in trust: carry them forward,
            // or this recovery write silently deletes them.
            residue = entry.residue,
        )
    }
}
