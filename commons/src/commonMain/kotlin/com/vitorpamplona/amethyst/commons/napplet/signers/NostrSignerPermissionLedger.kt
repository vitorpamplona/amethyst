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

import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The per-app Nostr signer permission ledger. Decides whether a signing or encryption
 * operation should auto-allow, auto-deny, or ask the user, by consulting:
 *
 * 1. Per-operation overrides ([NostrSignerPermissionStore.loadOpDecision]) — these always win.
 * 2. The app's [AppSignerPolicy] trust level ([NostrSignerPermissionStore.loadPolicy]).
 * 3. The built-in "reasonable" set (kind 1/6/7 are auto-allowed) when policy is [AppSignerPolicy.REASONABLE].
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
                when (op.kind) {
                    1 -> NostrOpDecision.ALLOW // Short text notes: common, low-risk
                    6 -> NostrOpDecision.ALLOW // Reposts
                    7 -> NostrOpDecision.ALLOW // Reactions / emoji
                    else -> NostrOpDecision.ASK
                }
            NostrSignerOp.Encrypt -> NostrOpDecision.ASK
            NostrSignerOp.Decrypt -> NostrOpDecision.ASK
        }
}
