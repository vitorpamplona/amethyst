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

import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import java.io.File

/**
 * Abstraction over a blob upload transport so [HlsUploadPipeline] can stay unit-testable.
 * Production wiring adapts this to either [com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader]
 * or [com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader].
 */
fun interface HlsBlobUploader {
    suspend fun upload(
        file: File,
        contentType: String,
    ): MediaUploadResult
}

data class HlsUploadResult(
    val masterUrl: String,
    val masterSha256: String?,
    val renditions: List<HlsUploadedRendition>,
)

data class HlsUploadedRendition(
    val label: String,
    val combinedUrl: String,
    val combinedSha256: String?,
    val combinedSize: Long?,
    val playlistUrl: String,
    val bitrateKbps: Int,
)

/**
 * Orchestrates the upload half of the HLS publish pipeline. For each rendition:
 *  1. uploads the combined fMP4 file,
 *  2. rewrites the media playlist so its byterange entries point at the uploaded blob URL,
 *  3. uploads the rewritten playlist.
 * Finally rewrites the master playlist to reference the per-rendition playlist URLs and uploads
 * the master. The resulting [HlsUploadResult] is what the publisher uses to build the NIP-71
 * event.
 */
class HlsUploadPipeline(
    private val uploader: HlsBlobUploader,
) {
    suspend fun upload(
        bundle: HlsBundle,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): HlsUploadResult {
        val playlistDir = File(bundle.workDir, "playlists").apply { mkdirs() }
        val total = bundle.renditions.size * 2 + 1
        var done = 0

        val uploadedRenditions =
            bundle.renditions.map { rendition ->
                val combined = uploader.upload(rendition.combinedFile, CONTENT_TYPE_VIDEO_MP4)
                onProgress(++done, total)
                val combinedUrl =
                    withExtensionHint(
                        combined.url ?: error("Uploader returned null URL for ${rendition.combinedFile.name}"),
                        CONTENT_TYPE_VIDEO_MP4,
                    )

                val rewrittenMedia =
                    HlsPlaylistRewriter.rewrite(
                        rendition.mediaPlaylist,
                        mapOf("${rendition.label}.mp4" to combinedUrl),
                    )
                val mediaPlaylistFile =
                    File(playlistDir, "${rendition.label}-media.m3u8").apply { writeText(rewrittenMedia) }
                val mediaPlaylist = uploader.upload(mediaPlaylistFile, CONTENT_TYPE_HLS)
                onProgress(++done, total)
                val mediaPlaylistUrl =
                    withExtensionHint(
                        mediaPlaylist.url ?: error("Uploader returned null URL for media playlist ${rendition.label}"),
                        CONTENT_TYPE_HLS,
                    )

                HlsUploadedRendition(
                    label = rendition.label,
                    combinedUrl = combinedUrl,
                    combinedSha256 = combined.sha256,
                    combinedSize = combined.size,
                    playlistUrl = mediaPlaylistUrl,
                    bitrateKbps = rendition.bitrateKbps,
                )
            }

        val masterUrlMap =
            uploadedRenditions.associate { "${it.label}/media.m3u8" to it.playlistUrl }
        val rewrittenMaster = HlsPlaylistRewriter.rewrite(bundle.masterPlaylist, masterUrlMap)
        val masterFile = File(playlistDir, "master.m3u8").apply { writeText(rewrittenMaster) }
        val master = uploader.upload(masterFile, CONTENT_TYPE_HLS)
        onProgress(++done, total)
        val masterUrl =
            withExtensionHint(
                master.url ?: error("Uploader returned null URL for master playlist"),
                CONTENT_TYPE_HLS,
            )

        return HlsUploadResult(
            masterUrl = masterUrl,
            masterSha256 = master.sha256,
            renditions = uploadedRenditions,
        )
    }

    // Blossom servers typically return bare-hash URLs (https://server/<sha256>), but HLS parsers
    // and ExoPlayer's Util.inferContentType sniff the URL extension to pick the right source
    // factory. Append a hint unless the upload server already baked one in.
    private fun withExtensionHint(
        url: String,
        contentType: String,
    ): String {
        val ext =
            when (contentType) {
                CONTENT_TYPE_VIDEO_MP4 -> ".mp4"
                CONTENT_TYPE_HLS -> ".m3u8"
                else -> return url
            }
        return if (url.endsWith(ext, ignoreCase = true)) url else url + ext
    }

    companion object {
        const val CONTENT_TYPE_VIDEO_MP4 = "video/mp4"
        const val CONTENT_TYPE_HLS = "application/vnd.apple.mpegurl"
    }
}
