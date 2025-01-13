/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip71Video

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID

@Immutable
class VideoVerticalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : VideoEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    companion object {
        const val KIND = 34236
        const val ALT_DESCRIPTION = "Vertical Video"

        fun create(
            url: String,
            mimeType: String? = null,
            alt: String? = null,
            hash: String? = null,
            size: Int? = null,
            duration: Int? = null,
            dimensions: Dimension? = null,
            blurhash: String? = null,
            sensitiveContent: Boolean? = null,
            service: String? = null,
            dTag: String = UUID.randomUUID().toString(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (VideoVerticalEvent) -> Unit,
        ) {
            create(
                kind = KIND,
                dTag = dTag,
                url = url,
                mimeType = mimeType,
                alt = alt,
                hash = hash,
                size = size,
                duration = duration,
                dimensions = dimensions,
                blurhash = blurhash,
                sensitiveContent = sensitiveContent,
                service = service,
                altDescription = ALT_DESCRIPTION,
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }
    }
}
