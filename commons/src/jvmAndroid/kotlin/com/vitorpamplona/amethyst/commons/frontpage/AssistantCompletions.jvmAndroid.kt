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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One chat-completions request on the user's AI key, as the Observer's writer sends its own:
 * the same auth, the same refusal of a key over cleartext, the same metered money route. Returns
 * the response body; a refusal throws [IOException] with what the provider said.
 */
suspend fun IRoleBasedHttpClientBuilder.postChatCompletion(
    key: AgentKeyTag,
    body: String,
): String =
    withContext(Dispatchers.IO) {
        val url =
            try {
                root(key) + "/chat/completions"
            } catch (e: WriterException) {
                throw IOException("This key's address isn't secure (http to another computer), so it isn't used.")
            }
        val request =
            try {
                Request
                    .Builder()
                    .url(url)
                    .post(body.toRequestBody(JSON_TYPE))
                    .auth(key)
                    .build()
            } catch (e: IllegalArgumentException) {
                throw IOException("The key's address isn't a valid URL: ${key.url}")
            }
        val client = okHttpClientForMoney(url).newBuilder().readTimeout(3, TimeUnit.MINUTES).build()
        client.newCall(request).executeAsync().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw IOException("The AI provider answered ${response.code}: ${text.take(400)}")
            text
        }
    }

/** The ids of the models [key] can use, as the provider lists them. */
suspend fun IRoleBasedHttpClientBuilder.listChatModelIds(key: AgentKeyTag): List<String> =
    try {
        listAgentModels(key).map { it.id }
    } catch (e: WriterException) {
        throw IOException("Could not list the models: ${e.failure}", e)
    }

private val JSON_TYPE = "application/json".toMediaType()
