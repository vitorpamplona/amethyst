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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls

import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.hls.HlsConfig
import com.davotoula.lightcompressor.hls.HlsContentTypes
import com.davotoula.lightcompressor.hls.HlsLadder
import com.davotoula.lightcompressor.hls.HlsListener
import com.davotoula.lightcompressor.hls.HlsUploadResult
import com.davotoula.lightcompressor.hls.HlsUploaded
import com.davotoula.lightcompressor.hls.Rendition
import com.davotoula.lightcompressor.hls.SimpleHlsListener
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.service.uploads.hls.HlsBlobUploader
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoEventBuilder
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoEventTemplate
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoPublishInput
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class HlsPublishRequest(
    val title: String,
    val description: String,
    val sensitiveContent: Boolean,
    val contentWarningReason: String,
    val codec: VideoCodec,
    val server: ServerName,
    val ladder: HlsLadder = HlsLadder.default(),
    val durationSeconds: Int? = null,
)

/**
 * Orchestrates the transcode → upload → build → publish pipeline for a single HLS video publish.
 * Delegates transcoding and segment/media-playlist upload plumbing to the library's
 * [com.davotoula.lightcompressor.hls.HlsUploadHelper] via the injected [runUpload] closure, then
 * uploads the rewritten master playlist itself, builds the NIP-71 event, signs, and publishes.
 * All Android/account-specific concerns are injected as suspending callbacks so the whole state
 * machine is unit-testable.
 *
 * State transitions: Idle → Transcoding (per rendition, driven by listener) → Uploading (per
 * segment/playlist upload, driven by the uploader lambda) → Publishing → Success, or → Failure
 * on any exception.
 */
class HlsPublishOrchestrator(
    private val _state: MutableStateFlow<HlsPublishState>,
    private val runUpload: suspend (
        config: HlsConfig,
        listener: HlsListener,
        uploadFile: suspend (File, String) -> HlsUploaded<MediaUploadResult>,
    ) -> HlsUploadResult<MediaUploadResult>,
    private val buildUploader: (ServerName) -> HlsBlobUploader,
    private val uploadMaster: suspend (HlsBlobUploader, String) -> MediaUploadResult,
    private val signAndPublish: suspend (HlsVideoEventTemplate) -> String,
) {
    val state: StateFlow<HlsPublishState> = _state

    suspend fun publish(request: HlsPublishRequest) {
        try {
            _state.value = HlsPublishState.Transcoding(currentLabel = "", percent = 0)

            val uploader = buildUploader(request.server)
            val config =
                HlsConfig(
                    codec = request.codec,
                    ladder = request.ladder,
                )

            val totalUploads = request.ladder.renditions.size * 2 + 1
            var uploadsDone = 0

            // Dedup Transcoding state emissions so onProgress (which fires many times per
            // integer percent) doesn't flood the StateFlow with identical values.
            var lastTranscodingLabel: String? = null
            var lastTranscodingPercent = -1

            val listener =
                object : SimpleHlsListener() {
                    override fun onRenditionStart(rendition: Rendition) {
                        val label = rendition.resolution.label
                        if (lastTranscodingLabel != label || lastTranscodingPercent != 0) {
                            lastTranscodingLabel = label
                            lastTranscodingPercent = 0
                            _state.value = HlsPublishState.Transcoding(label, 0)
                        }
                    }

                    override fun onProgress(
                        rendition: Rendition,
                        percent: Float,
                    ) {
                        val label = rendition.resolution.label
                        val p = percent.toInt()
                        if (lastTranscodingLabel != label || lastTranscodingPercent != p) {
                            lastTranscodingLabel = label
                            lastTranscodingPercent = p
                            _state.value = HlsPublishState.Transcoding(label, p)
                        }
                    }
                }

            val uploadResult =
                runUpload(config, listener) { file, suggestedFilename ->
                    // Pre-increment: `done` tracks the in-flight index, not the finished
                    // count. StateFlow conflation would otherwise eat the post-upload tick.
                    uploadsDone++
                    _state.value =
                        HlsPublishState.Uploading(
                            done = uploadsDone,
                            total = totalUploads,
                            currentLabel = suggestedFilename,
                        )
                    val contentType =
                        if (suggestedFilename.endsWith(".m3u8")) {
                            HlsContentTypes.HLS_PLAYLIST
                        } else {
                            HlsContentTypes.FMP4_SEGMENT
                        }
                    val result = uploader.upload(file, contentType)
                    HlsUploaded(
                        url =
                            result.url
                                ?: error("Uploader returned null URL for $suggestedFilename"),
                        metadata = result,
                    )
                }

            uploadsDone++
            _state.value =
                HlsPublishState.Uploading(
                    done = uploadsDone,
                    total = totalUploads,
                    currentLabel = "master.m3u8",
                )
            val masterUpload = uploadMaster(uploader, uploadResult.masterPlaylist)
            val masterUrl =
                masterUpload.url ?: error("Uploader returned null URL for master playlist")

            _state.value = HlsPublishState.Publishing
            val template =
                HlsVideoEventBuilder.build(
                    HlsVideoPublishInput(
                        renditions = uploadResult.renditions,
                        uploads = uploadResult.uploads,
                        masterUrl = masterUrl,
                        masterSha256 = masterUpload.sha256,
                        title = request.title,
                        description = request.description,
                        durationSeconds = request.durationSeconds,
                        contentWarning = contentWarningOrNull(request),
                    ),
                )
            val eventId = signAndPublish(template)

            _state.value =
                HlsPublishState.Success(
                    eventId = eventId,
                    masterUrl = masterUrl,
                )
        } catch (e: CancellationException) {
            // Cancellation is not a failure. Let the rethrow propagate up the coroutine
            // scope; NewHlsVideoViewModel.cancel() calls reset() to put state back to Idle.
            throw e
        } catch (e: Throwable) {
            _state.value = HlsPublishState.Failure(message = e.message ?: e::class.simpleName.orEmpty())
        }
    }

    fun reset() {
        _state.value = HlsPublishState.Idle
    }

    private fun contentWarningOrNull(request: HlsPublishRequest): String? = if (request.sensitiveContent) request.contentWarningReason else null
}
