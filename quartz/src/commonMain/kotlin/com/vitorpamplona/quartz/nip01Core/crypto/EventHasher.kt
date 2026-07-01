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
package com.vitorpamplona.quartz.nip01Core.crypto

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Computes and verifies Nostr **event ids** (NIP-01).
 *
 * An id is the SHA-256 of the canonically serialized array
 * `[0, pubkey, created_at, kind, tags, content]` — UTF-8, no whitespace, in that
 * exact order. Getting that serialization right by hand is easy to botch (it is
 * what makes relays reject an event), so route through here rather than calling
 * `sha256` on your own JSON. Signing/building via the typed event builders already
 * does this for you; reach for `EventHasher` when validating events from an
 * untrusted source or hashing a not-yet-wrapped template.
 *
 * ```kotlin
 * val ok = EventHasher.hashIdCheck(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content)
 * ```
 */
object EventHasher {
    /** Raw 32-byte id digest for the given event fields. See [hashId] for the hex form. */
    fun hashIdBytes(
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): ByteArray = sha256(EventHasherSerializer.fastMakeJsonForId(pubKey, createdAt, kind, tags, content))

    /** The event id as a lower-case 64-char hex string. */
    fun hashId(
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): String = hashIdBytes(pubKey, createdAt, kind, tags, content).toHexKey()

    /** True when [id] matches the id computed from the other fields — use to validate untrusted events. */
    fun hashIdCheck(
        id: HexKey,
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Boolean = EventHasherSerializer.makeJsonForIdHashAndCheck(id, pubKey, createdAt, kind, tags, content)
}
