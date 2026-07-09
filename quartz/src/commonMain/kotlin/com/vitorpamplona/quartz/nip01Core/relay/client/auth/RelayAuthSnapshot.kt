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
) {
    enum class Phase {
        /** Connected; no AUTH challenge has been received yet. */
        IDLE,

        /** Signed AUTH event in flight; awaiting OK from the relay. */
        AUTHENTICATING,

        /** Last AUTH succeeded; relay accepts authenticated REQs. */
        AUTHENTICATED,

        /** Last AUTH attempt failed; subsequent challenges may still arrive. */
        AUTH_FAILED,
    }

    companion object {
        val IDLE = RelayAuthSnapshot(Phase.IDLE, lastAuthSuccessAt = null)
    }
}
