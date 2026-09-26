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
package com.vitorpamplona.quartz.nip61Nutzaps.token

import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent

/**
 * NIP-61 does not define its own kind 7375: redeemed nutzap proofs are stored as
 * ordinary NIP-60 wallet token events. This used to be a second class for the same
 * kind, which `EventFactory` could never instantiate because [CashuTokenEvent] matched first.
 */
@Deprecated(
    "Kind 7375 is the NIP-60 Cashu wallet token event. Use CashuTokenEvent.",
    ReplaceWith("CashuTokenEvent", "com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent"),
)
typealias TokenEvent = CashuTokenEvent
