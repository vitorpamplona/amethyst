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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send

import android.content.Context
import android.net.Uri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04NostrCipher

/**
 * MIP-04 upload result containing all info needed to build the imeta tag.
 */
class Mip04UploadResult(
    val url: String,
    val mimeType: String,
    val filename: String,
    val originalFileHash: ByteArray,
    val nonce: ByteArray,
    val dimensions: String?,
    val blurhash: String?,
    val caption: String?,
)

/**
 * Handles MIP-04 encrypted media upload for Marmot groups.
 *
 * Uses the existing [UploadOrchestrator.uploadEncrypted] pipeline but
 * provides a per-file [Mip04NostrCipher] for MIP-04 key derivation.
 */
class MarmotFileUploader(
    val account: Account,
) {
    suspend fun uploadMip04(
        viewState: ChatFileUploadState,
        exporterSecret: ByteArray,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: suspend (List<Mip04UploadResult>) -> Unit,
    ) {
        val multiOrchestrator = viewState.multiOrchestrator ?: return
        viewState.mediaUploadTracker.startUpload(multiOrchestrator.hasNonMedia())

        val results = mutableListOf<Mip04UploadResult>()
        val quality = MediaCompressor.intToCompressorQuality(viewState.mediaQualitySlider)

        val count = multiOrchestrator.size()
        for (i in 0 until count) {
            val item = multiOrchestrator.get(i)
            val media = item.media
            val mimeType = media.mimeType ?: "application/octet-stream"
            val filename = resolveFilename(context, media.uri, mimeType)

            val cipher = Mip04NostrCipher(exporterSecret, mimeType, filename)

            item.orchestrator.uploadEncrypted(
                uri = media.uri,
                mimeType = mimeType,
                alt = viewState.caption.ifEmpty { null },
                contentWarningReason = viewState.contentWarningReason,
                compressionQuality = quality,
                encrypt = cipher,
                server = viewState.selectedServer,
                account = account,
                context = context,
                stripMetadata = viewState.stripMetadata,
            )

            val state = item.orchestrator.progressState.value
            if (state is UploadingState.Finished && state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                val serverResult = state.result
                results.add(
                    Mip04UploadResult(
                        url = serverResult.url,
                        mimeType = mimeType,
                        filename = filename,
                        originalFileHash = cipher.originalFileHash,
                        nonce = cipher.nonce,
                        dimensions = serverResult.fileHeader.dim?.toString(),
                        blurhash = serverResult.fileHeader.blurHash?.blurhash,
                        caption = viewState.caption.ifEmpty { null },
                    ),
                )
            } else {
                val errorMessage =
                    if (state is UploadingState.Error) {
                        stringRes(context, state.errorResource, *state.params)
                    } else {
                        "Upload failed for $filename"
                    }
                onError(
                    stringRes(context, R.string.failed_to_upload_media_no_details),
                    errorMessage,
                )
                viewState.mediaUploadTracker.finishUpload()
                return
            }
        }

        onceUploaded(results)
        viewState.reset()
        viewState.mediaUploadTracker.finishUpload()
    }

    private fun resolveFilename(
        context: Context,
        uri: Uri,
        mimeType: String,
    ): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val name =
            cursor?.use {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
            }
        if (name != null) return name

        val ext =
            when {
                mimeType.startsWith("image/jpeg") -> "jpg"
                mimeType.startsWith("image/png") -> "png"
                mimeType.startsWith("image/gif") -> "gif"
                mimeType.startsWith("image/webp") -> "webp"
                mimeType.startsWith("video/mp4") -> "mp4"
                mimeType.startsWith("video/webm") -> "webm"
                mimeType.startsWith("audio/") -> "m4a"
                else -> "bin"
            }
        return "media.$ext"
    }
}
