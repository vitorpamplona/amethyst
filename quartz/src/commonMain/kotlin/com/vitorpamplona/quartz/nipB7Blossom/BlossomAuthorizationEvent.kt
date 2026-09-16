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
package com.vitorpamplona.quartz.nipB7Blossom

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.io.encoding.Base64

@Immutable
class BlossomAuthorizationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * This event's JSON as standard Base64 WITH padding — the same encoder as
     * NIP-98's [com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent.rawToken].
     *
     * NOT what BUD-11 (draft) says: "the authorization token MUST be encoded as
     * Base64 URL-safe without padding (Base64url, as used by JWTs)". Deployed
     * servers decode with a strict standard decoder (Go's base64.StdEncoding in
     * khatru-based servers), which rejects both missing padding and the `-`/`_`
     * alphabet as "invalid base64 token" — and padding is needed whenever the
     * JSON length is not a multiple of three. Interop wins over the draft. Do not
     * switch this back to `Base64.UrlSafe` without re-probing a khatru server.
     */
    fun rawToken() = Base64.encode(toJson().encodeToByteArray())

    /**
     * The full `Authorization` header value for a Blossom request:
     * `Nostr <base64-event>` (BUD-11, HTTP Authorization Header).
     */
    fun toAuthorizationHeader() = "$AUTH_HEADER_SCHEME${rawToken()}"

    companion object {
        const val KIND = 24242

        /** Scheme prefix for the `Authorization` header value (BUD-11). */
        const val AUTH_HEADER_SCHEME = "Nostr "

        /**
         * BUD-11 `t=get` read authorization.
         *
         * [hash] is optional because BUD-11 lists the `x` tag as *optional* for
         * `GET /<sha256>`, and its Tag scoping rule is strict about what adding
         * one means: "When `x` tags are present, the token is only valid for
         * operations on the specified blob hashes." So pass a hash only for a
         * token used to fetch that one blob; pass null for a token that will be
         * reused across blobs on a host, and let the `server` tag scope it.
         * A hash-scoped token replayed for a different blob is invalid.
         */
        suspend fun createGetAuth(
            hash: HexKey?,
            alt: String,
            signer: NostrSigner,
            servers: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("get", hash, null, alt, signer, servers, createdAt)

        suspend fun createListAuth(
            signer: NostrSigner,
            alt: String,
            servers: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("list", null, null, alt, signer, servers, createdAt)

        suspend fun createDeleteAuth(
            hash: HexKey,
            alt: String,
            signer: NostrSigner,
            servers: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("delete", hash, null, alt, signer, servers, createdAt)

        suspend fun createUploadAuth(
            hash: HexKey,
            size: Long,
            alt: String,
            signer: NostrSigner,
            servers: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("upload", hash, size, alt, signer, servers, createdAt)

        /**
         * BUD-05 media-optimization auth (`t=media`). The [hash] is the sha256 of
         * the *original* bytes the client sends to `PUT /media`; the server returns
         * a descriptor whose hash is the optimized blob's.
         */
        suspend fun createMediaAuth(
            hash: HexKey,
            size: Long,
            alt: String,
            signer: NostrSigner,
            servers: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("media", hash, size, alt, signer, servers, createdAt)

        private suspend fun createAuth(
            type: String,
            hash: HexKey?,
            fileSize: Long?,
            alt: String,
            signer: NostrSigner,
            servers: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ): BlossomAuthorizationEvent {
            // BUD-11 `server` tags scope the token to specific domains so an upload
            // or delete token can't be replayed against another server. The value
            // MUST be the lowercase bare domain (no scheme/port/path).
            val serverTags =
                servers
                    .map { BlossomServerUrl.domain(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { arrayOf("server", it) }

            val tags =
                listOfNotNull(
                    arrayOf("t", type),
                    arrayOf("expiration", TimeUtils.oneHourAhead().toString()),
                    fileSize?.let { arrayOf("size", it.toString()) },
                    hash?.let { arrayOf("x", it) },
                ) + serverTags

            return signer.sign(createdAt, KIND, tags.toTypedArray(), alt)
        }
    }
}
