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
import com.vitorpamplona.amethyst.commons.napplet.NappletNotification
import com.vitorpamplona.amethyst.commons.napplet.OutboxEvent
import com.vitorpamplona.amethyst.commons.napplet.OutboxRelayPlan
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

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

    /** Result of `keys.registerAction`: the shell-assigned [actionId] and the [binding] it honored (e.g. `"Ctrl+S"`). */
    data class ActionRegistered(
        val actionId: String,
        val binding: String? = null,
    ) : NappletResponse

    /** `relay.subscribe` was authorized; the host now streams `relay.event`/`relay.eose` pushes. */
    data object Subscribed : NappletResponse

    /** `outbox.getEvent`: the event, or null with an [error] (`"not found"`, …). */
    data class OutboxFound(
        val found: OutboxEvent?,
        val incomplete: Boolean,
        val error: String? = null,
    ) : NappletResponse

    /** `outbox.query`: deduplicated events, [incomplete] when some relay lists or relays failed. */
    data class OutboxEvents(
        val events: List<OutboxEvent>,
        val incomplete: Boolean,
    ) : NappletResponse

    /**
     * `outbox.publish`: the [event] the shell signed and where it went. [ok] is false, with an
     * [error], when no relay took it or a required inbox could not be resolved.
     */
    data class OutboxPublished(
        val ok: Boolean,
        val event: Event?,
        val relays: Map<String, Boolean>,
        val error: String? = null,
    ) : NappletResponse

    /** `outbox.resolveRelays`. */
    data class OutboxPlan(
        val plan: OutboxRelayPlan,
    ) : NappletResponse

    /** `outbox.subscribe` was authorized on these [relays]; the host streams `outbox.event` pushes. */
    data class OutboxSubscribed(
        val relays: Set<NormalizedRelayUrl>,
    ) : NappletResponse

    /** Result of a storage read; [value] is null when the key is absent. */
    data class StorageValue(
        val value: String?,
    ) : NappletResponse

    /**
     * A single opaque string result, returned in the `value` field. Used by the NIP-44 ops, where the
     * payload is ciphertext one way and plaintext the other and the host has nothing to interpret.
     */
    data class Text(
        val value: String,
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

    data class ResourceInfo(
        val schemes: List<String>,
        val maxBytes: Long,
        val maxUrls: Int,
    ) : NappletResponse

    data class ResourceItem(
        val url: String,
        val resource: Bytes? = null,
        val error: String? = null,
        val message: String? = null,
    )

    data class ResourceItems(
        val items: List<ResourceItem>,
    ) : NappletResponse

    data class ResourceFailure(
        val error: String,
        val message: String? = null,
    ) : NappletResponse

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

    /** Result of `theme.get`: the host's current theme colors (hex strings). */
    data class Theme(
        val background: String,
        val text: String,
        val primary: String,
    ) : NappletResponse

    /** Result of `notify.create`: encoded as `notify.created { id }` (not the generic `.result`). */
    data class NotifyCreated(
        val id: String,
    ) : NappletResponse

    /** Result of `notify.list`: encoded as `notify.listed { notifications }` (not the generic `.result`). */
    data class NotifyListed(
        val notifications: List<NappletNotification>,
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

    /** A NAP-FS `FsRoot`. */
    data class FsRoot(
        val path: String,
        val name: String,
        val permissions: List<String>,
    )

    /** A file as NAP-FS shows it: an `FsPickedEntry`, or the fields of `FsMetadata`. */
    data class FsEntry(
        val path: String,
        val name: String,
        val permissions: List<String>,
        val size: Long? = null,
        val kind: String = "file",
    )

    /** `fs.info`: the visible roots and the byte limits of one read and one write. */
    data class FsInfoResult(
        val roots: List<FsRoot>,
        val maxReadBytes: Int,
        val maxWriteBytes: Int,
    ) : NappletResponse

    /** A successful pick: the entries the user chose, as virtual paths. */
    data class FsPicked(
        val entries: List<FsEntry>,
    ) : NappletResponse

    /** `fs.stat`. */
    data class FsMetadata(
        val entry: FsEntry,
    ) : NappletResponse

    /** `fs.read`: [bytes] read from [offset]; [eof] when nothing follows them. */
    data class FsReadResult(
        val bytes: ByteArray,
        val offset: Long,
        val eof: Boolean,
        val size: Long? = null,
    ) : NappletResponse {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FsReadResult) return false
            return offset == other.offset && eof == other.eof && size == other.size && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * (31 * (31 * offset.hashCode() + eof.hashCode()) + size.hashCode()) + bytes.contentHashCode()
    }

    /** `fs.write`: every byte landed; [size] is the file's size after it, when known. */
    data class FsWritten(
        val bytesWritten: Int,
        val size: Long? = null,
    ) : NappletResponse

    /** A failed `fs.*` request, with its NAP-FS [error] code. */
    data class FsFailure(
        val error: String,
    ) : NappletResponse
}
