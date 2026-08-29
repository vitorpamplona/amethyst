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
package com.vitorpamplona.amethyst.commons.uploads.hls

/**
 * Turns one video into an HLS ladder and hands each produced file to the
 * caller to upload.
 *
 * The interleaving is the point: a rendition's segments are uploaded as they
 * are produced, so a long publish is not "transcode everything, then upload
 * everything" — which on a phone means holding the whole ladder on disk, and on
 * any connection means a progress bar that sits still for minutes.
 *
 * Android runs LightCompressor's `MediaCodec` pipeline; the desktop runs
 * ffmpeg. Everything above this line — the ladder, the playlists, the NIP-71
 * event built from the rendition summaries — is shared.
 */
interface HlsTranscoder {
    /**
     * [uploadFile] is called for every produced file with its path and content
     * type, and returns where it landed. Throwing from it aborts the publish.
     *
     * Returns null when this platform has no HLS pipeline, which the caller
     * reports rather than treating as an empty result.
     */
    suspend fun <T> run(
        sourceUri: String,
        config: HlsConfig,
        listener: HlsListener,
        uploadFile: suspend (path: String, contentType: String) -> HlsUploaded<T>,
    ): HlsUploadResult<T>?

    companion object {
        @Volatile
        var installed: HlsTranscoder? = null
    }
}
