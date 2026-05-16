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

// UI-facing per-feature choice. Kept separate from PrivacyTransport so a future
// AUTO value can be added without churning the transport plumbing.
enum class TransportChoice(
    val screenCode: Int,
) {
    DIRECT(0),
    TOR(1),
    I2P(2),
}

fun parseTransportChoice(code: Int?): TransportChoice =
    when (code) {
        TransportChoice.TOR.screenCode -> TransportChoice.TOR
        TransportChoice.I2P.screenCode -> TransportChoice.I2P
        else -> TransportChoice.DIRECT
    }

fun TransportChoice.toTransport(): PrivacyTransport =
    when (this) {
        TransportChoice.DIRECT -> PrivacyTransport.DIRECT
        TransportChoice.TOR -> PrivacyTransport.TOR
        TransportChoice.I2P -> PrivacyTransport.I2P
    }
