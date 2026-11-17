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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.Volatile

class SelectedMediaProcessing(
    media: SelectedMedia,
    val orchestrator: UploadOrchestrator = UploadOrchestrator(),
    options: MediaUploadOptions = MediaUploadOptions.defaultFor(media),
) {
    /**
     * The picked file, labelled by what it holds once [checkType] has looked: a voice memo saved as
     * `.mp4` is picked as `video/mp4` and becomes `audio/mp4`. Snapshot state, so the Studio's tools
     * and the composer's card follow.
     */
    var media: SelectedMedia by mutableStateOf(media)
        private set

    @Volatile
    private var typeChecked = false

    /**
     * Relabels [media] by its content, once: an MP4-family file whose tracks are all sound is audio.
     * The Studio runs it as it opens, and every upload runs it first, so nothing is sent as a video
     * that has no picture.
     */
    suspend fun checkType(uploader: MediaUploader) {
        if (typeChecked) return
        val refined = uploader.refineMediaType(media)
        typeChecked = true
        if (refined.mimeType != media.mimeType) media = refined
    }

    /** This file's own upload options, edited in the media studio; snapshot state so its UI follows. */
    var options: MediaUploadOptions by mutableStateOf(options)

    /** Width over height as the file displays, once its picture (or a video's frame) has loaded; null until then. */
    var displayAspectRatio: Float? by mutableStateOf(null)

    /** Width over height as it will be sent: [displayAspectRatio] after the [MediaUploadOptions.crop]; null until the picture loads. */
    val sentAspectRatio: Float?
        get() = displayAspectRatio?.let { aspect -> options.crop?.let { aspect * it.width / it.height } ?: aspect }

    /**
     * What [MultiOrchestrator.uploadEach] last sent for this file in plaintext, kept so a retry after
     * another file failed doesn't send this one again. Dropped when its options or the server change.
     */
    @Volatile
    internal var sentPlain: MultiOrchestrator.SentPlain? = null

    /**
     * The encrypted blob [MultiOrchestrator.uploadEncrypted] last sent for this file, with its cipher,
     * kept so a retry reuses it instead of sealing the file again. Dropped when the file is sent again.
     */
    @Volatile
    internal var sentEncrypted: MultiOrchestrator.SentEncrypted? = null
}
