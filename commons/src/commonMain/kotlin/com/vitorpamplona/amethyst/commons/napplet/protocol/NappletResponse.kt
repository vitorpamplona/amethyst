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
package com.vitorpamplona.amethyst.commons.napplet.protocol

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The broker's reply to a [NappletRequest]. Crucially, **no variant ever carries private
 * key material** — only public results (a pubkey, a signed event, a ciphertext/plaintext,
 * a publish ack) or a typed failure. This is the property the process boundary exists to
 * preserve.
 */
sealed interface NappletResponse {
    data class PublicKey(
        val pubkey: HexKey,
    ) : NappletResponse

    data class SignedEvent(
        val event: Event,
    ) : NappletResponse

    /** Result of an encrypt/decrypt operation. */
    data class Text(
        val value: String,
    ) : NappletResponse

    data class Published(
        val relays: List<String>,
    ) : NappletResponse

    /** The user (or a standing DENY) refused the [capability]. */
    data class Denied(
        val capability: NappletCapability,
        val reason: String,
    ) : NappletResponse

    /** The operation is not implemented by this shell yet. */
    data class Unsupported(
        val operation: String,
    ) : NappletResponse

    /** The operation was authorized but failed while executing. */
    data class Failed(
        val reason: String,
    ) : NappletResponse
}
