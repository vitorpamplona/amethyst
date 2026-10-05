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
package com.vitorpamplona.amethyst.service.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.vitorpamplona.amethyst.commons.observer.ObserverWriter
import com.vitorpamplona.amethyst.commons.observer.ObserverWriterStatus
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

/**
 * The Observer's writer on Gemini Nano, through ML Kit's GenAI Prompt API and AICore — the
 * same on-device model the composer's writing assistant uses, so nothing leaves the phone.
 *
 * **Requests are detached, never cancelled.** Cancelling an in-flight ML Kit GenAI request
 * crashes the process from ML Kit's own worker thread (see `GenAiFutures.awaitDetached`). The
 * Prompt API is a suspend API, so the same rule is kept the coroutine way: each generation runs
 * in this writer's own scope, the caller only awaits it, and a caller that times out or is
 * cancelled stops waiting without touching the request. [close] waits for the last request to
 * finish before releasing the model for the same reason.
 */
class GeminiNanoObserverWriter : ObserverWriter {
    // Lazy: building the client on a phone without AICore can throw, and that belongs inside
    // status(), where the press already treats any failure as "no model", not in a constructor.
    private val modelLazy = lazy { Generation.getClient() }
    private val model: GenerativeModel get() = modelLazy.value

    /** Owns every in-flight request; deliberately not a child of any caller. */
    private val requests = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var inFlight: Deferred<String?>? = null

    @Volatile
    private var systemPrompts: Boolean? = null

    override suspend fun status(): ObserverWriterStatus = statusOf(model.checkStatus())

    override suspend fun download(): ObserverWriterStatus {
        // The flow ends with Completed or Failed; anything else is progress.
        val outcome = model.download().first { it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed }
        if (outcome is DownloadStatus.DownloadFailed) Log.w(TAG, "Gemini Nano download failed", outcome.e)
        return status()
    }

    override suspend fun write(
        instruction: String,
        input: String,
        maxOutputTokens: Int,
    ): String? {
        // One at a time: AICore serves one inference per client, and a request left running by
        // a timeout must finish before the next one is queued behind it.
        // join, not await: the previous request's own failure is not this caller's problem,
        // but this caller being cancelled still is.
        inFlight?.join()

        val useSystemPrompt = systemPrompts ?: supportsSystemPrompt().also { systemPrompts = it }
        val request =
            if (useSystemPrompt) {
                generateContentRequest(SystemInstruction(instruction), TextPart(input)) {
                    temperature = TEMPERATURE
                    topK = TOP_K
                    this.maxOutputTokens = maxOutputTokens
                }
            } else {
                // No system role on this device: the brief goes first, the material after it.
                generateContentRequest(TextPart(instruction + "\n\n" + input)) {
                    temperature = TEMPERATURE
                    topK = TOP_K
                    this.maxOutputTokens = maxOutputTokens
                }
            }

        val job =
            requests.async {
                model
                    .generateContent(request)
                    .candidates
                    .firstOrNull()
                    ?.text
            }
        inFlight = job
        return job.await()
    }

    private suspend fun supportsSystemPrompt(): Boolean =
        try {
            model.isSystemPromptAvailable()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not ask whether Gemini Nano takes a system prompt", e)
            false
        }

    override fun close() {
        val last = inFlight
        if (last == null || last.isCompleted) {
            release()
        } else {
            last.invokeOnCompletion { release() }
        }
    }

    private fun release() {
        if (modelLazy.isInitialized()) {
            try {
                model.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not close Gemini Nano", e)
            }
        }
        requests.cancel()
    }

    private fun statusOf(status: Int): ObserverWriterStatus =
        when (status) {
            FeatureStatus.AVAILABLE -> ObserverWriterStatus.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> ObserverWriterStatus.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> ObserverWriterStatus.DOWNLOADING
            else -> ObserverWriterStatus.UNAVAILABLE
        }

    companion object {
        private const val TAG = "GeminiNanoObserverWriter"

        /** Low: a newspaper summary should say what the post says, not improvise. */
        private const val TEMPERATURE = 0.2f
        private const val TOP_K = 16
    }
}
