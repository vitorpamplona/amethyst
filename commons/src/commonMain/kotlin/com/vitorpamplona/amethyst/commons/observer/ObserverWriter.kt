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
package com.vitorpamplona.amethyst.commons.observer

/**
 * The Observer's writer: an on-device language model that turns the ranked
 * material into headlines and summaries. This is the step the web Observer
 * hands to a hosted model; here it runs on the phone, so nothing leaves it.
 *
 * Deliberately thin — one instruction and one input in, text out. What to ask
 * and whether to trust the answer is decided in [ObserverCopy], in common code
 * where it can be tested; a platform only has to run the model. Mirrors the
 * composer's `WritingAssistant` port: Android's Play build backs it with Gemini
 * Nano, the F-Droid build has none, and the paper falls back to the authors'
 * own words wherever there is no writer.
 */
interface ObserverWriter {
    suspend fun status(): ObserverWriterStatus

    /**
     * Fetches the model when [status] said [ObserverWriterStatus.DOWNLOADABLE],
     * suspending until it is ready or has failed. Returns the status afterwards.
     */
    suspend fun download(): ObserverWriterStatus

    /**
     * Runs one request. [instruction] is the fixed editorial brief and
     * [input] the untrusted material it applies to; implementations must keep
     * them apart (a system instruction where the model supports one). Returns
     * null when the model produced nothing.
     */
    suspend fun write(
        instruction: String,
        input: String,
        maxOutputTokens: Int,
    ): String?

    fun close()
}

enum class ObserverWriterStatus {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}
