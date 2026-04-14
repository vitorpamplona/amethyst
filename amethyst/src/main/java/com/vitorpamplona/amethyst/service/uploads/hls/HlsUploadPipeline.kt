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
 *
 * URL handling policy: the pipeline uses the URL the server returned verbatim. No extension is
 * appended, no trailing dot stripped, no bare-hash rewriting. The server is responsible for
 * returning a URL that the player can fetch as-is — that way we get the best cache coherence,
 * correct Content-Type, clean range requests, and no double round trips. If a server returns an
 * unplayable URL, the fix is server-side.
 */
class HlsUploadPipeline(
    private val uploader: HlsBlobUploader,
) {
    suspend fun upload(
        bundle: HlsBundle,
        onProgress: (done: Int, total: Int, currentLabel: String) -> Unit = { _, _, _ -> },
    ): HlsUploadResult {
        val playlistDir = File(bundle.workDir, "playlists").apply { mkdirs() }
        val total = bundle.renditions.size * 2 + 1
        var done = 0

        val uploadedRenditions =
            bundle.renditions.map { rendition ->
                onProgress(done, total, "${rendition.label} video")
                val combined = uploader.upload(rendition.combinedFile, CONTENT_TYPE_VIDEO_MP4)
                done++
                val combinedUrl =
                    combined.url ?: error("Uploader returned null URL for ${rendition.combinedFile.name}")

                val rewrittenMedia =
                    HlsPlaylistRewriter.rewrite(
                        rendition.mediaPlaylist,
                        mapOf("${rendition.label}.mp4" to combinedUrl),
                    )
                val mediaPlaylistFile =
                    File(playlistDir, "${rendition.label}-media.m3u8").apply { writeText(rewrittenMedia) }
                onProgress(done, total, "${rendition.label} playlist")
                val mediaPlaylist = uploader.upload(mediaPlaylistFile, CONTENT_TYPE_HLS)
                done++
                val mediaPlaylistUrl =
                    mediaPlaylist.url ?: error("Uploader returned null URL for media playlist ${rendition.label}")

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
        onProgress(done, total, "master playlist")
        val master = uploader.upload(masterFile, CONTENT_TYPE_HLS)
        done++
        val masterUrl =
            master.url ?: error("Uploader returned null URL for master playlist")

        onProgress(done, total, "")

        return HlsUploadResult(
            masterUrl = masterUrl,
            masterSha256 = master.sha256,
            renditions = uploadedRenditions,
        )
    }

    companion object {
        const val CONTENT_TYPE_VIDEO_MP4 = "video/mp4"
        const val CONTENT_TYPE_HLS = "application/vnd.apple.mpegurl"
    }
}
