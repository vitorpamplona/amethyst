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
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaV2Cipher
import com.vitorpamplona.quartz.marmot.appComponents.MarmotMediaType
import com.vitorpamplona.quartz.marmot.appComponents.MediaLocatorV2

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
    val thumbhash: String? = null,
    /**
     * The `encrypted-media-v2` reference, when the group's policy asked for
     * one. Null means this upload is a MIP-04 attachment and the fields above
     * are what builds its tag.
     *
     * The two are carried together rather than as two result types because the
     * upload pipeline is identical — only the cipher and the tag differ — and
     * the choice belongs to the group, not to the uploader.
     */
    val encryptedMediaV2: EncryptedMediaReferenceV2? = null,
)

/** What an unparseable media type becomes, so a file is never described in a dialect nobody reads. */
private const val GENERIC_MEDIA_TYPE = "application/octet-stream"

/**
 * Handles encrypted media upload for Marmot groups.
 *
 * Uses the existing [UploadOrchestrator.uploadEncrypted] pipeline but
 * provides a per-file [EncryptedMediaV2Cipher] for key derivation.
 */
class MarmotFileUploader(
    val account: Account,
) {
    suspend fun uploadMip04(
        viewState: ChatFileUploadState,
        exporterSecret: ByteArray,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        /**
         * Produce `encrypted-media-v2` references instead of MIP-04 ones.
         * Decided by the group's policy component, not by the uploader.
         */
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

            // v2 puts `m` inside both the key derivation and the AEAD
            // associated data, so it has to be the canonical form and not
            // whatever the content resolver reported.
            //
            // Always v2, whatever the group carries. This used to be gated on
            // the group holding the `encrypted-media-v2` policy, and groups are
            // created without it on purpose (epoch 0 has to match the reference
            // implementation byte for byte), so in practice every attachment
            // went out in the MIP-era dialect -- `url`/`x`/`n`/`v mip04-v2` --
            // which no shipping Marmot implementation reads: MDK 0.9.21 knows
            // only `encrypted-media-v1|v2` and drops anything else at the
            // typed parser, silently. Receivers do not gate on the policy
            // either (MDK's own test pins that an out-of-policy locator is
            // "kept, not dropped on ingest"), so writing v2 into a group that
            // never committed the component is read correctly; it is only the
            // SENDER's own policy validation that a component would constrain.
            //
            // A media type too malformed to canonicalize becomes the generic
            // octet-stream rather than falling back to the old dialect: an
            // attachment nobody can render is worse than one labelled
            // imprecisely.
            val canonicalMediaType = MarmotMediaType.canonicalize(mimeType) ?: GENERIC_MEDIA_TYPE
            val cipher = EncryptedMediaV2Cipher(exporterSecret, canonicalMediaType, filename)
            val v2Cipher = cipher

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
                // The reference is built from what the cipher recorded while
                // encrypting the bytes the pipeline actually uploaded — after
                // compression and metadata stripping — because that is what the
                // key was derived from.
                val reference =
                    v2Cipher?.let {
                        EncryptedMediaReferenceV2(
                            locators =
                                listOf(
                                    MediaLocatorV2(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND, serverResult.url),
                                ),
                            ciphertextSha256 = it.ciphertextSha256,
                            plaintextSha256 = it.plaintextSha256,
                            nonce = it.nonce,
                            mediaType = it.mediaType,
                            filename = filename,
                            dim = serverResult.fileHeader.dim?.toString(),
                            thumbhash = serverResult.fileHeader.thumbHash?.thumbhash,
                        )
                    }
                results.add(
                    Mip04UploadResult(
                        url = serverResult.url,
                        mimeType = mimeType,
                        filename = filename,
                        // Empty now that nothing is encrypted with the MIP-era
                        // scheme. The fields stay on the result type because the
                        // reader still accepts that shape for messages older
                        // builds already sent.
                        originalFileHash = ByteArray(0),
                        nonce = ByteArray(0),
                        dimensions = serverResult.fileHeader.dim?.toString(),
                        blurhash = serverResult.fileHeader.blurHash?.blurhash,
                        caption = viewState.caption.ifEmpty { null },
                        thumbhash = serverResult.fileHeader.thumbHash?.thumbhash,
                        encryptedMediaV2 = reference,
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
