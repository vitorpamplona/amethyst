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

import androidx.compose.runtime.Immutable

/**
 * Compose-stable per-relay AUTH snapshot exposed by [RelayAuthenticator].
 *
 * The internal [RelayAuthStatus] is a mutable holder around concurrent LRU
 * caches — necessary for the per-relay OkHttp dispatcher, but unsuitable as
 * a [kotlinx.coroutines.flow.StateFlow] value (mutating it doesn't change
 * identity, so distinct-until-changed swallows updates).
 *
 * [RelayAuthSnapshot] is the immutable view downstream consumers (UI banner,
 * retry coordinator, indexer-fan-out gate) subscribe to.
 */
@Immutable
data class RelayAuthSnapshot(
    val phase: Phase,
    val lastAuthSuccessAt: Long?,
    /**
     * How many AUTHs have been accepted on this connection, counted monotonically.
     *
     * [phase] cannot answer "did an AUTH succeed since I asked my question?", and that is
     * the question a REQ refused with `auth-required:` actually needs: every success drives
     * [com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient.syncFilters], which
     * re-sends the refused REQ, so a caller whose count has moved knows its subscription is
     * live again. `AUTHENTICATED` alone is ambiguous — it reads the same whether the AUTH
     * landed a moment ago in response to our refusal or an hour ago on a connection the
     * relay is now gating for some other reason.
     *
     * Resets with the connection, along with the rest of the per-connection auth state.
     */
    val successCount: Int = 0,
) {
    enum class Phase {
        /** Connected; no AUTH challenge has been received yet. */
        IDLE,

        /**
         * A challenge is with the responder and the signature is being produced —
         * which is not instantaneous work: a NIP-55 external signer or a NIP-46
         * bunker puts a prompt in front of the user and can sit here for as long
         * as they take to answer.
         *
         * Distinct from [IDLE] on purpose. Both mean "no AUTH has been submitted
         * yet", and a reader that cannot tell them apart cannot tell a relay
         * nobody is answering for from one whose answer is still being written —
         * which is exactly the call a fetch has to make when a REQ comes back
         * `auth-required:`. See
         * [com.vitorpamplona.quartz.nip01Core.relay.client.auth.awaitAuthOutcome].
         */
        SIGNING,

        /** Signed AUTH event in flight; awaiting OK from the relay. */
        AUTHENTICATING,

        /** Last AUTH succeeded; relay accepts authenticated REQs. */
        AUTHENTICATED,

        /** Last AUTH attempt failed; subsequent challenges may still arrive. */
        AUTH_FAILED,
    }

    /**
     * True while someone is actively working on this relay's AUTH — the signature
     * is being produced ([Phase.SIGNING]) or an AUTH event is on the wire awaiting
     * its OK ([Phase.AUTHENTICATING]). A caller that was refused `auth-required:`
     * must keep waiting exactly while this holds.
     */
    val inFlight: Boolean get() = phase == Phase.SIGNING || phase == Phase.AUTHENTICATING

    companion object {
        val IDLE = RelayAuthSnapshot(Phase.IDLE, lastAuthSuccessAt = null)
    }
}
