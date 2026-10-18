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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.display

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.actions.EmojiPackActions
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiImport
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.extractEmojiArchive
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.lastPathSegmentOrNull
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.reflect.KClass

@Stable
class EmojiPackViewModel(
    val account: Account,
    val packIdentifier: String,
) : ViewModel() {
    val selectedPackFlow =
        account.ownedEmojiPacks
            .getOwnedEmojiPackFlow(packIdentifier)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2500), null)

    var isUploadingEmojiImage by mutableStateOf(false)

    suspend fun addEmoji(
        emoji: EmojiUrlTag,
        isPrivate: Boolean,
    ) {
        account.addEmojiToOwnedPack(packIdentifier, emoji, isPrivate)
    }

    suspend fun removeEmoji(
        shortcode: String,
        isPrivate: Boolean,
    ) {
        account.removeEmojiFromOwnedPack(packIdentifier, shortcode, isPrivate)
    }

    suspend fun deletePack() {
        account.deleteOwnedEmojiPack(packIdentifier)
    }

    fun uploadEmojiImage(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                uploader,
                onUploading = { isUploadingEmojiImage = it },
                onUploaded = onUploaded,
                onError = onError,
            )
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        uploader: MediaUploader,
        onUploading: (Boolean) -> Unit,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        onUploading(true)
        val url = uploadToDefaultServer(galleryUri, account, uploader, onError)
        onUploading(false)
        if (url != null) onUploaded(url)
    }

    /** Where a running [importFiles] is; null when none is. */
    var importProgress by mutableStateOf<EmojiImportProgress?>(null)
        private set

    /** What the last [importFiles] did, until [clearImportResult]. */
    var importResult by mutableStateOf<EmojiImportResult?>(null)
        private set

    fun clearImportResult() {
        importResult = null
    }

    /**
     * Uploads [files] (images, and the images inside any .zip among them) to the default media
     * server and adds them to this pack in one new version of it, each named after its file.
     * Shortcodes the pack already has in that bucket are skipped before uploading.
     */
    suspend fun importFiles(
        files: List<SelectedMedia>,
        uploader: MediaUploader,
        isPrivate: Boolean,
    ) = withContext(Dispatchers.IO) {
        importResult = null
        importProgress = EmojiImportProgress(0, 0)
        val tempFiles = mutableListOf<Path>()
        try {
            val candidates = mutableListOf<Pair<String, SelectedMedia>>()
            val unsupported = mutableListOf<String>()
            val unreadable = mutableListOf<String>()
            val tooLarge = mutableListOf<String>()

            for (media in files) {
                val name = uploader.displayName(media.uri) ?: media.uri.lastPathSegmentOrNull() ?: media.uri.toString()
                val mimeType = media.mimeType ?: uploader.mimeType(media.uri)
                if (EmojiImport.isZipFileName(name) || EmojiImport.isZipMimeType(mimeType)) {
                    val archive =
                        try {
                            uploader.readBytes(media.uri)?.let { extractEmojiArchive(it) }
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    if (archive == null) {
                        unreadable.add(name)
                        continue
                    }
                    archive.files.forEach { extracted ->
                        tempFiles.add(extracted.path)
                        candidates.add(extracted.fileName to SelectedMedia(mediaUriOfFile(extracted.path), EmojiImport.imageMimeTypeOf(extracted.fileName)))
                    }
                    tooLarge.addAll(archive.tooLarge)
                } else if (mimeType?.startsWith("image/") == true || EmojiImport.isImageFileName(name)) {
                    candidates.add(name to SelectedMedia(media.uri, mimeType ?: EmojiImport.imageMimeTypeOf(name)))
                } else {
                    unsupported.add(name)
                }
            }

            val codes = EmojiImport.assignShortcodes(candidates.map { it.first })
            val pack = account.ownedEmojiPacks.getPack(packIdentifier)
            val existing = (if (isPrivate) pack?.privateEmojis else pack?.publicEmojis).orEmpty().mapTo(HashSet()) { it.code }

            val unnamed = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            val toUpload = mutableListOf<Triple<String, String, SelectedMedia>>()
            candidates.forEachIndexed { index, (name, media) ->
                val code = codes[index]
                when {
                    code == null -> unnamed.add(name)
                    code in existing -> skipped.add(code)
                    else -> toUpload.add(Triple(code, name, media))
                }
            }

            val uploaded = mutableListOf<EmojiUrlTag>()
            val failed = mutableListOf<String>()
            var failureReason: String? = null
            toUpload.forEachIndexed { index, (code, name, media) ->
                importProgress = EmojiImportProgress(index, toUpload.size)
                val url = uploadToDefaultServer(media, account, uploader, onError = { _, message -> failureReason = message })
                if (url != null) uploaded.add(EmojiUrlTag(code, url)) else failed.add(name)
            }
            importProgress = EmojiImportProgress(toUpload.size, toUpload.size)

            val result =
                if (uploaded.isNotEmpty()) {
                    account.addEmojisToOwnedPack(packIdentifier, uploaded, isPrivate, EmojiPackActions.OnConflict.SKIP)
                } else {
                    null
                }

            importResult =
                EmojiImportResult(
                    added = result?.added?.size ?: 0,
                    skipped = (skipped + result?.skipped.orEmpty()).toImmutableList(),
                    failed = failed.toImmutableList(),
                    failureReason = failureReason,
                    unnamed = unnamed.toImmutableList(),
                    tooLarge = tooLarge.toImmutableList(),
                    unsupported = unsupported.toImmutableList(),
                    unreadable = unreadable.toImmutableList(),
                )
        } finally {
            importProgress = null
            tempFiles.forEach { runCatching { platformFileSystem.delete(it, mustExist = false) } }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Initializer(
        val account: Account,
        val packIdentifier: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = EmojiPackViewModel(account, packIdentifier) as T
    }
}

/** [done] of [total] uploads finished; a total of 0 means the files are still being read. */
@Immutable
data class EmojiImportProgress(
    val done: Int,
    val total: Int,
)

/** What an import did, by shortcode ([skipped]) or by file name (the rest). */
@Immutable
data class EmojiImportResult(
    val added: Int,
    val skipped: ImmutableList<String>,
    val failed: ImmutableList<String>,
    /** The upload error of the last file in [failed], as the uploader worded it. */
    val failureReason: String?,
    val unnamed: ImmutableList<String>,
    val tooLarge: ImmutableList<String>,
    val unsupported: ImmutableList<String>,
    val unreadable: ImmutableList<String>,
)
