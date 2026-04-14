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

import android.content.Context
import android.net.Uri
import com.davotoula.lightcompressor.HlsPreparer
import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.hls.HlsConfig
import com.davotoula.lightcompressor.hls.HlsLadder
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Runs a full HLS preparation over the given source URI and returns the resulting [HlsBundle] once
 * every rendition has been emitted, combined files moved into [workDir], and the master playlist
 * received. Defaults to the library-provided [HlsConfig] (all five renditions of
 * [com.davotoula.lightcompressor.hls.HlsLadder.default], single-file-per-rendition, 6s segments);
 * the caller picks the video codec.
 *
 * Cancellation: if the caller's coroutine is cancelled while awaiting the bundle, we forward that
 * cancellation to [HlsPreparer.cancel] so MediaCodec work stops. The underlying temp dir created by
 * HlsPreparer is cleaned up by the library; the caller is responsible for cleaning up [workDir]
 * after uploading is done.
 *
 * Not concurrent-safe: [HlsPreparer] is a process-wide singleton and only supports one preparation
 * at a time. Overlapping calls will cancel the previous preparation.
 */
object HlsTranscoder {
    suspend fun transcode(
        context: Context,
        uri: Uri,
        workDir: File,
        codec: VideoCodec,
        ladder: HlsLadder = HlsLadder.default(),
        onRenditionProgress: (label: String, percent: Int) -> Unit = { _, _ -> },
    ): HlsBundle {
        workDir.mkdirs()
        val session = HlsTranscodingSession(workDir, onRenditionProgress)
        val config = HlsConfig(codec = codec, ladder = ladder)

        HlsPreparer.start(
            context = context,
            uri = uri,
            config = config,
            listener = session,
        )

        return try {
            session.terminal.await()
        } catch (e: CancellationException) {
            HlsPreparer.cancel()
            throw e
        }
    }
}
