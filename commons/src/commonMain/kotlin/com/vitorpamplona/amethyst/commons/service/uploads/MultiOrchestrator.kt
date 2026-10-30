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
package com.vitorpamplona.amethyst.commons.service.uploads

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Stable
class MultiOrchestrator(
    uris: List<SelectedMedia>,
) {
    // Snapshot state so a removal recomposes the gallery that draws it; as a plain var the
    // Remove on a failed upload dropped the item from the upload but left it on screen.
    private var list: List<SelectedMediaProcessing> by mutableStateOf(uris.map { SelectedMediaProcessing(it) })

    /** [keptEncrypted] are the files [upload] did not send again because [uploadEncrypted] already had. */
    @Stable
    class Result(
        val allGood: Boolean,
        val successful: List<UploadingState.Finished>,
        val errors: List<UploadingState.Error>,
        val keptEncrypted: List<EncryptedUpload> = emptyList(),
    )

    fun first() = list.first()

    fun hasVideo() = list.any { it.isVideo() }

    fun hasGif() = list.any { it.media.isGif() }

    fun hasCompressible() = list.any { it.media.isCompressible() }

    /** A still image the platform re-encodes; [hasCompressible] also counts videos. */
    fun hasCompressibleImage() = list.any { it.media.isImage() == true && it.media.isCompressible() }

    /** The video the slider label describes. */
    fun firstVideo(): SelectedMedia? = list.firstOrNull { it.isVideo() }?.media

    /**
     * Every picked video, in order, for the HDR hint. A new list each call, of the same
     * [SelectedMedia] instances, so a caller can key on it and only a real add or removal counts.
     */
    fun videos(): List<SelectedMedia> = list.filter { it.isVideo() }.map { it.media }

    private fun SelectedMediaProcessing.isVideo() = media.mimeType?.startsWith("video", ignoreCase = true) == true

    fun hasNonMedia() = list.any { it.media.isNotMedia() }

    /**
     * Uploads every file in plaintext. With [keepEncrypted], a file the last [uploadEncrypted] sent,
     * under the same settings, is not sent again: it comes back in [Result.keptEncrypted], still
     * sealed, so a retry without encryption uploads only the files that failed.
     */
    suspend fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
        keepEncrypted: Boolean = false,
    ): Result {
        val settings = UploadSettings(alt, contentWarningReason, mediaQuality, server, useH265, stripMetadata, convertGifToMp4)
        val kept = if (keepEncrypted) list.mapNotNull { item -> sentEncrypted(item, settings)?.let { item to it } }.toMap() else emptyMap()
        coroutineScope {
            val jobs =
                list.filter { it !in kept }.map { item ->
                    launch(Dispatchers.IO) {
                        item.orchestrator.upload(
                            item.media.uri,
                            item.media.mimeType,
                            alt,
                            contentWarningReason,
                            mediaQuality,
                            server,
                            account,
                            uploader,
                            useH265,
                            stripMetadata,
                            onStrippingFailed,
                            convertGifToMp4 = convertGifToMp4,
                            forcedSigner = forcedSigner,
                        )
                    }
                }

            jobs.joinAll()
        }

        val keptEncrypted = list.mapNotNull { kept[it] }
        return computeFinalResults(list.filter { it !in kept }).let { Result(it.allGood, it.successful, it.errors, keptEncrypted) }
    }

    /** A file [uploadEncrypted] sent, with the cipher that sealed it: the receiver needs that cipher's key and nonce. */
    @Stable
    class EncryptedUpload(
        val finished: UploadingState.Finished,
        val cipher: AESGCM,
    )

    @Stable
    class EncryptedResult(
        val allGood: Boolean,
        val successful: List<EncryptedUpload>,
        val errors: List<UploadingState.Error>,
    )

    /** What shapes the blob a file becomes; a file sent under other settings is sent again. */
    private data class UploadSettings(
        val alt: String?,
        val contentWarningReason: String?,
        val mediaQuality: CompressorQuality,
        val server: ServerName,
        val useH265: Boolean,
        val stripMetadata: Boolean,
        val convertGifToMp4: Boolean,
    )

    private class SentEncrypted(
        val settings: UploadSettings,
        val upload: EncryptedUpload,
    )

    // The files the last uploadEncrypted sent. A retry after a partial failure reuses them instead
    // of sealing each one again under a new key and leaving its first blob on the server.
    private var sentEncrypted: Map<SelectedMediaProcessing, SentEncrypted> = emptyMap()

    /** What the last [uploadEncrypted] sent for [item] under [settings], unless the file was sent again since. */
    private fun sentEncrypted(
        item: SelectedMediaProcessing,
        settings: UploadSettings,
    ): EncryptedUpload? = sentEncrypted[item]?.takeIf { it.settings == settings && item.orchestrator.progressState.value === it.upload.finished }?.upload

    /**
     * Uploads every file encrypted under its own cipher from [newCipher]. One cipher cannot seal two
     * files: its nonce is fixed, so a second file under the same key and nonce would leak the XOR of
     * both plaintexts (and AESGCM throws on its second encrypt). A file the previous call already
     * sent, under the same settings, is not sent again.
     */
    suspend fun uploadEncrypted(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: CompressorQuality,
        newCipher: () -> AESGCM,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): EncryptedResult {
        val settings = UploadSettings(alt, contentWarningReason, mediaQuality, server, useH265, stripMetadata, convertGifToMp4)
        val items = list
        val outcomes =
            coroutineScope {
                items
                    .map { item ->
                        val sent = sentEncrypted(item, settings)
                        if (sent != null) {
                            CompletableDeferred(sent.finished to sent.cipher)
                        } else {
                            async(Dispatchers.IO) {
                                val cipher = newCipher()
                                val state =
                                    item.orchestrator.uploadEncrypted(
                                        item.media.uri,
                                        item.media.mimeType,
                                        alt,
                                        contentWarningReason,
                                        mediaQuality,
                                        cipher,
                                        server,
                                        account,
                                        uploader,
                                        useH265,
                                        stripMetadata,
                                        onStrippingFailed,
                                        convertGifToMp4 = convertGifToMp4,
                                        forcedSigner = forcedSigner,
                                    )
                                state to cipher
                            }
                        }
                    }.awaitAll()
            }

        // Like computeFinalResults, a file removed while the upload ran is left out.
        val remaining = items.zip(outcomes).filter { (item, _) -> item in list }
        val successful = remaining.mapNotNull { (item, outcome) -> (outcome.first as? UploadingState.Finished)?.let { item to EncryptedUpload(it, outcome.second) } }
        val errors = remaining.mapNotNull { (_, outcome) -> outcome.first as? UploadingState.Error }

        sentEncrypted = successful.associate { (item, upload) -> item to SentEncrypted(settings, upload) }

        return EncryptedResult(successful.size == remaining.size, successful.map { it.second }, errors)
    }

    private fun computeFinalResults(items: List<SelectedMediaProcessing> = list): Result {
        val resultsByState =
            items.map {
                it.orchestrator.progressState.value
            }

        val finished = resultsByState.filterIsInstance<UploadingState.Finished>()
        val errors = resultsByState.filterIsInstance<UploadingState.Error>()

        return Result(finished.size == items.size, finished, errors)
    }

    fun remove(selected: SelectedMediaProcessing) {
        list = list.filter { it != selected }
    }

    fun size() = list.size

    fun get(index: Int) = list[index]
}
