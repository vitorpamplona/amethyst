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
package com.vitorpamplona.quartz.concord.crypto

import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * The address + keys of a Concord message plane (Control, Chat, Guestbook, …) or
 * a derived channel, all produced by [ConcordKeyDerivation.groupKey].
 *
 * A plane is a stream on Nostr keyed by a single shared key:
 *  - [secretKey] signs the stream wraps (kind 1059) at this address and derives
 *    the [conversationKey].
 *  - [publicKey] is the 32-byte x-only pubkey that is the stream's address —
 *    members `REQ` for kind-1059 events authored by it.
 *  - [conversationKey] is the NIP-44 self-ECDH conversation key used to encrypt
 *    the wrap content (self-ECDH of [secretKey] against its own [publicKey]).
 *
 * Rotating the epoch (or the underlying secret) rotates [publicKey], keeping a
 * plane's traffic unlinkable across epochs (CORD-02 §Epochs).
 */
class GroupKey(
    val secretKey: ByteArray,
    val publicKey: ByteArray,
    val conversationKey: ByteArray,
) {
    /** Lower-case hex of the x-only [publicKey] — the stream address as it appears on the wire. */
    val publicKeyHex: String get() = publicKey.toHexKey()
}
