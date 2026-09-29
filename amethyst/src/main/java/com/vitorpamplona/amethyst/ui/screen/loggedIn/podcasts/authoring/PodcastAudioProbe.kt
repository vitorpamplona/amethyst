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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring.PodcastComposerMedia

object PodcastAudioProbe {
    /**
     * Reads duration + title from a picked audio file via [MediaMetadataRetriever] (heavy — call off
     * the main thread). Returns null if the provider rejects it or the file isn't a real container.
     */
    fun probeAudio(
        context: Context,
        uri: Uri,
    ): PodcastComposerMedia.ProbedAudio? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            PodcastComposerMedia.ProbedAudio(
                durationSeconds = durationMs?.let { (it / 1000).toInt().takeIf { secs -> secs > 0 } },
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifBlank { null },
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
