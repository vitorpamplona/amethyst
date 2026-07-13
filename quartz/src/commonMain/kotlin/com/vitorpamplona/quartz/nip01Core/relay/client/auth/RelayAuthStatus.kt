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
package com.vitorpamplona.quartz.nip01Core.relay.client.auth

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.concurrent.Volatile

class RelayAuthStatus {
    // Keeps track of auth responses to update the relay with all filters
    // after the authentication happen.
    // Sized generously: one connection may authenticate as many identities at once — the
    // user plus every Concord plane stream key hosted on that relay (control + channels) —
    // and if older entries roll off, OK-tracking / hasFinishedAllAuths() accounting degrades.
    private val authResponseWatcher: LruCache<HexKey, AuthEventReceiptStatus> = LruCache(200)

    // Avoids sending multiple replies for each auth.
    private val uniqueAuthChallengesSent: LruCache<ChallengePair, ChallengePair> = LruCache(200)

    // Latest epoch-second at which a tracked AUTH event received a successful OK.
    // Read by RelayAuthSnapshot consumers for staleness checks (e.g. proactive
    // re-AUTH on window focus).
    @Volatile
    private var lastAuthSuccessAt: Long? = null

    // The most recent challenge the relay sent on this connection. NIP-42: the challenge
    // "is valid for the duration of the connection or until another challenge is sent",
    // and a client "must have a stored challenge associated with that relay so it can act
    // upon that in response to the auth-required CLOSED message". We keep it so a REQ that
    // is refused with `auth-required:` AFTER the initial AUTH (e.g. a Concord channel-plane
    // REQ mounted once the control plane folds and reveals new stream keys) can be
    // re-authenticated with the folded-in keys without waiting for the relay to re-challenge.
    @Volatile
    private var lastChallenge: String? = null

    fun rememberChallenge(challenge: String) {
        lastChallenge = challenge
    }

    fun lastChallenge(): String? = lastChallenge

    enum class AuthEventReceiptStatus {
        AUTHENTICATING,
        AUTHENTICATED,
        NOT_AUTHENTICATED,
    }

    data class ChallengePair(
        val user: HexKey,
        val challenge: String,
    )

    fun saveAuthSubmission(authEvent: RelayAuthEvent): Boolean {
        val challenge = authEvent.challenge() ?: return false

        val challengePair = ChallengePair(authEvent.pubKey, challenge)

        // only send replies to new challenges to avoid infinite loop:
        return if (uniqueAuthChallengesSent[challengePair] == null) {
            authResponseWatcher.put(authEvent.id, AuthEventReceiptStatus.AUTHENTICATING)
            uniqueAuthChallengesSent.put(challengePair, challengePair)
            true
        } else {
            false
        }
    }

    fun checkAuthResults(
        eventId: HexKey,
        success: Boolean,
    ): Boolean {
        val wasAlreadyAuthenticated = authResponseWatcher[eventId]
        return if (wasAlreadyAuthenticated != null) {
            if (success) {
                authResponseWatcher.put(eventId, AuthEventReceiptStatus.AUTHENTICATED)
                lastAuthSuccessAt = TimeUtils.now()
            } else {
                authResponseWatcher.put(eventId, AuthEventReceiptStatus.NOT_AUTHENTICATED)
            }

            wasAlreadyAuthenticated != AuthEventReceiptStatus.AUTHENTICATED && success
        } else {
            false
        }
    }

    fun hasFinishedAllAuths() = authResponseWatcher.snapshot().all { it.value != AuthEventReceiptStatus.AUTHENTICATING }

    /**
     * Build an immutable Compose-stable snapshot of the current per-relay AUTH
     * state. The phase is derived from the response watcher:
     *
     * - any AUTHENTICATING entry → [RelayAuthSnapshot.Phase.AUTHENTICATING]
     * - else any AUTHENTICATED entry → [RelayAuthSnapshot.Phase.AUTHENTICATED]
     * - else any NOT_AUTHENTICATED entry → [RelayAuthSnapshot.Phase.AUTH_FAILED]
     * - else (no tracked challenges) → [RelayAuthSnapshot.Phase.IDLE]
     *
     * The watcher LRU caps at 10 entries; a long-running connection that has
     * already AUTHed will still report AUTHENTICATED even after older entries
     * roll off, because the LRU keeps the most recent.
     */
    fun snapshot(): RelayAuthSnapshot {
        val entries = authResponseWatcher.snapshot()
        val phase =
            when {
                entries.isEmpty() -> RelayAuthSnapshot.Phase.IDLE
                entries.values.any { it == AuthEventReceiptStatus.AUTHENTICATING } -> RelayAuthSnapshot.Phase.AUTHENTICATING
                entries.values.any { it == AuthEventReceiptStatus.AUTHENTICATED } -> RelayAuthSnapshot.Phase.AUTHENTICATED
                else -> RelayAuthSnapshot.Phase.AUTH_FAILED
            }
        return RelayAuthSnapshot(phase, lastAuthSuccessAt)
    }
}
