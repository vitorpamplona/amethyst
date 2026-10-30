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
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.Volatile

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

/**
 * The translation needs a model that isn't on the device, and the download settings don't allow
 * fetching it now (e.g. "Wi-Fi only" on mobile data). Not a "no translation needed" result, so
 * callers must not cache it: the same text translates once the download is allowed.
 */
class TranslationModelNotDownloadedException : Exception("Translation model not downloaded and downloads are not allowed now")

object LanguageTranslatorService {
    private const val TAG = "LanguageTranslatorService"

    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))

    private val identificationOptions =
        LanguageIdentificationOptions
            .Builder()
            .setExecutor(executorService)
            .setConfidenceThreshold(0.6f)
            .build()
    private val languageIdentification = LanguageIdentification.getClient(identificationOptions)

    private val modelManager by lazy { RemoteModelManager.getInstance() }

    // Translator codes, as TranslationLanguages.toTranslatorCode returns them. Keyed by the pair
    // rather than TranslatorOptions so the translators that use a model can be found and closed
    // before the model is deleted.
    private data class LanguagePair(
        val source: String,
        val target: String,
    )

    private val translators =
        object : LruCache<LanguagePair, Translator>(3) {
            override fun create(pair: LanguagePair): Translator =
                Translation.getClient(
                    TranslatorOptions
                        .Builder()
                        .setExecutor(executorService)
                        .setSourceLanguage(pair.source)
                        .setTargetLanguage(pair.target)
                        .build(),
                )

            override fun entryRemoved(
                evicted: Boolean,
                key: LanguagePair,
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
        val allowModelDownload: Boolean,
    )

    // Coalesces concurrent translation requests for the same (content, settings) — the same note
    // shown in N composables (reposts, notifications) only fires one ML Kit pipeline.
    private val inFlight = ConcurrentHashMap<InFlightKey, Task<ResultOrError>>()

    /**
     * The language settings of the last request, and the translator codes whose models are not
     * wanted under them: the "Don't translate from" languages, except the target and English
     * (built in, can't be deleted).
     */
    private class LanguageSettings(
        val dontTranslateFrom: Set<String>,
        val translateTo: String,
    ) {
        val unwantedModels: Set<String> =
            TranslationLanguages.toTranslatorCodes(dontTranslateFrom) -
                setOfNotNull(TranslationLanguages.toTranslatorCode(translateTo), TranslateLanguage.ENGLISH)

        fun isSame(
            dontTranslateFrom: Set<String>,
            translateTo: String,
        ) = this.translateTo == translateTo && this.dontTranslateFrom == dontTranslateFrom
    }

    @Volatile
    private var languageSettings: LanguageSettings? = null

    fun clear() {
        translators.evictAll()
        inFlight.clear()
        TranslationsCache.clear()
    }

    fun identifyLanguage(text: String): Task<String> = languageIdentification.identifyLanguage(text)

    /**
     * Translates [text] from [source] to [target]. When [allowModelDownload] is false, only
     * translates if both models are already on the device, and fails with
     * [TranslationModelNotDownloadedException] otherwise.
     */
    fun translate(
        text: String,
        source: String,
        target: String,
        allowModelDownload: Boolean,
    ): Task<ResultOrError> {
        val sourceLangCode = TranslationLanguages.toTranslatorCode(source)
        val targetLangCode = TranslationLanguages.toTranslatorCode(target)

        if (sourceLangCode == null || targetLangCode == null || sourceLangCode == targetLangCode) {
            return Tasks.forCanceled()
        }

        val pair = LanguagePair(sourceLangCode, targetLangCode)
        val modelsReady = if (allowModelDownload) Tasks.forResult(true) else areModelsDownloaded(pair)

        return modelsReady.onSuccessTask(executorService) { ready ->
            if (ready != true) throw TranslationModelNotDownloadedException()

            val translator = translators[pair]

            translator.downloadModelIfNeeded().onSuccessTask(executorService) {
                // The user may have blocked the language while its model was downloading, and an
                // ML Kit download can't be cancelled: drop the model it just stored.
                if (languageSettings?.unwantedModels?.contains(sourceLangCode) == true) {
                    deleteModel(sourceLangCode)
                    return@onSuccessTask Tasks.forCanceled<ResultOrError>()
                }

                val dict = TranslationDictionary.build(text)
                val encoded = TranslationDictionary.encode(text, dict)

                translator.translate(encoded).continueWith(executorService) { task ->
                    task.exception?.let { throw it }
                    ResultOrError(TranslationDictionary.decode(task.result, dict), source, target)
                }
            }
        }
    }

    fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
        allowModelDownload: Boolean,
    ): Task<ResultOrError> {
        updateLanguageSettings(dontTranslateFrom, translateTo)
        if (!TranslationDictionary.isWorthTranslating(text)) return Tasks.forCanceled()
        return dedupe(InFlightKey(text, translateTo, dontTranslateFrom, allowModelDownload)) {
            identifyLanguage(text).onSuccessTask(executorService) { detected ->
                if (TranslationLanguages.shouldTranslate(detected, dontTranslateFrom, translateTo)) {
                    translate(text, detected, translateTo, allowModelDownload)
                } else {
                    Tasks.forCanceled()
                }
            }
        }
    }

    private fun areModelsDownloaded(pair: LanguagePair): Task<Boolean> {
        val source = isModelDownloaded(pair.source)
        val target = isModelDownloaded(pair.target)
        return Tasks.whenAll(source, target).continueWith(executorService) {
            source.isSuccessful && source.result == true && target.isSuccessful && target.result == true
        }
    }

    private fun isModelDownloaded(code: String): Task<Boolean> =
        if (code == TranslateLanguage.ENGLISH) {
            Tasks.forResult(true)
        } else {
            modelManager.isModelDownloaded(TranslateRemoteModel.Builder(code).build())
        }

    /**
     * Records the settings of this request and, when they changed, deletes the models of the
     * languages they block. That also removes models stored before the alias fix, when a blocked
     * language spelled differently (`he`/`iw`, `nb`/`no`) still reached the download.
     */
    private fun updateLanguageSettings(
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ) {
        if (languageSettings?.isSame(dontTranslateFrom, translateTo) == true) return

        val settings = LanguageSettings(dontTranslateFrom, translateTo)
        languageSettings = settings
        if (settings.unwantedModels.isEmpty()) return

        modelManager
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener(executorService) { models ->
                // Settings changed again while listing: the newer call does its own pass.
                if (languageSettings !== settings) return@addOnSuccessListener
                models.forEach { model ->
                    if (model.language in settings.unwantedModels) deleteModel(model.language)
                }
            }.addOnFailureListener(executorService) { e ->
                Log.w(TAG, "Could not list the downloaded translation models", e)
            }
    }

    private fun deleteModel(code: String) {
        // Close the translators that hold the model before deleting its files.
        translators.snapshot().keys.forEach { pair ->
            if (pair.source == code || pair.target == code) translators.remove(pair)
        }

        modelManager
            .deleteDownloadedModel(TranslateRemoteModel.Builder(code).build())
            .addOnSuccessListener(executorService) { Log.d(TAG) { "Deleted the translation model of blocked language $code" } }
            .addOnFailureListener(executorService) { e -> Log.w(TAG, "Could not delete the translation model of $code", e) }
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
