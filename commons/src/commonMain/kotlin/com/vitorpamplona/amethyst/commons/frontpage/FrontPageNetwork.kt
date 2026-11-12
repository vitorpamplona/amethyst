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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag

/** Why the writer failed, for the UI to put in words (commons has no strings). */
enum class WriterFailure {
    REJECTED,
    NO_CREDIT,
    RATE_LIMITED,
    UNKNOWN_MODEL,
    UNREACHABLE,
    EMPTY,
    HTTP,
    UNSUPPORTED,

    /** The answer stopped before the page was done: an error frame, a `finish_reason` other than `stop`, or no end at all. */
    CUT_OFF,

    /** The key's URL breaks NIP-FA's https rule, so the key was never sent. */
    INSECURE_URL,
}

class WriterException(
    val failure: WriterFailure,
    val status: Int? = null,
    /** What the provider said, trimmed; shown only for [WriterFailure.HTTP]. */
    val providerSaid: String = "",
) : Exception("Writer failed: $failure ${status ?: ""}")

fun failureFor(status: Int): WriterFailure =
    when (status) {
        401, 403 -> WriterFailure.REJECTED
        402 -> WriterFailure.NO_CREDIT
        429 -> WriterFailure.RATE_LIMITED
        404 -> WriterFailure.UNKNOWN_MODEL
        else -> WriterFailure.HTTP
    }

/** The models `GET {url}/models` lists for this key, with prices where it gives them; ranking them is [rankModels]'s job. */
expect suspend fun IRoleBasedHttpClientBuilder.listAgentModels(key: AgentKeyTag): List<AgentModel>

/**
 * One Chat Completions call, streamed so the reader sees the model think and the page arrive
 * ([onEvent]: sent, first byte, reasoning, text); non-streamed when the provider refuses a stream,
 * said with [WriterEvent.AllAtOnce] before the whole answer comes.
 * @throws WriterException
 */
expect suspend fun IRoleBasedHttpClientBuilder.chatComplete(
    key: AgentKeyTag,
    model: String,
    messages: List<ChatMessage>,
    onEvent: (WriterEvent) -> Unit,
): Written
