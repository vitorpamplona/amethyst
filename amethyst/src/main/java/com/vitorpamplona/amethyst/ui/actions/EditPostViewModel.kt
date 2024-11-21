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
package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.BlossomUploader
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.MediaUploadResult
import com.vitorpamplona.amethyst.service.Nip96Uploader
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.components.MediaCompressor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
open class EditPostViewModel : ViewModel() {
    var accountViewModel: AccountViewModel? = null
    var account: Account? = null

    var editedFromNote: Note? = null

    var subject by mutableStateOf(TextFieldValue(""))

    var nip94attachments by mutableStateOf<List<FileHeaderEvent>>(emptyList())
    var nip95attachments by
        mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    var message by mutableStateOf(TextFieldValue(""))
    var urlPreview by mutableStateOf<String?>(null)
    var isUploadingImage by mutableStateOf(false)

    var userSuggestions by mutableStateOf<List<User>>(emptyList())
    var userSuggestionAnchor: TextRange? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    // Images and Videos
    var contentToAddUrl by mutableStateOf<Uri?>(null)

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    open fun prepare(
        edit: Note,
        versionLookingAt: Note?,
        accountViewModel: AccountViewModel,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.editedFromNote = edit
    }

    open fun load(
        edit: Note,
        versionLookingAt: Note?,
        accountViewModel: AccountViewModel,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account

        canAddInvoice = accountViewModel.userProfile().info?.lnAddress() != null
        contentToAddUrl = null

        message = TextFieldValue(versionLookingAt?.event?.content() ?: edit.event?.content() ?: "")
        urlPreview = findUrlInMessage()

        editedFromNote = edit
    }

    fun sendPost(relayList: List<RelaySetupInfo>) {
        viewModelScope.launch(Dispatchers.IO) { innerSendPost(relayList) }
    }

    suspend fun innerSendPost(relayList: List<RelaySetupInfo>) {
        if (accountViewModel == null) {
            cancel()
            return
        }

        nip95attachments.forEach {
            account?.sendNip95(it.first, it.second, relayList)
        }

        val notify =
            if (editedFromNote?.author?.pubkeyHex == account?.userProfile()?.pubkeyHex) {
                null
            } else {
                // notifies if it is not the logged in user
                editedFromNote?.author?.pubkeyHex
            }

        account?.sendEdit(
            message = message.text,
            originalNote = editedFromNote!!,
            notify = notify,
            summary = subject.text.ifBlank { null },
            relayList = relayList,
        )

        cancel()
    }

    open fun updateSubject(it: TextFieldValue) {
        subject = it
    }

