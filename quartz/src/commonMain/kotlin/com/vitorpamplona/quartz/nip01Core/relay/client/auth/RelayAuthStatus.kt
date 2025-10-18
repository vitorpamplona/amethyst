/**
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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent

class RelayAuthStatus {
    // Keeps track of auth responses to update the relay with all filters
    // after the authentication happen
    private val authResponseWatcher: MutableMap<HexKey, AuthEventReceiptStatus> = mutableMapOf()

    // Avoids sending multiple replies for each auth.
    private val uniqueAuthChallengesSent: MutableSet<ChallengePair> = mutableSetOf()

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
        val challenge = authEvent.challenge()
        if (challenge == null) return false

        val challengePair = ChallengePair(authEvent.pubKey, challenge)

        // only send replies to new challenges to avoid infinite loop:
        return if (challengePair !in uniqueAuthChallengesSent) {
            authResponseWatcher[authEvent.id] = AuthEventReceiptStatus.AUTHENTICATING
            uniqueAuthChallengesSent.add(challengePair)
            true
        } else {
            false
        }
    }

    fun checkAuthResults(
        eventId: HexKey,
        success: Boolean,
    ): Boolean =
        if (authResponseWatcher.containsKey(eventId)) {
            val wasAlreadyAuthenticated = authResponseWatcher[eventId]

            if (success) {
                authResponseWatcher.put(eventId, AuthEventReceiptStatus.AUTHENTICATED)
            } else {
                authResponseWatcher.put(eventId, AuthEventReceiptStatus.NOT_AUTHENTICATED)
            }

            wasAlreadyAuthenticated != AuthEventReceiptStatus.AUTHENTICATED && success
        } else {
            false
        }

    fun hasFinishedAllAuths() = authResponseWatcher.all { it.value != AuthEventReceiptStatus.AUTHENTICATING }
}
