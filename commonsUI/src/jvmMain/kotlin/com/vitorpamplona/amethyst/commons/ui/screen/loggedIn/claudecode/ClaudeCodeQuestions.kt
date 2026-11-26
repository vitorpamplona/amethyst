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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.QuestionAnswer
import com.vitorpamplona.agents.UserQuestion
import com.vitorpamplona.agents.UserQuestions
import com.vitorpamplona.amethyst.commons.claudecode.AskedQuestions
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_add_note
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_ask_more
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_ask_more_tooltip
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_dismiss
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_keys
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_keys_reply
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_no_answer
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_note_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_number_value
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_other_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_pick_any
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_preview
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_submit
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_text_hint
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_title
import com.vitorpamplona.amethyst.commons.resources.claude_code_question_waiting
import com.vitorpamplona.amethyst.commons.resources.claude_code_questions_title
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What the user has filled in on the card for one request, kept by the screen: its key handler
 * reads it, so Esc never throws away a form the user has started.
 */
@Stable
internal class QuestionForm(
    val questions: UserQuestions,
) {
    internal val drafts = questions.questions.map { QuestionDraft(it) }

    /** Something is answered: the answers can be sent. */
    val canSend: Boolean by derivedStateOf { drafts.any { it.isAnswered } }

    /** The user picked, typed or noted anything: dismissing would lose it. */
    val hasInput: Boolean by derivedStateOf { drafts.any { it.hasInput } }

    fun answers(askMore: Boolean) = PermissionDecision.Answer(drafts.associate { it.question.id to it.answer() }, askMore = askMore)
}

