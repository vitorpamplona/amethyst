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
package com.vitorpamplona.amethyst.service.ai

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MLKitWritingAssistant(
    private val context: Context,
) : WritingAssistant {
    private var rewriters = mutableMapOf<Long, Rewriter>()
    private var proofreaders = mutableMapOf<Int, Proofreader>()

    private fun rewriterCacheKey(
        @RewriterOptions.OutputType outputType: Int,
        @RewriterOptions.Language language: Int,
    ): Long = (outputType.toLong() shl 32) or language.toLong()

    private fun getRewriter(
        @RewriterOptions.OutputType outputType: Int,
        @RewriterOptions.Language language: Int,
    ): Rewriter =
        rewriters.getOrPut(rewriterCacheKey(outputType, language)) {
            Rewriting.getClient(
                RewriterOptions
                    .builder(context)
                    .setOutputType(outputType)
                    .setLanguage(language)
                    .build(),
            )
        }

    private fun getProofreader(
        @ProofreaderOptions.Language language: Int,
    ): Proofreader =
        proofreaders.getOrPut(language) {
            Proofreading.getClient(
                ProofreaderOptions
                    .builder(context)
                    .setInputType(ProofreaderOptions.InputType.KEYBOARD)
                    .setLanguage(language)
                    .build(),
            )
        }

    override suspend fun checkAvailability(): WritingAssistantStatus =
        withContext(Dispatchers.IO) {
            try {
                val rewriter = getRewriter(RewriterOptions.OutputType.REPHRASE, RewriterOptions.Language.ENGLISH)
                val status = rewriter.checkFeatureStatus().get()
                when (status) {
                    FeatureStatus.AVAILABLE -> WritingAssistantStatus.Available
                    FeatureStatus.DOWNLOADING -> WritingAssistantStatus.Downloading
                    else -> WritingAssistantStatus.Unavailable
                }
            } catch (e: Exception) {
                WritingAssistantStatus.Unavailable
            }
        }

    override suspend fun transform(
        text: String,
        tone: WritingTone,
    ): WritingResult {
        val language = detectLanguage(text)
        val transformedText =
            when (tone) {
                WritingTone.CORRECT -> proofread(text, language)
                WritingTone.REPHRASE -> rewrite(text, RewriterOptions.OutputType.REPHRASE, language)
                WritingTone.SHORTER -> rewrite(text, RewriterOptions.OutputType.SHORTEN, language)
                WritingTone.ELABORATE -> rewrite(text, RewriterOptions.OutputType.ELABORATE, language)
                WritingTone.FRIENDLY -> rewrite(text, RewriterOptions.OutputType.FRIENDLY, language)
                WritingTone.PROFESSIONAL -> rewrite(text, RewriterOptions.OutputType.PROFESSIONAL, language)
                WritingTone.EMOJIFY -> rewrite(text, RewriterOptions.OutputType.EMOJIFY, language)
                WritingTone.MORE_DIRECT -> rewrite(text, RewriterOptions.OutputType.PROFESSIONAL, language)
                WritingTone.PUNCHY -> rewrite(text, RewriterOptions.OutputType.SHORTEN, language)
            }

        return WritingResult(
            originalText = text,
            transformedText = transformedText,
            tone = tone,
        )
    }

    private suspend fun detectLanguage(text: String): Int =
        withContext(Dispatchers.IO) {
            try {
                val langTag = Tasks.await(LanguageTranslatorService.identifyLanguage(text))
                mapLanguageTag(langTag)
            } catch (e: Exception) {
                RewriterOptions.Language.ENGLISH
            }
        }

    private suspend fun rewrite(
        text: String,
        @RewriterOptions.OutputType outputType: Int,
        @RewriterOptions.Language language: Int,
    ): String =
        withContext(Dispatchers.IO) {
            val rewriter = getRewriter(outputType, language)
            val request = RewritingRequest.builder(text).build()
            val result = rewriter.runInference(request).get()
            result.results.firstOrNull()?.text ?: text
        }

    private suspend fun proofread(
        text: String,
        @ProofreaderOptions.Language language: Int,
    ): String =
        withContext(Dispatchers.IO) {
            val proofreader = getProofreader(language)
            val request = ProofreadingRequest.builder(text).build()
            val result = proofreader.runInference(request).get()
            result.results.firstOrNull()?.text ?: text
        }

    override fun close() {
        rewriters.values.forEach { it.close() }
        rewriters.clear()
        proofreaders.values.forEach { it.close() }
        proofreaders.clear()
    }

    companion object {
        fun mapLanguageTag(tag: String?): Int =
            when (tag?.lowercase()?.take(2)) {
                "en" -> RewriterOptions.Language.ENGLISH
                "ja" -> RewriterOptions.Language.JAPANESE
                "ko" -> RewriterOptions.Language.KOREAN
                "de" -> RewriterOptions.Language.GERMAN
                "fr" -> RewriterOptions.Language.FRENCH
                "it" -> RewriterOptions.Language.ITALIAN
                "es" -> RewriterOptions.Language.SPANISH
                else -> RewriterOptions.Language.ENGLISH
            }
    }
}
