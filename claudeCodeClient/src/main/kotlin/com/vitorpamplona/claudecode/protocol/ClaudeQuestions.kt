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
package com.vitorpamplona.claudecode.protocol

import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.QuestionAnswer
import com.vitorpamplona.agents.UserQuestion
import com.vitorpamplona.agents.UserQuestions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Claude Code's `AskUserQuestion`: the questions come as a `can_use_tool` prompt for that tool,
 * and the host answers by allowing the call with the user's answers added to its input.
 *
 * Input: `questions` (1-4), each with `question`, `header`, `options` (`label`, `description`,
 * `preview`) and `multiSelect`. With [HOST_ENVIRONMENT] the CLI may also send a `title` and
 * questions of `kind` `text` (a box) or `number` (`min`, `max`, `step`, `defaultValue`, `unit`).
 *
 * Answer: the same input, unchanged, plus `answers` (question text to the label picked, an array
 * of labels for a multi-select, or the text typed), `annotations` (question text to `notes`, the
 * picked option's `preview`, and with [UNANSWERED_CAPABILITY] how it went `unanswered`),
 * `response` (a message the user wrote instead) and `followUp` (asked for more questions). The
 * CLI refuses an answer that changes the input or carries anything else.
 */
object ClaudeQuestions {
    const val TOOL = "AskUserQuestion"

    /** In `init`'s `capabilities`: an answer may say why a question went unanswered. */
    const val UNANSWERED_CAPABILITY = "ask_user_question_unanswered_v1"

    /**
     * What a host sets to get the whole form: previews drawn as markdown, text and number
     * questions with a title and "ask me more" (extended), and options without descriptions.
     * The CLI reads the last two only from an SDK host (`-p`), which this client is.
     */
    val HOST_ENVIRONMENT =
        mapOf(
            "CLAUDE_CODE_QUESTION_PREVIEW_FORMAT" to "markdown",
            "CLAUDE_CODE_QUESTION_EXTENDED" to "1",
            "CLAUDE_CODE_QUESTION_OPTIONAL_DESCRIPTIONS" to "1",
        )

    /** What the user types, in all, that the CLI takes in one answer. */
    private const val MAX_TYPED = 32_768
    private const val MAX_NOTES = 8_192

    fun parse(input: JsonObject): UserQuestions? {
        val raw = input.array("questions")?.mapNotNull { it as? JsonObject }.orEmpty()
        val questions = raw.mapNotNull(::question)
        if (questions.isEmpty()) return null
        // The extended form gives every question a kind; only it takes a follow-up.
        val extended = raw.all { it.string("kind") != null }
        return UserQuestions(questions, title = input.string("title")?.takeIf { it.isNotBlank() }, acceptsAskMore = extended)
    }

    private fun question(obj: JsonObject): UserQuestion? {
        val text = obj.string("question") ?: return null
        val options =
            obj.array("options").orEmpty().mapNotNull { element ->
                val option = element as? JsonObject ?: return@mapNotNull null
                UserQuestion.Option(
                    label = option.string("label") ?: return@mapNotNull null,
                    description = option.string("description")?.takeIf { it.isNotBlank() },
                    preview = option.string("preview")?.takeIf { it.isNotBlank() },
                )
            }
        val kind =
            when (obj.string("kind")) {
                "text" -> UserQuestion.Kind.TEXT
                "number" -> UserQuestion.Kind.NUMBER
                else -> UserQuestion.Kind.CHOICE
            }
        return UserQuestion(
            id = text,
            question = text,
            header = obj.string("header")?.takeIf { it.isNotBlank() },
            description = obj.string("description")?.takeIf { it.isNotBlank() },
            kind = kind,
            options = if (kind == UserQuestion.Kind.CHOICE) options else emptyList(),
            multiSelect = kind == UserQuestion.Kind.CHOICE && obj.boolean("multiSelect") == true,
            // The CLI's own form always offers "Other": the tool tells the model not to add one.
            allowsOther = true,
            placeholder = obj.string("placeholder"),
            min = obj.number("min"),
            max = obj.number("max"),
            step = obj.number("step"),
            defaultValue = obj.number("defaultValue"),
            unit = obj.string("unit"),
        )
    }

    /**
     * The input to allow the call with: [input] as the CLI sent it plus the user's [answer].
     * [unansweredNotes] when the session advertised [UNANSWERED_CAPABILITY].
     */
    fun answeredInput(
        input: JsonObject,
        questions: UserQuestions,
        answer: PermissionDecision.Answer,
        unansweredNotes: Boolean,
    ): JsonObject {
        // The CLI counts the picked labels against the same limit as typed text (a multi-select's
        // joined with ", "): they always go, so they come off the budget first.
        var budget =
            MAX_TYPED -
                questions.questions.sumOf { question ->
                    val picked = question.options.map { it.label }.filter { it in (answer.answers[question.id]?.selected ?: emptyList()) }
                    picked.sumOf { it.length + 2 }
                }

        fun take(
            text: String,
            max: Int = MAX_TYPED,
        ): String {
            val kept = text.take(minOf(max, budget).coerceAtLeast(0))
            budget -= kept.length
            return kept
        }

        val reply =
            answer.reply
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { take(it) }
        val answers = LinkedHashMap<String, JsonElement>()
        val annotations = LinkedHashMap<String, JsonElement>()
        questions.questions.forEach { question ->
            val given = answer.answers[question.id] ?: QuestionAnswer()
            value(question, given, ::take)?.let { answers[question.id] = it }
            val note = LinkedHashMap<String, JsonElement>()
            given.notes
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { note["notes"] = JsonPrimitive(take(it, MAX_NOTES)) }
            // The preview the user picked goes back with the answer, as the CLI's own form does.
            if (!question.multiSelect && given.selected.size == 1) {
                question.options
                    .firstOrNull { it.label == given.selected[0] }
                    ?.preview
                    ?.let { note["preview"] = JsonPrimitive(it) }
            }
            if (unansweredNotes && question.id !in answers && note.isEmpty()) {
                note["unanswered"] = JsonPrimitive(if (reply != null) "replied_in_chat" else "skipped")
            }
            if (note.isNotEmpty()) annotations[question.id] = JsonObject(note)
        }
        val added =
            buildMap<String, JsonElement> {
                put("answers", JsonObject(answers))
                if (annotations.isNotEmpty()) put("annotations", JsonObject(annotations))
                reply?.let { put("response", JsonPrimitive(it)) }
                if (answer.askMore && questions.acceptsAskMore) put("followUp", JsonPrimitive(true))
            }
        return JsonObject(input + added)
    }

    /** One question's entry in `answers`, or null when the user gave none. */
    private fun value(
        question: UserQuestion,
        given: QuestionAnswer,
        take: (String, Int) -> String,
    ): JsonElement? {
        val typed = given.text?.trim()?.takeIf { it.isNotEmpty() }
        return when (question.kind) {
            UserQuestion.Kind.TEXT, UserQuestion.Kind.NUMBER -> typed?.let { JsonPrimitive(take(it, MAX_TYPED)) }
            UserQuestion.Kind.CHOICE -> {
                val known = question.options.map { it.label }
                val picked = known.filter { it in given.selected }
                if (question.multiSelect) {
                    val all = picked + listOfNotNull(typed?.let { take(it, MAX_TYPED - 2) }?.takeIf { it.isNotEmpty() })
                    if (all.isEmpty()) null else JsonArray(all.map(::JsonPrimitive))
                } else {
                    (picked.firstOrNull() ?: typed?.let { take(it, MAX_TYPED) })?.let(::JsonPrimitive)
                }
            }
        }
    }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
}
