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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import kotlinx.collections.immutable.ImmutableList

@Stable
class ChatFileUploadState(
    val defaultServer: ServerName,
) {
    var isUploadingImage by mutableStateOf(false)

    var selectedServer by mutableStateOf(defaultServer)
    var caption by mutableStateOf("")

    var contentWarning by mutableStateOf(false)
        private set

    var contentWarningReason by mutableStateOf<String?>(null)

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(1)

    fun load(uris: ImmutableList<SelectedMedia>) {
        reset()
        this.multiOrchestrator = MultiOrchestrator(uris)
    }

    fun isImage(
        url: String,
        mimeType: String?,
    ): Boolean = mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)

    fun reset() {
        multiOrchestrator = null
        isUploadingImage = false
        caption = ""
        selectedServer = defaultServer
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        multiOrchestrator?.remove(selected)
    }

    fun canPost(): Boolean = !isUploadingImage && multiOrchestrator != null

    fun hasPickedMedia() = multiOrchestrator != null

    fun updateContentWarning(value: Boolean) {
        contentWarning = value
        if (value) {
            contentWarningReason = ""
        } else {
            contentWarningReason = null
        }
    }
}
