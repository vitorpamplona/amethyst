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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.service.uploads.FileHeader
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip92IMeta.imetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.sensitiveContent
import com.vitorpamplona.quartz.nip94FileMetadata.size
import java.util.Locale

class IMetaAttachments {
    var iMetaAttachments by mutableStateOf<List<IMetaTag>>(emptyList())

    suspend fun downloadAndPrepare(
        url: String,
        forceProxy: Boolean,
    ) {
        val fileExtension: String = MimeTypeMap.getFileExtensionFromUrl(url)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase(Locale.getDefault()))

        val imeta =
            FileHeader.prepare(url, mimeType, null, forceProxy).getOrNull()?.let {
                IMetaTagBuilder(url)
                    .apply {
                        hash(it.hash)
                        size(it.size)
                        it.mimeType?.let { mimeType(it) }
                        it.dim?.let { dims(it) }
                        it.blurHash?.let { blurhash(it.blurhash) }
                    }.build()
            }

        if (imeta != null) {
            iMetaAttachments += imeta
        }
    }

    fun remove(url: String) {
        iMetaAttachments = iMetaAttachments.filter { it.url != url }
    }

    fun replace(
        url: String,
        iMeta: IMetaTag,
    ) {
        iMetaAttachments = iMetaAttachments.filter { it.url != url } + iMeta
    }

    fun add(
        result: UploadOrchestrator.OrchestratorResult.ServerResult,
        alt: String?,
        contentWarningReason: String?,
    ) {
        val iMeta =
            imetaTagBuilder(result.url) {
                hash(result.fileHeader.hash)
                size(result.fileHeader.size)
                result.fileHeader.mimeType?.let { mimeType(it) }
                result.fileHeader.dim?.let { dims(it) }
                result.fileHeader.blurHash?.let { blurhash(it.blurhash) }
                result.magnet?.let { magnet(it) }
                result.uploadedHash?.let { originalHash(it) }

                alt?.let { alt(it) }
                contentWarningReason?.let { sensitiveContent(contentWarningReason) }
            }

        replace(iMeta.url, iMeta)
    }

    fun filterIsIn(urls: Set<String>) = iMetaAttachments.filter { it.url in urls }
}
