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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorCode

/**
 * Decides whether a BOLT12 zap the wallet refused should be re-sent as a BOLT11 zap.
 *
 * Only ever consulted for a NIP-47 *error* reply, which by the spec means the wallet
 * did not pay — so the retry can never double-spend. The question left is whether a
 * second attempt over a different instruction has a chance: a refusal about the
 * offer (the wallet could not resolve it, the recipient's node is unreachable, the
 * offer expired, or our wallet does not handle `lno` at all) is worth retrying over
 * the recipient's lightning address; a refusal about *our* wallet — no balance, a
 * quota or rate limit, a permission the connection lacks — would fail the same way
 * on BOLT11 and would only produce a second error.
 */
object Bolt12LightningFallback {
    /** Refusals that describe the sender's wallet, not the offer. */
    private val senderSideCodes =
        setOf(
            NwcErrorCode.INSUFFICIENT_BALANCE,
            NwcErrorCode.QUOTA_EXCEEDED,
            NwcErrorCode.RATE_LIMITED,
            NwcErrorCode.RESTRICTED,
            NwcErrorCode.UNAUTHORIZED,
            NwcErrorCode.UNSUPPORTED_ENCRYPTION,
        )

    /** True when a refusal with [code] (null when the wallet sent none) should be retried over BOLT11. */
    fun shouldRetry(code: NwcErrorCode?): Boolean = code !in senderSideCodes
}