    fun upload(
        galleryUri: Uri,
        alt: String?,
        sensitiveContent: Boolean,
        mediaQuality: Int,
        isPrivate: Boolean = false,
        server: ServerName,
        onError: (String, String) -> Unit,
        context: Context,
    ) {
        isUploadingImage = true
        contentToAddUrl = null

        val contentResolver = context.contentResolver
        val contentType = contentResolver.getType(galleryUri)

        viewModelScope.launch(Dispatchers.IO) {
            MediaCompressor()
                .compress(
                    galleryUri,
                    contentType,
                    context.applicationContext,
                    onReady = { fileUri, contentType, size ->
                        if (server.type == ServerType.NIP95) {
                            contentResolver.openInputStream(fileUri)?.use {
                                createNIP95Record(
                                    it.readBytes(),
                                    contentType,
                                    alt,
                                    sensitiveContent,
                                    onError = {
                                        onError(stringRes(context, R.string.failed_to_upload_media_no_details), it)
                                    },
                                    context,
                                )
                            }
                        } else if (server.type == ServerType.NIP96) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val result =
                                        Nip96Uploader(account)
                                            .uploadImage(
                                                uri = fileUri,
                                                contentType = contentType,
                                                size = size,
                                                alt = alt,
                                                sensitiveContent = if (sensitiveContent) "" else null,
                                                server = server,
                                                contentResolver = contentResolver,
                                                forceProxy = account?.let { it::shouldUseTorForNIP96 } ?: { false },
                                                onProgress = {},
                                                context = context,
                                            )

                                    createNIP94Record(
                                        uploadingResult = result,
                                        localContentType = contentType,
                                        alt = alt,
                                        sensitiveContent = sensitiveContent,
                                        forceProxy = account?.let { it::shouldUseTorForNIP96 } ?: { false },
                                        onError = {
                                            onError(stringRes(context, R.string.failed_to_upload_media_no_details), it)
                                        },
                                        context = context,
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(
                                        "ImageUploader",
                                        "Failed to upload ${e.message}",
                                        e,
                                    )
                                    isUploadingImage = false
                                    onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
                                }
                            }
                        } else {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val result =
                                        BlossomUploader(account)
                                            .uploadImage(
                                                uri = fileUri,
                                                contentType = contentType,
                                                size = size,
                                                alt = alt,
                                                sensitiveContent = if (sensitiveContent) "" else null,
                                                server = server,
                                                contentResolver = contentResolver,
                                                forceProxy = account?.let { it::shouldUseTorForNIP96 } ?: { false },
                                                context = context,
                                            )

                                    createNIP94Record(
                                        uploadingResult = result,
                                        localContentType = contentType,
                                        alt = alt,
                                        sensitiveContent = sensitiveContent,
                                        forceProxy = account?.let { it::shouldUseTorForNIP96 } ?: { false },
                                        onError = {
                                            onError(stringRes(context, R.string.failed_to_upload_media_no_details), it)
                                        },
                                        context = context,
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(
                                        "ImageUploader",
                                        "Failed to upload ${e.message}",
                                        e,
                                    )
                                    isUploadingImage = false
                                    onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
                                }
                            }
                        }
                    },
                    onError = {
                        isUploadingImage = false
                        onError(stringRes(context, R.string.failed_to_upload_media_no_details), stringRes(context, it))
                    },
                    mediaQuality = MediaCompressor().intToCompressorQuality(mediaQuality),
                )
        }
    }

    open fun cancel() {
        message = TextFieldValue("")
        subject = TextFieldValue("")

        editedFromNote = null

        contentToAddUrl = null
        urlPreview = null
        isUploadingImage = false

        wantsInvoice = false

        userSuggestions = emptyList()
        userSuggestionAnchor = null
        userSuggestionsMainMessage = null

        NostrSearchEventOrUserDataSource.clear()
    }

    open fun findUrlInMessage(): String? =
        message.text.split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                RichTextParser.isValidURL(word) || RichTextParser.isUrlWithoutScheme(word)
            }
        }

    open fun updateMessage(it: TextFieldValue) {
        message = it
        urlPreview = findUrlInMessage()

        if (it.selection.collapsed) {
            val lastWord =
                it.text
                    .substring(0, it.selection.end)
                    .substringAfterLast("\n")
                    .substringAfterLast(" ")
            userSuggestionAnchor = it.selection
            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            if (lastWord.startsWith("@") && lastWord.length > 2) {
                NostrSearchEventOrUserDataSource.search(lastWord.removePrefix("@"))
                viewModelScope.launch(Dispatchers.IO) {
                    userSuggestions =
                        LocalCache
                            .findUsersStartingWith(lastWord.removePrefix("@"), account)
                            .sortedWith(compareBy({ account?.isFollowing(it) }, { it.toBestDisplayName() }, { it.pubkeyHex }))
                            .reversed()
                }
            } else {
                NostrSearchEventOrUserDataSource.clear()
                userSuggestions = emptyList()
            }
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestionAnchor?.let {
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord =
                    message.text
                        .substring(0, it.end)
                        .substringAfterLast("\n")
                        .substringAfterLast(" ")
                val lastWordStart = it.end - lastWord.length
                val wordToInsert = "@${item.pubkeyNpub()}"

                message =
                    TextFieldValue(
                        message.text.replaceRange(lastWordStart, it.end, wordToInsert),
                        TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
                    )
            }

            userSuggestionAnchor = null
            userSuggestionsMainMessage = null
            userSuggestions = emptyList()
        }
    }

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            !isUploadingImage &&
            !wantsInvoice &&
            contentToAddUrl == null

    suspend fun createNIP94Record(
        uploadingResult: MediaUploadResult,
        localContentType: String?,
        alt: String?,
        sensitiveContent: Boolean,
        forceProxy: (String) -> Boolean,
        onError: (String) -> Unit = {},
        context: Context,
    ) {
        if (uploadingResult.url.isNullOrBlank()) {
            Log.e("ImageDownload", "Couldn't download image from server")
            cancel()
            isUploadingImage = false
            onError(stringRes(context, R.string.server_did_not_provide_a_url_after_uploading))
            return
        }

        FileHeader.prepare(
            fileUrl = uploadingResult.url,
            mimeType = uploadingResult.type ?: localContentType,
            dimPrecomputed = uploadingResult.dimension,
            forceProxy = forceProxy(uploadingResult.url),
            onReady = { header: FileHeader ->
                account?.createHeader(uploadingResult.url, uploadingResult.magnet, header, alt, sensitiveContent, uploadingResult.sha256) { event ->
                    isUploadingImage = false
                    nip94attachments = nip94attachments + event

                    message = message.insertUrlAtCursor(uploadingResult.url)
                    urlPreview = findUrlInMessage()
                }
            },
            onError = {
                isUploadingImage = false
                onError(stringRes(context, R.string.could_not_prepare_header, it))
            },
        )
    }

    fun createNIP95Record(
        bytes: ByteArray,
        mimeType: String?,
        alt: String?,
        sensitiveContent: Boolean,
        onError: (String) -> Unit = {},
        context: Context,
    ) {
        if (bytes.size > 80000) {
            viewModelScope.launch {
                onError(stringRes(context, id = R.string.media_too_big_for_nip95))
                isUploadingImage = false
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            FileHeader.prepare(
                bytes,
                mimeType,
                null,
                onReady = {
                    account?.createNip95(bytes, headerInfo = it, alt, sensitiveContent) { nip95 ->
                        nip95attachments = nip95attachments + nip95
                        val note = nip95.let { it1 -> account?.consumeNip95(it1.first, it1.second) }

                        isUploadingImage = false

                        note?.let {
                            message = message.insertUrlAtCursor("nostr:" + it.toNEvent())
                        }

                        urlPreview = findUrlInMessage()
                    }
                },
                onError = {
                    isUploadingImage = false
                    onError(stringRes(context, R.string.could_not_prepare_header, it))
                },
            )
        }
    }

    fun selectImage(uri: Uri) {
        contentToAddUrl = uri
    }
}
