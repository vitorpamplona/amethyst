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

    /**
     * Result of `relay.publish` / `relay.publishEncrypted`: the [event] the shell signed on the
     * napplet's behalf and the [relays] that accepted it. Matches the upstream contract, where
     * `publish(template)` resolves to the signed `NostrEvent`.
     */
    data class Published(
        val event: Event,
        val relays: List<String>,
    ) : NappletResponse

    /** Result of a [com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest.QueryEvents]. */
    data class Events(
        val events: List<Event>,
    ) : NappletResponse

    /** Result of `shell.supports(domain)`. */
    data class Supported(
        val supported: Boolean,
    ) : NappletResponse

    /** Result of `keys.registerAction`: the shell-assigned [actionId] and the [binding] it honored (e.g. `"Ctrl+S"`). */
    data class ActionRegistered(
        val actionId: String,
        val binding: String? = null,
    ) : NappletResponse

    /** `relay.subscribe` was authorized; the host now streams `relay.event`/`relay.eose` pushes. */
    data object Subscribed : NappletResponse

    /** Result of a storage read; [value] is null when the key is absent. */
    data class StorageValue(
        val value: String?,
    ) : NappletResponse

    /** Result of `storage.keys` (and other string-list reads). */
    data class Strings(
        val values: List<String>,
    ) : NappletResponse

    /**
     * A read result already serialized as a JSON value string (object/array/string, or the literal
     * `"null"`). Used by `identity.*` reads, whose shapes vary; the host builds the JSON.
     */
    data class Json(
        val raw: String,
    ) : NappletResponse

    /** Result of a `resource.bytes` fetch. */
    data class Bytes(
        val bytes: ByteArray,
        val contentType: String,
    ) : NappletResponse {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false
            return contentType == other.contentType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * contentType.hashCode() + bytes.contentHashCode()
    }

    /** Result of an `upload.upload`; [url] is where the blob can be fetched, plus NIP-94-ish metadata. */
    data class Uploaded(
        val url: String,
        val sha256: String? = null,
        val size: Long? = null,
        val mimeType: String? = null,
    ) : NappletResponse

    /** Result of a successful invoice payment; [preimage] is null when unconfirmed. */
    data class Paid(
        val preimage: String?,
    ) : NappletResponse

    /** A successful operation with no return value (e.g. a storage write/remove). */
    data object Done : NappletResponse

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
