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
package com.vitorpamplona.amethyst.ios.translation

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.NaturalLanguage.NLLanguageRecognizer

/**
 * Result of language detection on note content.
 *
 * @property languageCode BCP-47 language code (e.g. "en", "ja", "pt")
 * @property confidence 0.0 – 1.0
 * @property isForeign true when the detected language differs from the device locale
 */
data class DetectedLanguage(
    val languageCode: String,
    val confidence: Double,
    val isForeign: Boolean,
)

/**
 * Thin wrapper around Apple's NLLanguageRecognizer to detect whether note
 * content is in a foreign language.
 */
object LanguageDetector {
    /** Minimum confidence before we claim a detection. */
    private const val MIN_CONFIDENCE = 0.5

    /** Minimum text length worth analyzing. */
    private const val MIN_LENGTH = 12

    private val deviceLanguage: String by lazy {
        NSLocale.currentLocale.languageCode
    }

    /**
     * Detect the dominant language of [text].
     * Returns `null` when the text is too short or confidence is below threshold.
     */
    fun detect(text: String): DetectedLanguage? {
        if (text.length < MIN_LENGTH) return null

        val recognizer = NLLanguageRecognizer()
        recognizer.processString(text)
        val language = recognizer.dominantLanguage ?: return null

        // Get top hypothesis confidence
        val hypotheses = recognizer.languageHypothesesWithMaximum(1u)
        val confidence = hypotheses[language] as? Double ?: 0.0
        if (confidence < MIN_CONFIDENCE) return null

        val code = language
        val isForeign = !code.startsWith(deviceLanguage)

        return DetectedLanguage(
            languageCode = code,
            confidence = confidence,
            isForeign = isForeign,
        )
    }
}
