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
package com.vitorpamplona.agents

/**
 * Questions an agent stops to ask the user in the middle of a turn: Claude Code's
 * `AskUserQuestion`, Codex's `request_user_input`. They come as a [PermissionRequest] whose
 * [PermissionRequest.questions] is set, and are answered with [PermissionDecision.Answer].
 */
data class UserQuestions(
    /** One to four questions, most important first. */
    val questions: List<UserQuestion>,
    /** A heading over the questions, when the agent gave one. */
    val title: String? = null,
    /** The user may ask for another round of questions instead of answering ([PermissionDecision.Answer.askMore]). */
    val acceptsAskMore: Boolean = false,
)

data class UserQuestion(
    /** How an answer names the question: Claude Code keys answers by the question's text, Codex by an id. */
    val id: String,
    val question: String,
    /** A short label for the question ("Auth method"). */
    val header: String? = null,
    /** One more line under the question. */
    val description: String? = null,
    val kind: Kind = Kind.CHOICE,
    val options: List<Option> = emptyList(),
    /** More than one option may be picked. */
    val multiSelect: Boolean = false,
    /** Besides the options, the user may type an answer of their own. */
    val allowsOther: Boolean = true,
    /** What the user types is hidden as it is typed (a password, a token). */
    val secret: Boolean = false,
    /** For [Kind.TEXT]: what the empty box says. */
    val placeholder: String? = null,
    /** For [Kind.NUMBER]: the range, step, starting value and unit ("px"). */
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val defaultValue: Double? = null,
    val unit: String? = null,
) {
    enum class Kind {
        /** Pick from [options] (or type an answer, when [allowsOther]). */
        CHOICE,

        /** Type the answer. */
        TEXT,

        /** A number between [min] and [max]. */
        NUMBER,
    }

    data class Option(
        val label: String,
        val description: String? = null,
        /** What the choice would look like (a layout, a snippet), as markdown. */
        val preview: String? = null,
    )
}

/** The user's answer to one [UserQuestion]. */
data class QuestionAnswer(
    /** The labels of the options picked, in the options' order. */
    val selected: List<String> = emptyList(),
    /** What the user typed: their own answer to a choice, or the answer to a text or number question. */
    val text: String? = null,
    /** Notes the user added to the answer. */
    val notes: String? = null,
) {
    val isAnswered: Boolean get() = selected.isNotEmpty() || !text.isNullOrBlank()
}
