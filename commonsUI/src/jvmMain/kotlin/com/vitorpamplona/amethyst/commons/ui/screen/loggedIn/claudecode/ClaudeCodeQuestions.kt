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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.QuestionAnswer
import com.vitorpamplona.agents.UserQuestion
import com.vitorpamplona.agents.UserQuestions
import com.vitorpamplona.amethyst.commons.claudecode.AskedQuestions
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_add_note
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_ask_more
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_ask_more_tooltip
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_dismiss
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_keys
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_no_answer
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_note_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_number_value
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_other
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_other_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_pick_any
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_preview
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_submit
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_text_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_waiting
import com.vitorpamplona.amethyst.commons.resources.claude_code_questions_title
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.roundToInt

/** What the user has chosen and typed for one question, while the card is open. */
@Stable
private class QuestionDraft(
    val question: UserQuestion,
) {
    var selected by mutableStateOf(emptySet<String>())

    /** The typed answer is the one picked (a choice's "Other"). */
    var otherChosen by mutableStateOf(false)

    /** A choice's own answer, or the answer to a text question. */
    var text by mutableStateOf("")

    /** A number question's value, once the user set one (or the agent gave a starting value). */
    var number by mutableStateOf(question.defaultValue)
    var notes by mutableStateOf("")
    var notesOpen by mutableStateOf(false)

    fun pick(label: String) {
        if (question.multiSelect) {
            selected = if (label in selected) selected - label else selected + label
        } else {
            selected = setOf(label)
            otherChosen = false
        }
    }

    fun chooseOther(chosen: Boolean) {
        otherChosen = chosen
        if (chosen && !question.multiSelect) selected = emptySet()
    }

    fun answer(): QuestionAnswer =
        when (question.kind) {
            UserQuestion.Kind.CHOICE ->
                QuestionAnswer(
                    selected = question.options.map { it.label }.filter { it in selected },
                    text = text.takeIf { otherChosen && it.isNotBlank() },
                    notes = notes.takeIf { it.isNotBlank() },
                )
            UserQuestion.Kind.TEXT -> QuestionAnswer(text = text.takeIf { it.isNotBlank() }, notes = notes.takeIf { it.isNotBlank() })
            UserQuestion.Kind.NUMBER -> QuestionAnswer(text = number?.let { formatNumber(it) }, notes = notes.takeIf { it.isNotBlank() })
        }
}

/**
 * The agent's questions, as a form: choices to pick (several, when it asks for that), an answer
 * of the user's own, a box for text, a slider for a number, and a note on any of them. Sent
 * together; Esc dismisses them (the screen's key handler), and a message typed in the composer
 * goes back as a reply instead of answers.
 */
