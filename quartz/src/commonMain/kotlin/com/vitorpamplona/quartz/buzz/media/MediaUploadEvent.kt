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
package com.vitorpamplona.quartz.buzz.media

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz media-upload record (`kind:49001`): metadata for one uploaded blob.
 *
 * SCHEMA INFERRED — STRONGLY FLAGGED. `buzz-core/src/kind.rs` documents this kind verbatim as
 * "Internal kind for media upload audit entries. **Not a relay event kind.**", and no code in
 * the Buzz tree constructs a `kind:49001` Nostr event. The model here is a Quartz-side
 * projection: `content` is a [MediaUploadPayload] (the Blossom-style `BlobDescriptor` from
 * `buzz-media/src/types.rs`), with an optional NIP-94 `x` (sha256) tag and `p` (uploader) tag
 * for filtering. Do NOT register or rely on this on the wire — it is an internal/audit kind.
 */
@Immutable
class MediaUploadEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The blob sha256 — the `x` tag. */
    fun sha256Tag(): String? = tags.mediaSha256()

    /** The uploader pubkey — the `p` tag, if present. */
    fun uploader(): HexKey? = tags.mediaUploader()

    /** The parsed blob descriptor from `content`, or `null` if it does not decode. */
    fun payload(): MediaUploadPayload? = MediaUploadPayload.decodeFromJsonOrNull(content)

    companion object {
        const val KIND = 49001

        fun build(
            payload: MediaUploadPayload,
            uploaderPubKey: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MediaUploadEvent>.() -> Unit = {},
        ) = eventTemplate<MediaUploadEvent>(KIND, payload.encodeToJson(), createdAt) {
            sha256(payload.sha256)
            uploaderPubKey?.let { uploader(it) }
            initializer()
        }
    }
}
