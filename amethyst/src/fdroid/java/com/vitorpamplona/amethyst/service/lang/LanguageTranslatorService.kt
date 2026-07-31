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

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

/**
 * F-Droid build: translation backed by a user-configured LibreTranslate
 * server (see [TranslationServerConfig]). Mirrors the API surface of the
 * Play build's ML Kit service so callers behave the same way in both
 * flavors. Off by default; nothing is sent anywhere until the user enables
 * it and their language settings ask for a translation.
 */
object LanguageTranslatorService {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    // LibreTranslate language set (ISO 639-1). Codes outside it are skipped.
    private val supportedLanguages =
        setOf(
            "en", "ar", "az", "bg", "bs", "ca", "cs", "da", "de", "el", "es", "et", "fa", "fi",
            "fr", "he", "hi", "hr", "hu", "hy", "id", "it", "ja", "ka", "kk", "ko", "lt", "lv",
            "mk", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sr", "sv",
            "th", "tr", "uk", "ur", "vi", "zh",
        )

    fun clear() {
        TranslationsCache.clear()
    }

    /** Maps a BCP-47 tag (e.g. "zh-CN") to the closest LibreTranslate code, or null. */
    fun toLibreTranslateCode(tag: String): String? {
        val code = tag.substringBefore('-').lowercase()
        return code.takeIf { it in supportedLanguages }
    }

    suspend fun identifyLanguage(context: Context, text: String): String? {
        val body = FormBody.Builder().add("q", text).build()
        val response = execute(apiRequest(context, "/detect", body))
        val json = JSONArray(response)
        if (json.length() == 0) return null
        return json.getJSONObject(0).optString("language").takeIf { it.isNotBlank() }?.lowercase()
    }

    suspend fun translate(
        context: Context,
        text: String,
        source: String,
        target: String,
    ): ResultOrError {
        val sourceCode = toLibreTranslateCode(source)
        val targetCode = toLibreTranslateCode(target)
        if (sourceCode == null || targetCode == null) return ResultOrError(null, null, null)

        val form =
            FormBody.Builder()
                .add("q", text)
                .add("source", sourceCode)
                .add("target", targetCode)
                .add("format", "text")
        TranslationServerConfig.apiKey(context).takeIf { it.isNotBlank() }?.let { form.add("api_key", it) }

        val response = execute(apiRequest(context, "/translate", form.build()))
        val translated = JSONObject(response).optString("translatedText")
        return ResultOrError(translated.ifBlank { null }, source, target)
    }

    suspend fun autoTranslate(
        context: Context,
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ): ResultOrError {
        if (!TranslationServerConfig.isEnabled(context)) return ResultOrError(text, null, null)
        if (!isWorthTranslating(text)) return ResultOrError(text, null, null)

        val detected = identifyLanguage(context, text) ?: return ResultOrError(text, null, null)
        return when {
            detected == "und" -> ResultOrError(text, null, null)
            detected.equals(translateTo, ignoreCase = true) -> ResultOrError(text, null, null)
            detected in dontTranslateFrom -> ResultOrError(text, null, null)
            else -> translate(context, text, detected, translateTo)
        }
    }

    private fun isWorthTranslating(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length >= 2 && trimmed.any { it.isLetter() }
    }

    private fun apiRequest(context: Context, path: String, body: FormBody): Request =
        Request
            .Builder()
            .url("${TranslationServerConfig.serverUrl(context)}$path")
            .post(body)
            .build()

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!cont.isCancelled) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (cont.isCancelled) return
                            if (it.isSuccessful) {
                                cont.resume(it.body?.string() ?: "")
                            } else {
                                cont.resumeWithException(IOException("HTTP ${it.code}: ${it.body?.string()?.take(200)}"))
                            }
                        }
                    }
                },
            )
        }
}