@Composable
internal fun QuestionCard(
    request: PermissionRequest,
    questions: UserQuestions,
    onAnswer: (PermissionDecision.Answer) -> Unit,
    onDismiss: () -> Unit,
) {
    val drafts = remember(request.requestId) { questions.questions.map { QuestionDraft(it) } }
    val answered = drafts.any { it.answer().isAnswered }
    val submit = { askMore: Boolean ->
        onAnswer(PermissionDecision.Answer(drafts.associate { it.question.id to it.answer() }, askMore = askMore))
    }
    val agent = LocalAgentName.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(PartGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(InlineGap))
                Text(
                    questions.title
                        ?: if (drafts.size == 1) {
                            stringRes(Res.string.claude_code_question_title, agent)
                        } else {
                            pluralStringRes(Res.plurals.claude_code_questions_title, drafts.size, agent, drafts.size)
                        },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Four questions with previews can be taller than the window: the questions scroll, the buttons stay.
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FormGap),
            ) {
                drafts.forEach { draft -> QuestionBlock(draft, onEnter = { if (answered) submit(false) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { submit(false) }, enabled = answered) { Text(stringRes(Res.string.claude_code_question_submit)) }
                if (questions.acceptsAskMore) {
                    HoverTooltip(stringRes(Res.string.claude_code_question_ask_more_tooltip, agent)) {
                        OutlinedButton(onClick = { submit(true) }) { Text(stringRes(Res.string.claude_code_question_ask_more)) }
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.claude_code_question_dismiss)) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringRes(Res.string.claude_code_question_keys),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    draft: QuestionDraft,
    onEnter: () -> Unit,
) {
    val question = draft.question
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        question.header?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        Text(question.question, style = MaterialTheme.typography.bodyLarge)
        val helper = question.description ?: if (question.multiSelect) stringRes(Res.string.claude_code_question_pick_any) else null
        helper?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
        when (question.kind) {
            UserQuestion.Kind.CHOICE -> ChoiceAnswer(draft, onEnter)
            UserQuestion.Kind.TEXT ->
                AnswerField(
                    value = draft.text,
                    onValueChange = { draft.text = it },
                    placeholder = question.placeholder ?: stringRes(Res.string.claude_code_question_text_hint),
                    secret = question.secret,
                    onEnter = onEnter,
                    modifier = Modifier.fillMaxWidth(),
                )
            UserQuestion.Kind.NUMBER -> NumberAnswer(draft)
        }
        if (!question.secret) NoteField(draft, onEnter)
    }
}

@Composable
private fun ChoiceAnswer(
    draft: QuestionDraft,
    onEnter: () -> Unit,
) {
    val question = draft.question
    // The preview shown is the hovered option's, else the one last picked, else the first one that has any.
    var hovered by remember { mutableStateOf<String?>(null) }
    var lastPicked by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        question.options.forEach { option ->
            val interaction = remember { MutableInteractionSource() }
            val isHovered by interaction.collectIsHoveredAsState()
            LaunchedEffect(isHovered) {
                if (isHovered) {
                    hovered = option.label
                } else if (hovered == option.label) {
                    hovered = null
                }
            }
            OptionRow(
                multi = question.multiSelect,
                checked = option.label in draft.selected,
                onClick = {
                    draft.pick(option.label)
                    lastPicked = option.label
                },
                modifier = Modifier.hoverable(interaction),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
                }
            }
        }
        if (question.allowsOther) {
            OptionRow(
                multi = question.multiSelect,
                checked = draft.otherChosen,
                onClick = { draft.chooseOther(!draft.otherChosen || !question.multiSelect) },
            ) {
                AnswerField(
                    value = draft.text,
                    onValueChange = {
                        draft.text = it
                        // Typing an answer of one's own picks it.
                        if (it.isNotBlank() && !draft.otherChosen) draft.chooseOther(true)
                    },
                    placeholder = stringRes(Res.string.claude_code_question_other_hint),
                    label = stringRes(Res.string.claude_code_question_other),
                    secret = question.secret,
                    onEnter = onEnter,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    val withPreview = question.options.filter { it.preview != null }
    val shown = withPreview.firstOrNull { it.label == hovered } ?: withPreview.firstOrNull { it.label == lastPicked } ?: withPreview.firstOrNull { it.label in draft.selected } ?: withPreview.firstOrNull()
    shown?.let { current ->
        Text(stringRes(Res.string.claude_code_question_preview, current.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                // Every preview is laid out, the others unseen, so the box is as tall as the tallest:
                // a card that changed height on hover would move the options out from under the pointer.
                withPreview.forEach { option ->
                    RenderMarkdown(
                        option.preview ?: "",
                        rememberChatLinks(nav = null),
                        Modifier.fillMaxWidth().alpha(if (option.label == current.label) 1f else 0f),
                        style = rememberChatMarkdownStyle(),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** A choice: the whole row picks it, a radio button for one of many, a box to tick for any of many. */
@Composable
private fun OptionRow(
    multi: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val pick =
        if (multi) {
            Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
        } else {
            Modifier.selectable(selected = checked, role = Role.RadioButton, onClick = onClick)
        }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(pick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dense {
            // The row handles the click: the control only shows the state.
            if (multi) Checkbox(checked = checked, onCheckedChange = null) else RadioButton(selected = checked, onClick = null)
        }
        Spacer(Modifier.width(PartGap))
        content()
    }
}

@Composable
private fun NumberAnswer(draft: QuestionDraft) {
    val question = draft.question
    val min = question.min ?: 0.0
    val max = (question.max ?: (min + 100.0)).coerceAtLeast(min + 1e-9)
    val step = question.step?.takeIf { it > 0 }
    // A slider snaps to its steps when there are few enough of them to tell apart.
    val stops = step?.let { ((max - min) / it).roundToInt() - 1 }?.takeIf { it in 0..200 } ?: 0
    val value = draft.number
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = (value ?: min).toFloat(),
            onValueChange = { raw ->
                val snapped = if (step != null) min + ((raw - min) / step).roundToInt() * step else raw.toDouble()
                draft.number = snapped.coerceIn(min, max)
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = stops,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(PartGap))
        Text(
            value?.let { v -> question.unit?.let { stringRes(Res.string.claude_code_question_number_value, formatNumber(v), it) } ?: formatNumber(v) }
                ?: stringRes(Res.string.claude_code_question_no_answer),
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) MaterialTheme.colorScheme.placeholderText else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NoteField(
    draft: QuestionDraft,
    onEnter: () -> Unit,
) {
    if (!draft.notesOpen) {
        InlineAction(stringRes(Res.string.claude_code_question_add_note)) { draft.notesOpen = true }
    }
    AnimatedVisibility(draft.notesOpen) {
        AnswerField(
            value = draft.notes,
            onValueChange = { draft.notes = it },
            placeholder = stringRes(Res.string.claude_code_question_note_hint),
            secret = false,
            onEnter = onEnter,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One line of the user's own words; Enter sends the answers once something is answered. */
@Composable
private fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = label?.let { { Text(it) } },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.placeholderText) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier =
            modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
    )
}

/**
 * A question call in the chat: each question with the user's answer, from the call's result.
 * While it waits, the card above the composer is where it is answered.
 */
@Composable
internal fun QuestionCallRow(
    call: ChatItem.ToolCall,
    turnRunning: Boolean,
) {
    val questions = remember(call.name, call.input) { AskedQuestions.questions(call.name, call.input) }
    if (questions == null) {
        ToolCallRow(call, cwd = "", turnRunning = turnRunning)
        return
    }
    val answers = remember(call.result, questions) { call.result?.let { AskedQuestions.answers(it, questions.questions) }.orEmpty() }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                questions.title ?: if (questions.questions.size == 1) {
                    stringRes(Res.string.claude_code_question_title, LocalAgentName.current)
                } else {
                    pluralStringRes(Res.plurals.claude_code_questions_title, questions.questions.size, LocalAgentName.current, questions.questions.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        Column(Modifier.padding(start = 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            questions.questions.forEach { question ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(question.question, style = MaterialTheme.typography.bodyMedium)
                    val answer = answers[question.id]
                    Text(
                        when {
                            answer != null -> if (question.secret) "•••" else answer
                            !call.isDone -> stringRes(Res.string.claude_code_question_waiting)
                            else -> stringRes(Res.string.claude_code_question_no_answer)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (answer != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }
            // A reply instead of answers, or questions dismissed: the result says so in its own words.
            call.result?.takeIf { answers.isEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (call.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.grayText)
            }
        }
    }
}

/** [value] as a person writes it: `4`, not `4.0`; `0.25` as it is. */
private fun formatNumber(value: Double): String =
    if (value == floor(value) && value in -1e15..1e15) {
        value.toLong().toString()
    } else {
        BigDecimal
            .valueOf(value)
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
