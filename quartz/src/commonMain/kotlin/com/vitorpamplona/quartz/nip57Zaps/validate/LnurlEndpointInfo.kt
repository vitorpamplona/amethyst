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
package com.vitorpamplona.quartz.nip57Zaps.validate

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Cached metadata pulled from a recipient's `/.well-known/lnurlp/<user>` endpoint
 * that is relevant to NIP-57 zap receipt validation.
 *
 * `nostrPubkey` is the pubkey the LNURL provider will use to sign zap receipts
 * (kind 9735). NIP-57 Appendix F requires the receipt's `pubkey` to equal this
 * value. May be null if the provider did not advertise one (i.e. doesn't support
 * NIP-57 zaps).
 */
data class LnurlEndpointInfo(
    val nostrPubkey: HexKey?,
    val allowsNostr: Boolean,
)
