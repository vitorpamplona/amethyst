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
package com.vitorpamplona.amethyst.commons.privacy

// Outcome of PrivacyRouter.route. Either an actionable transport, or Blocked
// when a hidden-service hostname cannot be reached (matching daemon is OFF)
// and we fail closed rather than leak the request over the clearnet.
sealed interface PrivacyRoute {
    data object Direct : PrivacyRoute

    data object Tor : PrivacyRoute

    data object I2p : PrivacyRoute

    // Hidden-service request whose matching daemon is disabled. Callers must
    // surface this as a visible failure — don't fall back to DIRECT.
    data class Blocked(
        val reason: BlockReason,
    ) : PrivacyRoute
}

enum class BlockReason {
    ONION_REQUIRES_TOR,
    I2P_REQUIRES_I2P,
}
