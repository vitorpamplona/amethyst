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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.amethyst.commons.privacy.BlockReason
import java.io.IOException

/**
 * Thrown when a request targets a hidden-service hostname whose matching
 * privacy daemon is disabled. The fail-closed semantics chosen by the user —
 * we never silently fall back to a clearnet route for `.onion` / `.i2p` URLs.
 *
 * Subclass of [IOException] so the existing HTTP error paths in Coil, OkHttp
 * call factories, etc. propagate it without changes. Callers that want to
 * present a clearer UI ("enable Tor/I2P to view this content") can
 * pattern-match on the type / reason instead of inspecting the message.
 */
class BlockedRouteException(
    val reason: BlockReason,
) : IOException(messageFor(reason)) {
    companion object {
        fun messageFor(reason: BlockReason): String =
            when (reason) {
                BlockReason.ONION_REQUIRES_TOR ->
                    "Cannot reach .onion address: Tor is disabled. Enable Tor in Privacy Options."
                BlockReason.I2P_REQUIRES_I2P ->
                    "Cannot reach .i2p address: I2P is disabled. Enable I2P in Privacy Options."
            }
    }
}