/** What the user has chosen and typed for one question, while the card is open. */
@Stable
internal class QuestionDraft(
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

    /** [answer] has something in it, read without building it. */
    val isAnswered: Boolean
        get() =
            when (question.kind) {
                UserQuestion.Kind.CHOICE -> selected.isNotEmpty() || (otherChosen && text.isNotBlank())
                UserQuestion.Kind.TEXT -> text.isNotBlank()
                UserQuestion.Kind.NUMBER -> number != null
            }

    /** The user touched it: a pick, a typed word or a note. A number's starting value isn't theirs. */
    val hasInput: Boolean
        get() = selected.isNotEmpty() || text.isNotBlank() || notes.isNotBlank() || (question.kind == UserQuestion.Kind.NUMBER && number != question.defaultValue)

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
    form: QuestionForm,
    onAnswer: (PermissionDecision.Answer) -> Unit,
    onDismiss: () -> Unit,
) {
    val questions = form.questions
    val drafts = form.drafts
    // Read through the form's derived states, so typing recomposes the field and the Send button, not every question.
    val currentOnAnswer by rememberUpdatedState(onAnswer)
    val onEnter = remember(form) { { if (form.canSend) currentOnAnswer(form.answers(askMore = false)) } }
    val agent = LocalAgentName.current
    // A short window keeps room for the conversation: the questions scroll within at most two fifths of it.
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val questionsMax = (windowHeight * 0.4f).coerceIn(200.dp, 520.dp)
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
                Modifier.heightIn(max = questionsMax).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FormGap),
            ) {
                drafts.forEachIndexed { i, draft ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    QuestionBlock(draft, onEnter)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onAnswer(form.answers(askMore = false)) }, enabled = form.canSend) { Text(stringRes(Res.string.claude_code_question_submit)) }
                if (questions.acceptsAskMore) {
                    HoverTooltip(stringRes(Res.string.claude_code_question_ask_more_tooltip, agent)) {
                        OutlinedButton(onClick = { onAnswer(form.answers(askMore = true)) }) { Text(stringRes(Res.string.claude_code_question_ask_more)) }
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.claude_code_question_dismiss)) }
                Spacer(Modifier.weight(1f))
                Text(
                    // Esc dismisses only a form with nothing in it (the screen's key handler).
                    stringRes(if (form.hasInput) Res.string.claude_code_question_keys_reply else Res.string.claude_code_question_keys),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.header?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        Text(question.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            OptionTile(
                multi = question.multiSelect,
                checked = option.label in draft.selected,
                interaction = interaction,
                onClick = {
                    draft.pick(option.label)
                    lastPicked = option.label
                },
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
                }
            }
        }
        if (question.allowsOther) {
            // The user's own answer is a tile like the others, typed into where its label would be.
            val focus = remember { FocusRequester() }
            OptionTile(
                multi = question.multiSelect,
                checked = draft.otherChosen,
                interaction = remember { MutableInteractionSource() },
                onClick = {
                    draft.chooseOther(!draft.otherChosen || !question.multiSelect)
                    if (draft.otherChosen) focus.requestFocus()
                },
            ) {
                BareTextField(
                    value = draft.text,
                    onValueChange = {
                        draft.text = it
                        // Typing an answer of one's own picks it.
                        if (it.isNotBlank() && !draft.otherChosen) draft.chooseOther(true)
                    },
                    placeholder = stringRes(Res.string.claude_code_question_other_hint),
                    secret = question.secret,
                    onEnter = onEnter,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        }
    }
    val withPreview = remember(question) { question.options.filter { it.preview != null } }
    if (withPreview.isEmpty()) return
    // Which preview shows changes on hover: read in the draw phase and in the label, never by the markdown.
    val shown =
        remember(withPreview, draft) {
            derivedStateOf {
                (
                    withPreview.firstOrNull { it.label == hovered }
                        ?: withPreview.firstOrNull { it.label == lastPicked }
                        ?: withPreview.firstOrNull { it.label in draft.selected }
                        ?: withPreview.first()
                ).label
            }
        }
    run {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringRes(Res.string.claude_code_question_preview, shown.value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = TileShape, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Every preview is laid out, the others unseen, so the box is as tall as the tallest:
                    // a card that changed height on hover would move the options out from under the pointer.
                    // The surface is the code block's box: the markdown draws none of its own inside it.
                    val style = rememberChatMarkdownStyle(codeBackground = Color.Transparent)
                    val onLink = rememberChatLinks(nav = null)
                    withPreview.forEach { option ->
                        ChatMarkdown(
                            option.preview ?: "",
                            onLink,
                            Modifier.fillMaxWidth().graphicsLayer { alpha = if (option.label == shown.value) 1f else 0f },
                            style = style,
                            textStyle = MaterialTheme.typography.bodySmall,
                            chips = false,
                        )
                    }
                }
            }
        }
    }
}

/** The tiles' corners, and the fields' and preview's that sit with them. */
private val TileShape = RoundedCornerShape(12.dp)

/**
 * A choice as a tile: the whole tile picks it. Picked, it takes the primary color in its outline,
 * a light tint and its mark: a filled dot for one of many, a check for any of many.
 */
