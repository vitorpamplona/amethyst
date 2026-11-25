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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.agents.UserQuestion
import com.vitorpamplona.agents.UserQuestions
import com.vitorpamplona.claudecode.protocol.ClaudeQuestions
import com.vitorpamplona.codex.CodexQuestions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A question call in the chat, read back: what the agent asked, and what the user answered. */
object AskedQuestions {
    /**
     * Questions already read, by call: a row scrolled back into view would otherwise parse its
     * input again on the main thread. Least recently used first.
     */
    private val cache = LinkedHashMap<String, UserQuestions?>(16, 0.75f, true)

    /** The questions in a call to an agent's question tool, or null for any other call. */
    fun questions(
        toolName: String,
        input: String,
    ): UserQuestions? {
        if (toolName != ClaudeQuestions.TOOL && toolName != CodexQuestions.TOOL) return null
        val key = toolName + "\u0000" + input
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val parsed = parse(toolName, input)
        synchronized(cache) {
            cache[key] = parsed
            if (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
        }
        return parsed
    }

    private fun parse(
        toolName: String,
        input: String,
    ): UserQuestions? {
        val obj =
            try {
                Json.parseToJsonElement(input) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return null
        return when (toolName) {
            ClaudeQuestions.TOOL -> ClaudeQuestions.parse(obj)
            CodexQuestions.TOOL -> CodexQuestions.parse(obj)
            else -> null
        }
    }

    /**
     * The answer to each of [questions] in the call's [result], by [UserQuestion.id], as Claude
     * Code words it (`… "Which database?"="Postgres", "Which platforms?"="Android, Desktop" …`),
     * which Codex's row copies. A question the result doesn't answer is left out.
     */
    fun answers(
        result: String,
        questions: List<UserQuestion>,
    ): Map<String, String> {
        // Where each question's answer starts, in the order they appear.
        val starts =
            questions
                .mapNotNull { question ->
                    val marker = "\"${question.question}\"="
                    val at = result.indexOf(marker)
                    if (at < 0) null else Triple(question.id, at, at + marker.length)
                }.sortedBy { it.second }
        return buildMap {
            starts.forEachIndexed { i, (id, _, from) ->
                val until = starts.getOrNull(i + 1)?.second ?: result.length
                val segment = result.substring(from, until)
                if (!segment.startsWith("\"")) return@forEachIndexed
                // The answer is quoted; what follows it is a picked preview, notes, or the end of the line.
                val end =
                    listOf("\" selected preview:", "\" notes: ")
                        .map { segment.indexOf(it) }
                        .filter { it > 0 }
                        .minOrNull() ?: segment.lastIndexOf('"')
                if (end > 0) put(id, segment.substring(1, end))
            }
        }
    }

    private const val CACHE_SIZE = 64
}
