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
package com.vitorpamplona.codex

import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.QuestionAnswer
import com.vitorpamplona.agents.UserQuestion
import com.vitorpamplona.agents.UserQuestions
import com.vitorpamplona.codex.CodexItems.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Codex's `request_user_input` (`item/tool/requestUserInput`, experimental): questions with an
 * `id`, `header`, `question`, `options` (`label`, `description`; null for a typed answer),
 * `isOther` (a "None of the above" choice) and `isSecret`. The answer maps each id to a list of
 * strings: the label picked, then the user's typed text as `user_note: …`, as Codex's own TUI
 * sends it.
 */
object CodexQuestions {
    const val METHOD = "item/tool/requestUserInput"

    /** The tool's name in the chat. */
    const val TOOL = "request_user_input"

    /** The choice Codex's TUI adds for `isOther`, which its model knows by this label. */
    const val OTHER_LABEL = "None of the above"

    private const val NOTE_PREFIX = "user_note: "

    fun parse(params: JsonObject): UserQuestions? {
        val questions =
            (params["questions"] as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.text("id") ?: return@mapNotNull null
                val options =
                    (obj["options"] as? JsonArray)?.mapNotNull { option ->
                        val o = option as? JsonObject ?: return@mapNotNull null
                        UserQuestion.Option(o.text("label") ?: return@mapNotNull null, o.text("description")?.takeIf { it.isNotBlank() })
                    }
                val choice = !options.isNullOrEmpty()
                UserQuestion(
                    id = id,
                    question = obj.text("question") ?: obj.text("header") ?: id,
                    header = obj.text("header")?.takeIf { it.isNotBlank() },
                    kind = if (choice) UserQuestion.Kind.CHOICE else UserQuestion.Kind.TEXT,
                    options = options.orEmpty(),
                    allowsOther = !choice || obj.flag("isOther"),
                    secret = obj.flag("isSecret"),
                )
            }
        return if (questions.isEmpty()) null else UserQuestions(questions)
    }

    fun request(
        key: String,
        itemId: String?,
        params: JsonObject,
        questions: UserQuestions,
    ) = PermissionRequest(
        requestId = key,
        toolName = TOOL,
        displayName = null,
        description = null,
        title = null,
        input = params,
        toolUseId = itemId,
        decisionReason = null,
        blockedPath = null,
        suggestions = JsonArray(emptyList()),
        suppressAlwaysAllow = true,
        defaultToNo = false,
        agentId = null,
        questions = questions,
    )

    /**
     * Codex's answer: each question's picked label and the user's text as a note. A message the
     * user wrote instead of answering ([PermissionDecision.Answer.reply]) goes to every question
     * left unanswered, since Codex has no place for it of its own. Null [answer]: none at all.
     */
    fun response(
        questions: UserQuestions,
        answer: PermissionDecision.Answer?,
    ): JsonObject =
        buildJsonObject {
            put(
                "answers",
                buildJsonObject {
                    questions.questions.forEach { question ->
                        val given = answer?.answers?.get(question.id) ?: QuestionAnswer()
                        val values = values(question, given, answer?.reply?.trim()?.takeIf { it.isNotEmpty() })
                        put(question.id, buildJsonObject { put("answers", JsonArray(values.map(::JsonPrimitive))) })
                    }
                },
            )
        }

    private fun values(
        question: UserQuestion,
        given: QuestionAnswer,
        reply: String?,
    ): List<String> {
        val typed = given.text?.trim()?.takeIf { it.isNotEmpty() }
        val notes = given.notes?.trim()?.takeIf { it.isNotEmpty() }
        val picked = question.options.map { it.label }.filter { it in given.selected }
        // A typed answer to a choice is the "None of the above" choice with the text as its note.
        val labels = if (picked.isEmpty() && typed != null && question.options.isNotEmpty()) listOf(OTHER_LABEL) else picked
        val note = listOfNotNull(typed, notes).joinToString("\n").ifEmpty { if (labels.isEmpty()) reply.orEmpty() else "" }
        return labels + listOfNotNull(note.takeIf { it.isNotEmpty() }?.let { NOTE_PREFIX + it })
    }

    /**
     * The answers as one line for the chat's row, written the way Claude Code words its own
     * (`"question"="answer"`), so one reader shows both.
     */
    fun summary(
        questions: UserQuestions,
        decision: PermissionDecision,
    ): String {
        if (decision !is PermissionDecision.Answer) return "The user dismissed the questions."
        val answered =
            questions.questions.mapNotNull { question ->
                val given = decision.answers[question.id] ?: return@mapNotNull null
                if (!given.isAnswered) return@mapNotNull null
                val shown = if (question.secret) "•••" else (given.selected + listOfNotNull(given.text?.trim()?.takeIf { it.isNotEmpty() })).joinToString(", ")
                "\"${question.question}\"=\"$shown\""
            }
        return when {
            answered.isNotEmpty() -> "The user answered: " + answered.joinToString(", ") + "."
            decision.reply != null -> "The user responded: " + decision.reply
            else -> "The user did not answer the questions."
        }
    }

    private fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull == true
}
