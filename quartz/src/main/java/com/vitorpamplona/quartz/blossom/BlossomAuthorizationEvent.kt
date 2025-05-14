/**
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
package com.vitorpamplona.quartz.blossom

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class BlossomAuthorizationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 24242

        fun createGetAuth(
            hash: HexKey,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BlossomAuthorizationEvent) -> Unit,
        ) = createAuth("get", hash, null, alt, signer, createdAt, onReady)

        fun createListAuth(
            signer: NostrSigner,
            alt: String,
            createdAt: Long = TimeUtils.now(),
            onReady: (BlossomAuthorizationEvent) -> Unit,
        ) = createAuth("list", null, null, alt, signer, createdAt, onReady)

        fun createDeleteAuth(
            hash: HexKey,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BlossomAuthorizationEvent) -> Unit,
        ) = createAuth("delete", hash, null, alt, signer, createdAt, onReady)

        fun createUploadAuth(
            hash: HexKey,
            size: Long,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BlossomAuthorizationEvent) -> Unit,
        ) = createAuth("upload", hash, size, alt, signer, createdAt, onReady)

        private fun createAuth(
            type: String,
            hash: HexKey?,
            fileSize: Long?,
            alt: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BlossomAuthorizationEvent) -> Unit,
        ) {
            val tags =
                listOfNotNull(
                    arrayOf("t", type),
                    arrayOf("expiration", TimeUtils.oneHourAhead().toString()),
                    fileSize?.let { arrayOf("size", it.toString()) },
                    hash?.let { arrayOf("x", it) },
                )

            signer.sign(createdAt, KIND, tags.toTypedArray(), alt, onReady)
        }
    }
}
