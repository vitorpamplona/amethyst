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

import kotlinx.coroutines.delay

/**
 * TODO: Remove before shipping. Debug-only mock for testing the AI writing help UI
 * on devices without Gemini Nano / AICore support.
 */
class MockWritingAssistant : WritingAssistant {
    override suspend fun checkAvailability(): WritingAssistantStatus = WritingAssistantStatus.Available

    override suspend fun transform(
        text: String,
        tone: WritingTone,
    ): WritingResult {
        delay(800)

        val transformed =
            when (tone) {
                WritingTone.CORRECT -> {
                    correctMock(text)
                }

                WritingTone.REPHRASE -> {
                    "Here's another way to put it: $text"
                }

                WritingTone.SHORTER -> {
                    text
                        .split(".")
                        .firstOrNull()
                        ?.trim()
                        ?.plus(".") ?: text
                }

                WritingTone.ELABORATE -> {
                    "$text Furthermore, this point deserves deeper consideration and nuance."
                }

                WritingTone.FRIENDLY -> {
                    "Hey! $text Hope that makes sense! :)"
                }

                WritingTone.PROFESSIONAL -> {
                    "I would like to bring to your attention the following: $text"
                }

                WritingTone.MORE_DIRECT -> {
                    text.replace("I think ", "").replace("maybe ", "").replace("perhaps ", "")
                }

                WritingTone.PUNCHY -> {
                    text.uppercase().replace(".", "!")
                }

                WritingTone.EMOJIFY -> {
                    "$text \uD83D\uDE80\uD83D\uDD25\u2728"
                }
            }

        return WritingResult(
            originalText = text,
            transformedText = transformed,
            tone = tone,
        )
    }

    private fun correctMock(text: String): String =
        text
            .replace("teh ", "the ")
            .replace("dont ", "don't ")
            .replace("cant ", "can't ")
            .replace("wont ", "won't ")
            .replace("i ", "I ")

    override fun close() {
        // no-op: mock holds no native handles or background workers to release.
    }
}
