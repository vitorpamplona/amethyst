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
import com.davotoula.lightcompressor.hls.HlsLadder
import com.vitorpamplona.amethyst.service.uploads.hls.HlsBlobUploader
import com.vitorpamplona.amethyst.service.uploads.hls.HlsBundle
import com.vitorpamplona.amethyst.service.uploads.hls.HlsUploadPipeline
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
 * All Android/account-specific concerns are injected as suspending callbacks so the whole state
 * machine is unit-testable.
 *
 * State transitions: Idle → Transcoding → Uploading → Publishing → Success, or → Failure on any
 * exception. The [state] flow emits each transition as it happens so the UI can reflect progress.
 */
class HlsPublishOrchestrator(
    private val _state: MutableStateFlow<HlsPublishState>,
    private val runTranscode: suspend (
        workDir: File,
        codec: VideoCodec,
        ladder: HlsLadder,
        onProgress: (label: String, percent: Int) -> Unit,
    ) -> HlsBundle,
    private val buildUploader: (ServerName) -> HlsBlobUploader,
    private val signAndPublish: suspend (HlsVideoEventTemplate) -> String,
    private val workDirFactory: () -> File,
) {
    val state: StateFlow<HlsPublishState> = _state

    suspend fun publish(request: HlsPublishRequest) {
        val workDir = workDirFactory()
        try {
            _state.value = HlsPublishState.Transcoding(currentLabel = "", percent = 0)
            val bundle =
                runTranscode(workDir, request.codec, request.ladder) { label, percent ->
                    _state.value = HlsPublishState.Transcoding(label, percent)
                }

            val uploadTotal = bundle.renditions.size * 2 + 1
            _state.value = HlsPublishState.Uploading(done = 0, total = uploadTotal)
            val uploader = buildUploader(request.server)
            val pipeline = HlsUploadPipeline(uploader)
            val uploadResult =
                pipeline.upload(bundle) { done, total ->
                    _state.value = HlsPublishState.Uploading(done, total)
                }

            _state.value = HlsPublishState.Publishing
            val template =
                HlsVideoEventBuilder.build(
                    HlsVideoPublishInput(
                        bundle = bundle,
                        uploadResult = uploadResult,
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
                    masterUrl = uploadResult.masterUrl,
                )
        } catch (e: CancellationException) {
            _state.value = HlsPublishState.Failure(message = "Cancelled")
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
