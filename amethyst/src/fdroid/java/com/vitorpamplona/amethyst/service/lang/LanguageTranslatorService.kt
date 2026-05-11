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
package com.vitorpamplona.amethyst.service.lang

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.Amethyst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

object LanguageTranslatorService {
    private val json = jacksonObjectMapper()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun clear() {
        TranslationsCache.clear()
    }

    suspend fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
        serverUrl: String,
        apiKey: String,
    ): ResultOrError? {
        if (serverUrl.isBlank() || !TranslationDictionary.isWorthTranslating(text)) return null

        val url = normalizedTranslateUrl(serverUrl) ?: return null
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val normalizedApiKey = apiKey.trim()

        return withContext(Dispatchers.IO) {
            val payload =
                mutableMapOf(
                    "q" to encoded,
                    "source" to "auto",
                    "target" to translateTo,
                )

            if (normalizedApiKey.isNotBlank()) {
                payload["api_key"] = normalizedApiKey
            }

            val requestBody =
                json
                    .writeValueAsString(payload)
                    .toRequestBody(jsonMediaType)

            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

            val client = Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForNip05(url)

            client.newCall(request).executeAsync().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalArgumentException("LibreTranslate returned HTTP ${response.code}")
                }

                val body = response.body.string()
                val tree = json.readTree(body)
                val translated = TranslationDictionary.decode(tree["translatedText"]?.asText(), dict)
                val source = tree["detectedLanguage"]?.get("language")?.asText()

                if (translated.isNullOrBlank()) return@use null
                if (source == null || source == "und") return@use null
                if (source.equals(translateTo, ignoreCase = true)) return@use null
                if (source in dontTranslateFrom) return@use null

                ResultOrError(translated, source, translateTo)
            }
        }
    }

    private fun normalizedTranslateUrl(serverUrl: String): String? {
        val root = serverUrl.trim().trimEnd('/')
        if (root.isBlank()) return null
        val baseUrl = root.toHttpUrlOrNull() ?: return null
        if (baseUrl.encodedPath.trimEnd('/').endsWith("/translate")) return baseUrl.toString()
        return baseUrl
            .newBuilder()
            .addPathSegment("translate")
            .build()
            .toString()
    }
}
