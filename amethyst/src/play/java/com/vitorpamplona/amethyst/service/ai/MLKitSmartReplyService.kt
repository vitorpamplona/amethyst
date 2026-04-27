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

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MLKitSmartReplyService {
    private val genAiClient: GenerativeModel by lazy { Generation.getClient() }
    private val legacyClient = SmartReply.getClient()

    @Volatile private var genAiAvailable: Boolean? = null

    suspend fun suggestReplies(history: List<SmartReplyMessage>): List<String> {
        if (history.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            generateWithGenAi(history) ?: generateWithLegacy(history)
        }
    }

    /**
     * Returns null when genai-prompt is unavailable on this device (older hardware,
     * model not downloaded, etc.) so the caller can fall back to legacy smart-reply.
     * Returns an empty list only when genai ran but produced nothing usable — in that
     * case the legacy fallback would not do better, so don't try it.
     */
    private suspend fun generateWithGenAi(history: List<SmartReplyMessage>): List<String>? {
        if (genAiAvailable == false) return null
        return try {
            if (genAiAvailable == null) {
                val status = genAiClient.checkStatus()
                genAiAvailable = (status == FeatureStatus.AVAILABLE)
                if (status == FeatureStatus.DOWNLOADING) return null
                if (status != FeatureStatus.AVAILABLE) return null
            }

            val request =
                GenerateContentRequest
                    .Builder(TextPart(buildConversationPrompt(history)))
                    .apply {
                        promptPrefix = PromptPrefix(SYSTEM_PROMPT)
                        temperature = 0.7f
                        candidateCount = 1
                        maxOutputTokens = 80
                    }.build()

            val response = genAiClient.generateContent(request)
            val raw = response.candidates.firstOrNull()?.text ?: return emptyList()
            parseSuggestions(raw)
        } catch (e: Exception) {
            genAiAvailable = false
            null
        }
    }

    private suspend fun generateWithLegacy(history: List<SmartReplyMessage>): List<String> {
        val conversation =
            history.map { msg ->
                if (msg.isLocalUser) {
                    TextMessage.createForLocalUser(msg.text, msg.timestampMs)
                } else {
                    TextMessage.createForRemoteUser(msg.text, msg.timestampMs, msg.remoteUserId)
                }
            }

        return try {
            val result = Tasks.await(legacyClient.suggestReplies(conversation))
            when (result.status) {
                SmartReplySuggestionResult.STATUS_SUCCESS -> {
                    result.suggestions
                        .map { it.text }
                        .distinct()
                        .take(3)
                }

                SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE -> {
                    emptyList()
                }

                SmartReplySuggestionResult.STATUS_NO_REPLY -> {
                    emptyList()
                }

                else -> {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun close() {
        runCatching { genAiClient.close() }
        legacyClient.close()
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You suggest 3 short, casual reply messages for a 1:1 chat.\n" +
                "Rules:\n" +
                "- Each suggestion under 40 characters.\n" +
                "- Plain text only. No quotes, no numbering, no commentary.\n" +
                "- Output exactly 3 suggestions, one per line.\n" +
                "\n" +
                "Example:\n" +
                "Conversation:\n" +
                "Them: dinner tonight?\n" +
                "Suggestions:\n" +
                "sure!\n" +
                "what time?\n" +
                "not tonight, sorry\n" +
                "\n" +
                "Example:\n" +
                "Conversation:\n" +
                "Them: i got the job!!\n" +
                "Me: oh wow\n" +
                "Them: starts monday\n" +
                "Suggestions:\n" +
                "congrats!!\n" +
                "amazing news\n" +
                "let's celebrate\n"

        private fun buildConversationPrompt(history: List<SmartReplyMessage>): String {
            val transcript =
                history.joinToString(separator = "\n") { msg ->
                    val who = if (msg.isLocalUser) "Me" else "Them"
                    "$who: ${msg.text.replace('\n', ' ').take(200)}"
                }
            return "Conversation:\n$transcript\nSuggestions:\n"
        }

        private val SUGGESTION_LINE_PREFIX = Regex("""^\s*(?:[-*•]|\d+[.):])\s*""")

        internal fun parseSuggestions(raw: String): List<String> =
            raw
                .lineSequence()
                .map {
                    it
                        .trim()
                        .removePrefix("\"")
                        .removeSuffix("\"")
                        .trim()
                }.map { it.replace(SUGGESTION_LINE_PREFIX, "") }
                .filter { it.isNotEmpty() }
                .filterNot { it.equals("Suggestions:", ignoreCase = true) }
                .filterNot { it.startsWith("Conversation:", ignoreCase = true) }
                .map { it.take(120) }
                .distinct()
                .take(3)
                .toList()
    }
}
