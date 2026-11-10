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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.util.concurrent.TimeUnit

private fun Request.Builder.auth(key: AgentKeyTag): Request.Builder =
    apply {
        if (key.hasKey()) header("Authorization", "Bearer ${key.key}")
        // Anthropic's compatibility layer still wants its own key header and a version.
        if (isAnthropic(key.url)) {
            if (key.hasKey()) header("x-api-key", key.key)
            header("anthropic-version", "2023-06-01")
        }
    }

private fun root(key: AgentKeyTag) = key.url.trimEnd('/')

actual suspend fun IRoleBasedHttpClientBuilder.listAgentModels(key: AgentKeyTag): List<AgentModel> =
    withContext(Dispatchers.IO) {
        val url = root(key) + "/models"
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .auth(key)
                .build()
        try {
            okHttpClientForMoney(url).newCall(request).executeAsync().use { response ->
                if (!response.isSuccessful) throw WriterException(failureFor(response.code), response.code)
                readModels(response.body.string())
            }
        } catch (e: IOException) {
            throw WriterException(WriterFailure.UNREACHABLE)
        }
    }

private val JSON = "application/json".toMediaType()

actual suspend fun IRoleBasedHttpClientBuilder.chatComplete(
    key: AgentKeyTag,
    model: String,
    messages: List<ChatMessage>,
    onText: (Int) -> Unit,
): Written =
    withContext(Dispatchers.IO) {
        val url = root(key) + "/chat/completions"
        // A page takes minutes to write: no read timeout between streamed chunks of that size.
        val client = okHttpClientForMoney(url).newBuilder().readTimeout(5, TimeUnit.MINUTES).build()

        fun call(stream: Boolean) =
            Request
                .Builder()
                .url(url)
                .post(chatRequestBody(key.url, model, messages, stream).toRequestBody(JSON))
                .auth(key)
                .build()

        try {
            client.newCall(call(stream = true)).executeAsync().use { response ->
                val isStream = (response.body.contentType()?.toString() ?: response.header("Content-Type").orEmpty()).contains("text/event-stream")
                when {
                    response.isSuccessful && isStream -> return@withContext readStream(response, onText)
                    response.isSuccessful -> return@withContext readCompletion(response.body.string()).also { onText(it.text.length) }
                    // A provider that refuses streaming says so with one of these; try once without.
                    response.code !in setOf(400, 415, 422) -> throw failure(response)
                }
            }
            client.newCall(call(stream = false)).executeAsync().use { response ->
                if (!response.isSuccessful) throw failure(response)
                readCompletion(response.body.string()).also { onText(it.text.length) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw WriterException(WriterFailure.UNREACHABLE)
        }
    }

private fun failure(response: Response): WriterException {
    val said =
        runCatching { response.body.string() }
            .getOrDefault("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(160)
    return WriterException(failureFor(response.code), response.code, said)
}

private suspend fun readStream(
    response: Response,
    onText: (Int) -> Unit,
): Written {
    val text = StringBuilder()
    var usage: Usage? = null
    val source = response.body.source()
    while (true) {
        currentCoroutineContext().ensureActive()
        val line = source.readUtf8Line() ?: break
        val chunk = parseStreamLine(line)
        if (chunk.text.isNotEmpty()) {
            text.append(chunk.text)
            onText(text.length)
        }
        if (chunk.usage != null) usage = chunk.usage
        if (chunk.done) break
    }
    return Written(text.toString(), usage)
}