@Composable
private fun OptionTile(
    multi: Boolean,
    checked: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pick =
        if (multi) {
            Modifier.toggleable(value = checked, interactionSource = interaction, indication = ripple(), role = Role.Checkbox, onValueChange = { onClick() })
        } else {
            Modifier.selectable(selected = checked, interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = onClick)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            // Hover is the ripple's own layer, from [interaction].
            .background(if (checked) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (checked) colors.primary else colors.outlineVariant, TileShape)
            .hoverable(interaction)
            .then(pick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceMark(multi, checked)
        Spacer(Modifier.width(12.dp))
        content()
    }
}

/** The mark of a tile: a ring that fills with a dot (one of many), or a rounded box that fills with a check (any of many). */
@Composable
private fun ChoiceMark(
    multi: Boolean,
    checked: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (checked) colors.primary else colors.outline
    if (multi) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) colors.primary else Color.Transparent)
                .border(1.5.dp, tint, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(MaterialSymbols.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
        }
    } else {
        Icon(if (checked) MaterialSymbols.RadioButtonChecked else MaterialSymbols.RadioButtonUnchecked, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NumberAnswer(draft: QuestionDraft) {
    val question = draft.question
    val min = question.min ?: 0.0
    val max = (question.max ?: (min + 100.0)).coerceAtLeast(min + 1e-9)
    val step = question.step?.takeIf { it > 0 }
    // Ticks only where there are few enough to read; past that the value still snaps to its steps.
    val stops = step?.let { ((max - min) / it).roundToInt() - 1 }?.takeIf { it in 0..MAX_TICKS } ?: 0
    val value = draft.number
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatNumber(min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
        Slider(
            value = (value ?: min).toFloat(),
            onValueChange = { raw ->
                val snapped = if (step != null) min + ((raw - min) / step).roundToInt() * step else raw.toDouble()
                draft.number = snapped.coerceIn(min, max)
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = stops,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(formatNumber(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
        Spacer(Modifier.width(PartGap))
        // The value in a tile of its own, as wide as the largest value would be: it doesn't jump as it changes.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = TileShape) {
            Text(
                value?.let { v -> question.unit?.let { stringRes(Res.string.claude_code_question_number_value, formatNumber(v), it) } ?: formatNumber(v) }
                    ?: stringRes(Res.string.claude_code_question_no_answer),
                style = MaterialTheme.typography.labelLarge,
                color = if (value == null) MaterialTheme.colorScheme.placeholderText else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NoteField(
    draft: QuestionDraft,
    onEnter: () -> Unit,
) {
    if (draft.notesOpen) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AnswerField(
            value = draft.notes,
            onValueChange = { draft.notes = it },
            placeholder = stringRes(Res.string.claude_code_question_note_hint),
            secret = false,
            onEnter = onEnter,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            leading = MaterialSymbols.EditNote,
        )
    } else {
        // Quiet: every question has one, and most answers need none.
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { draft.notesOpen = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.claude_code_question_add_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
        }
    }
}

/**
 * The user's own words in a field of the tiles' shape: a tonal fill, no outline until it has
 * focus. Enter sends the answers once something is answered.
 */
@Composable
private fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    leading: MaterialSymbol? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(TileShape)
            .background(colors.surfaceContainerHighest)
            .border(1.dp, if (focused) colors.primary else Color.Transparent, TileShape)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Icon(it, contentDescription = null, tint = if (focused) colors.primary else colors.grayText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        BareTextField(value, onValueChange, placeholder, secret, onEnter, Modifier.weight(1f), interaction)
    }
}

/** One line of text with its placeholder and nothing around it: the tile or field it sits in is its box. */
@Composable
private fun BareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        interactionSource = interaction,
        modifier =
            modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.placeholderText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
}

/** More stops than this and a slider's ticks run together. */
private const val MAX_TICKS = 10

/**
 * A question call in the chat: each question with the user's answer, from the call's result.
 * While it waits, the card above the composer is where it is answered.
 */
@Composable
internal fun QuestionCallRow(
    call: ChatItem.ToolCall,
    /** The chat is mid-turn: an unanswered call still waits. Otherwise it was cut off. */
    waiting: Boolean,
) {
    val questions = remember(call.name, call.input) { AskedQuestions.questions(call.name, call.input) }
    if (questions == null) {
        ToolCallRow(call, cwd = "", turnRunning = waiting)
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
            // Nothing answered (a reply instead, or dismissed): the questions once, then what happened, in the agent's words.
            val noneAnswered = (call.isDone || !waiting) && answers.isEmpty()
            questions.questions.forEach { question ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(question.question, style = MaterialTheme.typography.bodyMedium, color = if (noneAnswered) MaterialTheme.colorScheme.grayText else Color.Unspecified)
                    val answer = answers[question.id]
                    if (!noneAnswered) {
                        Text(
                            when {
                                answer != null -> if (question.secret) "•••" else answer
                                !call.isDone && waiting -> stringRes(Res.string.claude_code_question_waiting)
                                else -> stringRes(Res.string.claude_code_question_no_answer)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (answer != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.placeholderText,
                        )
                    }
                }
            }
            if (noneAnswered) {
                call.result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
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
