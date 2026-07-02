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
package com.vitorpamplona.amethyst.commons.napplet.signers

import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The per-app Nostr signer permission ledger. Decides whether a signing or encryption
 * operation should auto-allow, auto-deny, or ask the user, by consulting:
 *
 * 1. Per-operation overrides ([NostrSignerPermissionStore.loadOpDecision]) — these always win.
 * 2. The app's [AppSignerPolicy] trust level ([NostrSignerPermissionStore.loadPolicy]).
 * 3. The built-in "reasonable" set ([REASONABLE_SIGN_KINDS] + encrypt are auto-allowed) when policy is [AppSignerPolicy.REASONABLE].
 *
 * When no policy has been set (`null`), [decide] returns [NostrOpDecision.ASK], which triggers the
 * first-connect dialog in the broker.
 */
class NostrSignerPermissionLedger(
    val store: NostrSignerPermissionStore,
) {
    /**
     * `true` when a trust level has been set for [coordinate] — i.e. the "Connect to Nostr"
     * dialog has already been shown and the user made a choice.
     */
    suspend fun hasPolicy(coordinate: String): Boolean = store.loadPolicy(coordinate) != null

    /**
     * The authorization verdict for ([coordinate], [op]) based on stored policy + per-op overrides.
     * Checks expiry: if a timed override has passed [now], it is cleared and the policy-level decision
     * is returned instead.
     */
    suspend fun decide(
        coordinate: String,
        op: NostrSignerOp,
        now: Long = TimeUtils.now(),
    ): NostrOpDecision {
        store.loadOpDecision(coordinate, op)?.let { decision ->
            val expiresAt = store.loadOpExpiry(coordinate, op)
            if (expiresAt != null && now > expiresAt) {
                store.clearOpDecision(coordinate, op)
                store.clearOpExpiry(coordinate, op)
            } else {
                return decision
            }
        }
        return when (store.loadPolicy(coordinate)) {
            AppSignerPolicy.FULL_TRUST -> NostrOpDecision.ALLOW
            AppSignerPolicy.PARANOID -> NostrOpDecision.ASK
            AppSignerPolicy.REASONABLE -> reasonableDecision(op)
            null -> NostrOpDecision.ASK
        }
    }

    /** Stores the user's chosen trust level for [coordinate]. */
    suspend fun setPolicy(
        coordinate: String,
        policy: AppSignerPolicy,
    ) = store.storePolicy(coordinate, policy)

    /** Stores a per-operation override for ([coordinate], [op]). */
    suspend fun setOpDecision(
        coordinate: String,
        op: NostrSignerOp,
        decision: NostrOpDecision,
    ) = store.storeOpDecision(coordinate, op, decision)

    /** Stores a time-bound per-operation override that expires at [expiresAt] (Unix epoch seconds). */
    suspend fun setTimedOpDecision(
        coordinate: String,
        op: NostrSignerOp,
        decision: NostrOpDecision,
        expiresAt: Long,
    ) {
        store.storeOpDecision(coordinate, op, decision)
        store.storeOpExpiry(coordinate, op, expiresAt)
    }

    /** Records the current time as the last-used timestamp for [coordinate]. */
    suspend fun updateLastUsed(
        coordinate: String,
        now: Long = TimeUtils.now(),
    ) = store.storeLastUsed(coordinate, now)

    /** The last-used timestamp for [coordinate], or `null` if never used. */
    suspend fun lastUsed(coordinate: String): Long? = store.loadLastUsed(coordinate)

    /** Removes a per-operation override (and any expiry), reverting to the policy-level decision. */
    suspend fun revokeOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    ) {
        store.clearOpDecision(coordinate, op)
        store.clearOpExpiry(coordinate, op)
    }

    /** Removes all signer permissions for [coordinate] — trust level and all per-op overrides. */
    suspend fun revokeAll(coordinate: String) = store.clearAll(coordinate)

    private fun reasonableDecision(op: NostrSignerOp): NostrOpDecision =
        when (op) {
            is NostrSignerOp.SignKind ->
                if (op.kind in REASONABLE_SIGN_KINDS) NostrOpDecision.ALLOW else NostrOpDecision.ASK
            NostrSignerOp.Encrypt -> NostrOpDecision.ALLOW
            NostrSignerOp.Decrypt -> NostrOpDecision.ASK
        }

    companion object {
        /**
         * Event kinds auto-approved under [AppSignerPolicy.REASONABLE].
         *
         * The rule for membership: an app may sign these on the user's behalf without a prompt only
         * when doing so **cannot harm the user**. Everything here is *additive, public, and
         * non-destructive* — creating a new note-like event that the user could delete afterwards, in
         * the same risk class as the original kind 1/6/7 set. None of them can silently:
         *  - spend money (zaps/nutzaps are gated separately and always prompt),
         *  - overwrite account configuration (profile 0, contacts 3, relay/mute/bookmark lists are
         *    replaceable — a bad write can wipe settings, so they stay ASK),
         *  - delete existing content (kind 5), or
         *  - leak private data (DMs and [NostrSignerOp.Decrypt] stay ASK).
         *
         * Deliberately conservative: when a kind's blast radius is unclear, it is left out so the user
         * is asked rather than surprised.
         */
        val REASONABLE_SIGN_KINDS: Set<Int> =
            setOf(
                TextNoteEvent.KIND, // 1 — short text notes & replies
                RepostEvent.KIND, // 6 — reposts of text notes
                ReactionEvent.KIND, // 7 — likes / emoji reactions
                GenericRepostEvent.KIND, // 16 — reposts of non-text content (same risk as kind 6)
                PictureEvent.KIND, // 20 — picture posts (same risk as kind 1)
                ChannelMessageEvent.KIND, // 42 — public chat messages
                HighlightEvent.KIND, // 9802 — highlighted snippets shared publicly
                CommentEvent.KIND, // 1111 — NIP-22 threaded comments (same risk as kind 1)
                StatusEvent.KIND, // 30315 — ephemeral user status / presence
            )
    }
}
