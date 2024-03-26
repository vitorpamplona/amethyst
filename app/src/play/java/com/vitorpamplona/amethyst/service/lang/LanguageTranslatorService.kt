/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlinx.coroutines.CancellationException
import java.util.concurrent.Executors
import java.util.regex.Pattern

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

object LanguageTranslatorService {
    var executorService = Executors.newCachedThreadPool()

    private val options =
        LanguageIdentificationOptions.Builder()
            .setExecutor(executorService)
            .setConfidenceThreshold(0.6f)
            .build()
    private val languageIdentification = LanguageIdentification.getClient(options)
    val lnRegex = Pattern.compile("\\blnbc[a-z0-9]+\\b", Pattern.CASE_INSENSITIVE)
    val tagRegex =
        Pattern.compile(
            "(nostr:)?@?(nsec1|npub1|nevent1|naddr1|note1|nprofile1|nrelay1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)",
            Pattern.CASE_INSENSITIVE,
        )

    private val translators =
        object : LruCache<TranslatorOptions, Translator>(3) {
            override fun create(options: TranslatorOptions): Translator {
                return Translation.getClient(options)
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: TranslatorOptions,
                oldValue: Translator,
                newValue: Translator?,
            ) {
                oldValue.close()
            }
        }

    fun clear() {
        translators.evictAll()
    }

    fun identifyLanguage(text: String): Task<String> {
        return languageIdentification.identifyLanguage(text)
    }

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
            TranslatorOptions.Builder()
                .setExecutor(executorService)
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(targetLangCode)
                .build()

        val translator = translators[options]

        return translator.downloadModelIfNeeded().onSuccessTask(executorService) {
            checkNotInMainThread()

            val tasks = mutableListOf<Task<String>>()
            val dict = lnDictionary(text) + urlDictionary(text) + tagDictionary(text)

            for (paragraph in encodeDictionary(text, dict).split("\n")) {
                tasks.add(translator.translate(paragraph))
            }

            Tasks.whenAll(tasks).continueWith(executorService) {
                checkNotInMainThread()

                val results: MutableList<String> = ArrayList()
                for (task in tasks) {
                    val fixedText =
                        task.result.replace("# [", "#[") // fixes tags that always return with a space
                    results.add(decodeDictionary(fixedText, dict))
                }
                ResultOrError(results.joinToString("\n"), source, target)
            }
        }
    }

    private fun encodeDictionary(
        text: String,
        dict: Map<String, String>,
    ): String {
        var newText = text
        for (pair in dict) {
            newText = newText.replace(pair.value, pair.key, true)
        }
        return newText
    }

    private fun decodeDictionary(
        text: String,
        dict: Map<String, String>,
    ): String {
        var newText = text
        for (pair in dict) {
            newText = newText.replace(pair.key, pair.value, true)
        }
        return newText
    }

    private fun tagDictionary(text: String): Map<String, String> {
        val matcher = tagRegex.matcher(text)
        val returningList = mutableMapOf<String, String>()
        var counter = 0
        while (matcher.find()) {
            try {
                val tag = matcher.group()
                val short = "A$counter"
                counter++
                returningList.put(short, tag)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        return returningList
    }

    private fun lnDictionary(text: String): Map<String, String> {
        val matcher = lnRegex.matcher(text)
        val returningList = mutableMapOf<String, String>()
        var counter = 0
        while (matcher.find()) {
            try {
                val lnInvoice = matcher.group()
                val short = "A$counter"
                counter++
                returningList.put(short, lnInvoice)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        return returningList
    }

    private fun urlDictionary(text: String): Map<String, String> {
        val parser = UrlDetector(text, UrlDetectorOptions.Default)
        val urlsInText = parser.detect()

        var counter = 0

        return urlsInText
            .filter { !it.originalUrl.contains("，") && !it.originalUrl.contains("。") }
            .associate {
                counter++
                "A$counter" to it.originalUrl
            }
    }

    fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ): Task<ResultOrError> {
        return identifyLanguage(text).onSuccessTask(executorService) {
            if (it.equals(translateTo, true)) {
                Tasks.forCanceled()
            } else if (it != "und" && !dontTranslateFrom.contains(it)) {
                translate(text, it, translateTo)
            } else {
                Tasks.forCanceled()
            }
        }
    }
}
