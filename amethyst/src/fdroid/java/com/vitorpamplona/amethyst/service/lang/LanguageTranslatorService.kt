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
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.io.IOException

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

object LanguageTranslatorService {
    private const val MIN_DETECTION_CONFIDENCE = 0.6

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun clear() {
        TranslationsCache.clear()
    }

    suspend fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
        externalTranslatorUrl: String,
        okHttpClientForUrl: (String) -> OkHttpClient,
    ): ResultOrError? {
        val serverUrl = normalizeTranslatorUrl(externalTranslatorUrl) ?: return null
        if (translateTo.isBlank() || !TranslationDictionary.isWorthTranslating(text)) return null

        return withContext(Dispatchers.IO) {
            translateOrSkip(text, dontTranslateFrom, translateTo, serverUrl, okHttpClientForUrl)
        }
    }

    private suspend fun translateOrSkip(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
        serverUrl: String,
        okHttpClientForUrl: (String) -> OkHttpClient,
    ): ResultOrError? {
        val detected = detectLanguage(text, serverUrl, okHttpClientForUrl) ?: return null

        return when {
            detected == "und" -> null
            detected.equals(translateTo, ignoreCase = true) -> null
            dontTranslateFrom.any { it.equals(detected, ignoreCase = true) } -> null
            else -> translate(text, detected, translateTo, serverUrl, okHttpClientForUrl)
        }
    }

    private suspend fun detectLanguage(
        text: String,
        serverUrl: String,
        okHttpClientForUrl: (String) -> OkHttpClient,
    ): String? {
        val response =
            postJson(
                url = "$serverUrl/detect",
                body = json.encodeToString(DetectRequest(text)),
                okHttpClientForUrl = okHttpClientForUrl,
            )
        val detections = json.decodeFromString<List<DetectResponse>>(response)
        val best = detections.maxByOrNull { it.confidence } ?: return null
        return best.language.takeIf { it.isNotBlank() && best.confidence >= MIN_DETECTION_CONFIDENCE }
    }

    private suspend fun translate(
        text: String,
        source: String,
        target: String,
        serverUrl: String,
        okHttpClientForUrl: (String) -> OkHttpClient,
    ): ResultOrError {
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val response =
            postJson(
                url = "$serverUrl/translate",
                body = json.encodeToString(TranslateRequest(encoded, source, target)),
                okHttpClientForUrl = okHttpClientForUrl,
            )
        val translated = json.decodeFromString<TranslateResponse>(response).translatedText
        return ResultOrError(TranslationDictionary.decode(translated, dict), source, target)
    }

    private suspend fun postJson(
        url: String,
        body: String,
        okHttpClientForUrl: (String) -> OkHttpClient,
    ): String {
        checkNotInMainThread()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(body.toRequestBody(mediaType))
                .build()

        return okHttpClientForUrl(url).newCall(request).executeAsync().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("LibreTranslate request failed with HTTP ${response.code}")
            }
            responseBody
        }
    }

    private fun normalizeTranslatorUrl(url: String): String? {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isBlank()) return null
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) return null
        return normalized
    }

    @Serializable
    private data class DetectRequest(
        val q: String,
    )

    @Serializable
    private data class DetectResponse(
        val confidence: Double = 0.0,
        val language: String = "",
    )

    @Serializable
    private data class TranslateRequest(
        val q: String,
        val source: String,
        val target: String,
        val format: String = "text",
    )

    @Serializable
    private data class TranslateResponse(
        val translatedText: String = "",
    )
}
