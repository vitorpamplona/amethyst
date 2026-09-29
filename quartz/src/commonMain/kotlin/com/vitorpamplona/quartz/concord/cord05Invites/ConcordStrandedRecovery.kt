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
 * Stranded detection (CORD-05/06).
 *
 * A Refounding carries only `(newRoot, newEpoch, rotator)` — there is **no
 * recipient list** — so a member who is simply left out of the rekey recipient
 * set receives nothing and is silently stranded on the dead epoch, while everyone
 * else moves on. The invite link the membership was joined through
 * ([ConcordCommunityListEntry.inviteRef]) is re-minted at the current epoch by its
 * creator, so re-resolving it and finding a **higher** epoch tells a member they
 * were left behind.
 *
 * What a bundle may NOT do is move the base (CORD-06 §2, and the reference client's
 * `isCatchUpBundle`: "It may never move the base"). Nothing binds `community_root`
 * to `community_id`, so a bundle is not proof of continuity: a link creator — or
 * anyone who ever held a link's signer — could serve a higher-epoch bundle carrying
 * a root of their own and silently relocate every member who joined through that
 * link onto streams they read. The base advances only by a CORD-06 §2 rekey blob
 * whose `prevcommit` proves it extends the key we hold, from a rotator our roster
 * authorizes. So this object only **detects** ([isStranded]); the background sweep
 * never adopts anything, and the one way forward from a bundle is the user
 * explicitly accepting the link again ([rejoinForward]) — the same trust decision
 * as the first join, never taken on their behalf.
 */
object ConcordStrandedRecovery {
    /**
     * True when [bundle], resolved at [entry]'s stored invite link, says we were
     * left behind: it must describe the same community and sit at a strictly higher
     * epoch. Same or lower is a no-op (we are current, or the bundle is stale).
     *
     * [bannedAtCurrentEpoch] is the caller's answer to "does the community, as I fold
     * it right now, have me on its banlist?" — and a `true` answers false outright:
     * a removed member is not stranded, they are removed.
     */
    fun isStranded(
        entry: ConcordCommunityListEntry,
        bundle: CommunityInvite,
        bannedAtCurrentEpoch: Boolean,
    ): Boolean =
        !bannedAtCurrentEpoch &&
            entry.inviteRef != null &&
            bundle.communityId.equals(entry.id, ignoreCase = true) &&
            bundle.rootEpoch > entry.rootEpoch

    /**
     * The entry that results from the user **explicitly** re-accepting the invite
     * link [bundle] was resolved from, while stranded on [entry] — or null when
     * [isStranded] is false. Never call this from a background sweep: adopting a
     * bundle's root is a join decision (see the class note), and only the user can
     * make it.
     *
     * The merge is epoch-monotonic and preserves what a fresh join would lose: the
     * [ConcordCommunityListEntry.inviteRef] anchor, and the existing
     * [ConcordCommunityListEntry.heldRoots] plus the root we are leaving, so
     * prior-epoch history stays derivable.
     */
    fun rejoinForward(
        entry: ConcordCommunityListEntry,
        bundle: CommunityInvite,
        bannedAtCurrentEpoch: Boolean,
    ): ConcordCommunityListEntry? {
        if (!isStranded(entry, bundle, bannedAtCurrentEpoch)) return null

        // Bank the epoch we are leaving with its control_pk, so its Control Plane
        // stays re-subscribable for the anti-rollback floor (a split epoch's address
        // is held, never derivable — CORD-02 §2).
        val held = (entry.heldRoots + HeldRoot(entry.rootEpoch, entry.root, entry.controlPk, entry.controlRoot)).distinctBy { it.epoch to it.key.lowercase() }

        return ConcordCommunityListEntry(
            id = entry.id,
            owner = entry.owner,
            ownerSalt = entry.ownerSalt,
            root = bundle.communityRoot,
            rootEpoch = bundle.rootEpoch,
            // The re-minted bundle carries the new epoch's control_pk (CORD-05 §1);
            // absent means the community is (still) legacy at that epoch.
            controlPk = bundle.controlPk,
            heldRoots = held,
            privateChannels = entry.privateChannels,
            relays = if (bundle.relays.isNotEmpty()) bundle.relays else entry.relays,
            name = entry.name.ifEmpty { bundle.name },
            addedAt = entry.addedAt,
            inviteRef = entry.inviteRef,
            // Unknown keys another client wrote are data we hold in trust: carry them forward,
            // or this write silently deletes them.
            residue = entry.residue,
        )
    }
}
