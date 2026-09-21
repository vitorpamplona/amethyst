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

import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

object LanguageTranslatorService {
    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))

    private val identificationOptions =
        LanguageIdentificationOptions
            .Builder()
            .setExecutor(executorService)
            .setConfidenceThreshold(0.6f)
            .build()
    private val languageIdentification = LanguageIdentification.getClient(identificationOptions)

    private val translators =
        object : LruCache<TranslatorOptions, Translator>(3) {
            override fun create(options: TranslatorOptions): Translator = Translation.getClient(options)

            override fun entryRemoved(
                evicted: Boolean,
                key: TranslatorOptions,
                oldValue: Translator,
                newValue: Translator?,
            ) {
                oldValue.close()
            }
        }

    private data class InFlightKey(
        val text: String,
        val translateTo: String,
        val dontTranslateFrom: Set<String>,
    )

    // Coalesces concurrent translation requests for the same (content, settings) — the same note
    // shown in N composables (reposts, notifications) only fires one ML Kit pipeline.
    private val inFlight = ConcurrentHashMap<InFlightKey, Task<ResultOrError>>()

    fun clear() {
        translators.evictAll()
        inFlight.clear()
        TranslationsCache.clear()
    }

    fun identifyLanguage(text: String): Task<String> = languageIdentification.identifyLanguage(text)

    fun translate(
        text: String,
        source: String,
        target: String,
    ): Task<ResultOrError> {
        checkNotInMainThread()
        val sourceLangCode = TranslateLanguage.fromLanguageTag(source)
        val targetLangCode = TranslateLanguage.fromLanguageTag(target)

        if (sourceLangCode == null || targetLangCode == null) {
            return Tasks.forCanceled()
        }

        val options =
            TranslatorOptions
                .Builder()
                .setExecutor(executorService)
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(targetLangCode)
                .build()

        val translator = translators[options]

        return translator.downloadModelIfNeeded().onSuccessTask(executorService) {
            checkNotInMainThread()

            val dict = TranslationDictionary.build(text)
            val encoded = TranslationDictionary.encode(text, dict)

            translator.translate(encoded).continueWith(executorService) { task ->
                task.exception?.let { throw it }
                ResultOrError(TranslationDictionary.decode(task.result, dict), source, target)
            }
        }
    }

    fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ): Task<ResultOrError> {
        if (!TranslationDictionary.isWorthTranslating(text)) return Tasks.forCanceled()
        return dedupe(InFlightKey(text, translateTo, dontTranslateFrom)) {
            identifyLanguage(text).onSuccessTask(executorService) { detected ->
                translateOrSkip(text, detected, dontTranslateFrom, translateTo)
            }
        }
    }

    private fun translateOrSkip(
        text: String,
        detected: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ): Task<ResultOrError> =
        when {
            detected == "und" -> Tasks.forCanceled()
            detected.equals(translateTo, ignoreCase = true) -> Tasks.forCanceled()
            detected in dontTranslateFrom -> Tasks.forCanceled()
            else -> translate(text, detected, translateTo)
        }

    private inline fun dedupe(
        key: InFlightKey,
        factory: () -> Task<ResultOrError>,
    ): Task<ResultOrError> {
        inFlight[key]?.let { return it }
        val candidate = factory()
        // putIfAbsent guards against a racing caller: keep the winner, drop the loser.
        val winner = inFlight.putIfAbsent(key, candidate) ?: candidate
        winner.addOnCompleteListener(executorService) { inFlight.remove(key, winner) }
        return winner
    }
}
