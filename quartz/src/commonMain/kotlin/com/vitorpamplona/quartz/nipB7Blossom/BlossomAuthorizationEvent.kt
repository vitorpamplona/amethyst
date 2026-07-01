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
    /** Base64 of this event's JSON, as carried in the `Authorization` header value. */
    fun rawToken() = Base64.encode(toJson().encodeToByteArray())

    /**
     * The full `Authorization` header value for a BUD-01/BUD-02 request:
     * `Nostr <base64-event>`. Mirrors NIP-98's
     * [com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent.toAuthToken],
     * which Blossom auth reuses.
     */
    fun toAuthorizationHeader() = "$AUTH_HEADER_SCHEME${rawToken()}"

    companion object {
        const val KIND = 24242

        /** Scheme prefix for the `Authorization` header value (BUD-01). */
        const val AUTH_HEADER_SCHEME = "Nostr "

        suspend fun createGetAuth(
            hash: HexKey,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("get", hash, null, alt, signer, createdAt)

        suspend fun createListAuth(
            signer: NostrSigner,
            alt: String,
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("list", null, null, alt, signer, createdAt)

        suspend fun createDeleteAuth(
            hash: HexKey,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("delete", hash, null, alt, signer, createdAt)

        suspend fun createUploadAuth(
            hash: HexKey,
            size: Long,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = createAuth("upload", hash, size, alt, signer, createdAt)

        private suspend fun createAuth(
            type: String,
            hash: HexKey?,
            fileSize: Long?,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): BlossomAuthorizationEvent {
            val tags =
                listOfNotNull(
                    arrayOf("t", type),
                    arrayOf("expiration", TimeUtils.oneHourAhead().toString()),
                    fileSize?.let { arrayOf("size", it.toString()) },
                    hash?.let { arrayOf("x", it) },
                )

            return signer.sign(createdAt, KIND, tags.toTypedArray(), alt)
        }
    }
}
