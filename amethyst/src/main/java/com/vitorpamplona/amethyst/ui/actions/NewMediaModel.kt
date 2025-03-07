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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@Stable
open class NewMediaModel : ViewModel() {
    var account: Account? = null

    var isUploadingImage by mutableStateOf(false)

    var selectedServer by mutableStateOf<ServerName?>(null)
    var caption by mutableStateOf("")
    var sensitiveContent by mutableStateOf(false)

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)
    var onceUploaded: () -> Unit = {}

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(1)

    open fun load(
        account: Account,
        uris: ImmutableList<SelectedMedia>,
    ) {
        this.caption = ""
        this.account = account
        this.multiOrchestrator = MultiOrchestrator(uris)
        this.selectedServer = defaultServer()
    }

    fun isImage(
        url: String,
        mimeType: String?,
    ): Boolean = mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)

    fun upload(
        context: Context,
        relayList: List<RelaySetupInfo>,
        onSucess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch {
            val myAccount = account ?: return@launch
            if (relayList.isEmpty()) return@launch
            val serverToUse = selectedServer ?: return@launch

            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    viewModelScope,
                    caption,
                    if (sensitiveContent) "" else null,
                    MediaCompressor.intToCompressorQuality(mediaQualitySlider),
                    serverToUse,
                    myAccount,
                    context,
                )

            if (results.allGood) {
                // It all finished successfully
                val nip95s =
                    results.successful.mapNotNull {
                        it.result as? UploadOrchestrator.OrchestratorResult.NIP95Result
                    }

                val videosAndOthers =
                    results.successful.mapNotNull {
                        val map = it.result as? UploadOrchestrator.OrchestratorResult.ServerResult
                        if (map != null && !isImage(map.url, map.fileHeader.mimeType)) {
                            map
                        } else {
                            null
                        }
                    }

                val imageUrls =
                    results.successful
                        .mapNotNull {
                            val map = it.result as? UploadOrchestrator.OrchestratorResult.ServerResult
                            if (map != null && isImage(map.url, map.fileHeader.mimeType)) {
                                Pair(map.url, map.fileHeader)
                            } else {
                                null
                            }
                        }.toMap()

                val nip95jobs =
                    nip95s.map {
                        // upload each file as an individual nip95 event.
                        viewModelScope.launch(Dispatchers.IO) {
                            withTimeoutOrNull(30000) {
                                suspendCancellableCoroutine { continuation ->
                                    account?.createNip95(it.bytes, headerInfo = it.fileHeader, caption, if (sensitiveContent) "" else null) { nip95 ->
                                        account?.consumeAndSendNip95(nip95.first, nip95.second, relayList)
                                        continuation.resume(true)
                                    }
                                }
                            }
                        }
                    }

                val videoJobs =
                    videosAndOthers.map {
                        // upload each file as an individual nip95 event.
                        viewModelScope.launch(Dispatchers.IO) {
                            withTimeoutOrNull(30000) {
                                suspendCancellableCoroutine { continuation ->
                                    account?.sendHeader(
                                        it.url,
                                        it.magnet,
                                        it.fileHeader,
                                        caption,
                                        if (sensitiveContent) "" else null,
                                        it.uploadedHash,
                                        relayList,
                                    ) {
                                        continuation.resume(true)
                                    }
                                }
                            }
                        }
                    }

                val imageJobs =
                    if (imageUrls.isNotEmpty()) {
                        listOf(
                            viewModelScope.launch(Dispatchers.IO) {
                                withTimeoutOrNull(30000) {
                                    suspendCancellableCoroutine { continuation ->
                                        account?.sendAllAsOnePictureEvent(
                                            imageUrls,
                                            caption,
                                            if (sensitiveContent) "" else null,
                                            relayList,
                                        ) {
                                            continuation.resume(true)
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        emptyList()
                    }

                nip95jobs.joinAll()
                videoJobs.joinAll()
                imageJobs.joinAll()

                onSucess()
                onceUploaded()
                cancelModel()
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }
        }
    }

    open fun cancelModel() {
        multiOrchestrator = null
        isUploadingImage = false
        caption = ""
        selectedServer = defaultServer()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        multiOrchestrator?.remove(selected)
    }

    fun canPost(): Boolean = !isUploadingImage && multiOrchestrator != null && selectedServer != null

    fun defaultServer() = account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0]

    fun onceUploaded(onceUploaded: () -> Unit) {
        this.onceUploaded = onceUploaded
    }
}
