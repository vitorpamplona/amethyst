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
import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

object LanguageTranslatorService {
    // Texts shorter than this, or with no letters at all (emoji-only, punctuation), are skipped
    // before any ML Kit work — language identification is unreliable on them anyway.
    private const val MIN_TRANSLATABLE_LENGTH = 4

    // Single Unicode Private Use Area codepoint per placeholder. PUA chars don't appear in normal
    // user text, the translator has no rule for them so it passes them through, and using one
    // codepoint (instead of bracketed digits) means the translator can't split or reorder the
    // placeholder. Range U+E000..U+F8FF gives 6400 slots, far more than any single note needs.
    private const val PLACEHOLDER_BASE = 0xE000
    private const val PLACEHOLDER_LIMIT = 0xF8FF - PLACEHOLDER_BASE

    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))

    private val identificationOptions =
        LanguageIdentificationOptions
            .Builder()
            .setExecutor(executorService)
            .setConfidenceThreshold(0.6f)
            .build()
    private val languageIdentification = LanguageIdentification.getClient(identificationOptions)

    val lnRegex: Pattern = Pattern.compile("\\blnbc[a-z0-9]+\\b", Pattern.CASE_INSENSITIVE)
    val tagRegex: Pattern =
        Pattern.compile(
            "(nostr:)?@?(nsec1|npub1|nevent1|naddr1|note1|nprofile1|nrelay1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)",
            Pattern.CASE_INSENSITIVE,
        )

    // Legacy NIP-08 positional references like #[0]. Translators tend to insert a space inside the
    // brackets ("# [0]"), so we shield them via the placeholder dictionary instead of post-fixing.
    val nip08RefRegex: Pattern = Pattern.compile("#\\[\\d+]")

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

            val dict = buildDictionary(text)
            val encoded = encodeWithDictionary(text, dict)

            translator.translate(encoded).continueWith(executorService) { task ->
                task.exception?.let { throw it }
                ResultOrError(decodeWithDictionary(task.result, dict), source, target)
            }
        }
    }

    fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ): Task<ResultOrError> {
        if (!isWorthTranslating(text)) return Tasks.forCanceled()

        val key = InFlightKey(text, translateTo, dontTranslateFrom)
        inFlight[key]?.let { return it }

        val task =
            identifyLanguage(text).onSuccessTask(executorService) { detected ->
                when {
                    detected == "und" -> Tasks.forCanceled()
                    detected.equals(translateTo, ignoreCase = true) -> Tasks.forCanceled()
                    detected in dontTranslateFrom -> Tasks.forCanceled()
                    else -> translate(text, detected, translateTo)
                }
            }

        // putIfAbsent guards against a racing caller: keep the winner, drop the loser.
        val winner = inFlight.putIfAbsent(key, task) ?: task
        winner.addOnCompleteListener(executorService) { inFlight.remove(key, winner) }
        return winner
    }

    private fun isWorthTranslating(text: String): Boolean {
        if (text.length < MIN_TRANSLATABLE_LENGTH) return false
        // Cheap scan; bail as soon as we see one letter codepoint.
        for (cp in text.codePoints()) {
            if (Character.isLetter(cp)) return true
        }
        return false
    }

    private fun buildDictionary(text: String): Map<String, String> {
        val dict = LinkedHashMap<String, String>()
        var counter = 0

        fun addUnique(value: String) {
            if (value.isEmpty()) return
            if (counter > PLACEHOLDER_LIMIT) return
            if (dict.containsValue(value)) return
            dict[placeholder(counter++)] = value
        }

        val lnMatcher = lnRegex.matcher(text)
        while (lnMatcher.find()) addUnique(lnMatcher.group())

        val tagMatcher = tagRegex.matcher(text)
        while (tagMatcher.find()) addUnique(tagMatcher.group())

        val nip08Matcher = nip08RefRegex.matcher(text)
        while (nip08Matcher.find()) addUnique(nip08Matcher.group())

        for (url in UrlDetector(text).detect()) {
            val original = url.originalUrl
            // The URL detector greedily includes Chinese full-width punctuation; skip those false hits.
            if (original.contains('，') || original.contains('。')) continue
            addUnique(original)
        }

        return dict
    }

    private fun placeholder(index: Int): String {
        require(index in 0..PLACEHOLDER_LIMIT) { "placeholder index $index out of range" }
        return String(Character.toChars(PLACEHOLDER_BASE + index))
    }

    private fun encodeWithDictionary(
        text: String,
        dict: Map<String, String>,
    ): String {
        if (dict.isEmpty()) return text
        var newText = text
        // Replace longest values first so a URL prefix never clobbers a longer URL or tag.
        for ((token, original) in dict.entries.sortedByDescending { it.value.length }) {
            newText = newText.replace(original, token, ignoreCase = false)
        }
        return newText
    }

    private fun decodeWithDictionary(
        text: String?,
        dict: Map<String, String>,
    ): String? {
        if (text == null || dict.isEmpty()) return text
        var newText: String = text
        for ((token, original) in dict) {
            newText = newText.replace(token, original, ignoreCase = false)
        }
        return newText
    }
}
