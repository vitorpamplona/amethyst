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
package com.vitorpamplona.amethyst.commons.frontpage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WriterTest {
    @Test
    fun readsStreamedChunksUsageAndTheEnd() {
        val chunk = parseStreamLine("""data: {"choices":[{"delta":{"content":"<html>"}}]}""")
        assertEquals("<html>", chunk.text)
        val usage = parseStreamLine("""data: {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5}}""").usage
        assertEquals(15L, usage?.total)
        assertTrue(parseStreamLine("data: [DONE]").done)
        assertEquals("", parseStreamLine(": keep-alive").text)
        assertEquals("", parseStreamLine("data: {broken").text)
    }

    @Test
    fun readsANonStreamedAnswer() {
        val written = readCompletion("""{"choices":[{"message":{"content":"<p>x</p>"}}],"usage":{"prompt_tokens":3}}""")
        assertEquals("<p>x</p>", written.text)
        assertEquals(3L, written.usage?.promptTokens)
    }

    @Test
    fun extractsTheDocumentFromAWrappedAnswer() {
        assertEquals("<!doctype html><html><body>x</body></html>", extractDocument("```html\n<!doctype html><html><body>x</body></html>\n```"))
        assertEquals("<html><body>x</body></html>", extractDocument("Here is your paper:\n<html><body>x</body></html>\nEnjoy!"))
    }

    @Test
    fun ranksLongFormWritersFirst() {
        val ranked = rankModels(listOf("text-embedding-3-large", "gpt-4o-mini", "anthropic/claude-haiku-4.5", "anthropic/claude-sonnet-4.5", "openai/gpt-5", "google/gemini-2.5-pro"))
        assertEquals("anthropic/claude-sonnet-4.5", ranked.first())
        assertTrue("text-embedding-3-large" !in ranked)
        assertTrue(ranked.indexOf("openai/gpt-5") < ranked.indexOf("gpt-4o-mini"))
    }

    @Test
    fun readsBothModelListShapes() {
        assertEquals(listOf("a", "b"), readModelIds("""{"data":[{"id":"a"},{"id":"b"}]}"""))
        assertEquals(listOf("gemini-2.5-pro"), readModelIds("""{"models":[{"name":"models/gemini-2.5-pro"}]}"""))
        assertEquals(emptyList(), readModelIds("<html>"))
    }

    @Test
    fun anthropicGetsRoomForAWholePage() {
        assertTrue(chatRequestBody("https://api.anthropic.com/v1", "m", emptyList(), true).contains("\"max_tokens\":32000"))
        assertTrue(!chatRequestBody("https://openrouter.ai/api/v1", "m", emptyList(), true).contains("max_tokens"))
        assertTrue(chatRequestBody("https://openrouter.ai/api/v1", "m", emptyList(), true).contains("\"include_usage\":true"))
    }

    @Test
    fun theDigestGoesInsideTheCorpusBlockAndTheBriefHasTheStylesheet() {
        val message = userMessage("DIGEST", "Alice")
        assertTrue(message.indexOf("<corpus>") < message.indexOf("DIGEST"))
        assertTrue(message.contains("Reader: Alice"))
        assertTrue(systemPrompt().contains("color-scheme: light"))
        assertTrue(repairMessage(listOf(Violation(Violation.Kind.QUOTE, "not found", "x"))).contains("- QUOTE: not found"))
    }
}
