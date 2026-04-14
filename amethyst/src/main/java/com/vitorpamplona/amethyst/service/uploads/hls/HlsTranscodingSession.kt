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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.davotoula.lightcompressor.hls.HlsError
import com.davotoula.lightcompressor.hls.HlsListener
import com.davotoula.lightcompressor.hls.HlsSegment
import com.davotoula.lightcompressor.hls.Rendition
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.io.IOException

/**
 * Accumulates HlsListener callbacks from a single-file-per-rendition HLS preparation into an
 * [HlsBundle] that the upload pipeline can consume. The combined fMP4 files emitted by
 * `onSegmentReady` are moved (renameTo, with copyTo fallback) to [workDir]/{label}.mp4 so the
 * library's temp dir can be cleaned up and the bundle is self-contained.
 *
 * Terminal states are exposed via [terminal]: completes with [HlsBundle] on success, completes
 * exceptionally on failure, is cancelled on user cancel.
 */
class HlsTranscodingSession(
    private val workDir: File,
    private val onRenditionProgress: (label: String, percent: Int) -> Unit = { _, _ -> },
) : HlsListener {
    val terminal: CompletableDeferred<HlsBundle> = CompletableDeferred()

    private val combinedByLabel = mutableMapOf<String, File>()
    private val completed = mutableListOf<HlsBundleRendition>()

    override fun onStart(renditionCount: Int) = Unit

    override fun onRenditionStart(rendition: Rendition) = Unit

    override fun onSegmentReady(
        rendition: Rendition,
        segment: HlsSegment,
    ) {
        if (!segment.isCombinedRendition) return

        val target = File(workDir, "${rendition.resolution.label}.mp4")
        if (target.exists() && !target.delete()) {
            throw IOException("Could not replace existing $target")
        }
        if (!segment.file.renameTo(target)) {
            segment.file.copyTo(target, overwrite = true)
        }
        combinedByLabel[rendition.resolution.label] = target
    }

    override fun onRenditionComplete(
        rendition: Rendition,
        playlist: String,
    ) {
        val combined =
            combinedByLabel[rendition.resolution.label]
                ?: error("onRenditionComplete without prior onSegmentReady for ${rendition.resolution.label}")
        completed +=
            HlsBundleRendition(
                label = rendition.resolution.label,
                combinedFile = combined,
                mediaPlaylist = playlist,
                bitrateKbps = rendition.bitrateKbps,
            )
    }

    override fun onComplete(masterPlaylist: String) {
        terminal.complete(
            HlsBundle(
                workDir = workDir,
                masterPlaylist = masterPlaylist,
                renditions = completed.toList(),
            ),
        )
    }

    override fun onFailure(error: HlsError) {
        terminal.completeExceptionally(HlsTranscodingException(error.message))
    }

    override fun onCancelled() {
        terminal.cancel()
    }

    override fun onProgress(
        rendition: Rendition,
        percent: Float,
    ) {
        onRenditionProgress(rendition.resolution.label, percent.toInt())
    }
}

class HlsTranscodingException(
    message: String,
) : RuntimeException(message)
