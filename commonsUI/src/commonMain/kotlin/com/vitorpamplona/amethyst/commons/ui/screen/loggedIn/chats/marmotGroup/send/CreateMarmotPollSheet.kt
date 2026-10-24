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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotPolls
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_add_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_closes_at
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_create
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_deadline_days
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_deadline_hours
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_duplicate_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_has_deadline
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_invalid
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_invalid_text
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_multiple_answers
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_new
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_no_deadline
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_not_anonymous
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_option_count
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_options
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_pick_many
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_pick_one
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_question
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_remove_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_stays_open
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

/** Deadlines offered by the sheet, in seconds; MDK caps a poll at 30 days. */
private val DEADLINES = listOf(null, 3_600L, 24 * 3_600L, 7 * 24 * 3_600L, MarmotPolls.MAX_LIFETIME_SECONDS)

/** One option being written. [id] keys its row, so removing another keeps this one's text and focus. */
@Stable
private class DraftOption(
    val id: Int,
) {
    var text by mutableStateOf("")

    val label: String get() = text.trim()
}

/**
 * What the sheet holds while a poll is being written. There is always one empty row at the end
 * (up to [MarmotPolls.MAX_OPTIONS]) to type the next option into, so adding one is just typing.
 * Text is checked trimmed, the way it is sent: MDK refuses padded labels outright.
 */
@Stable
private class PollDraft {
    var question by mutableStateOf("")
    var multiple by mutableStateOf(false)
    var deadline by mutableStateOf<Long?>(null)

    private var nextId = 0
    val options = mutableStateListOf(DraftOption(nextId++), DraftOption(nextId++))

    fun edit(
        option: DraftOption,
        text: String,
    ) {
        option.text = text
        keepTrailingRow()
    }

    fun remove(option: DraftOption) {
        options.remove(option)
        while (options.size < MarmotPolls.MIN_OPTIONS) options.add(DraftOption(nextId++))
        keepTrailingRow()
    }

    private fun keepTrailingRow() {
        if (options.last().text.isNotEmpty() && options.size < MarmotPolls.MAX_OPTIONS) options.add(DraftOption(nextId++))
    }

    /** The empty row at the end is where the next option goes, not an option to remove. */
    fun canRemove(option: DraftOption) = options.size > MarmotPolls.MIN_OPTIONS && option.text.isNotEmpty()

    fun isTrailing(option: DraftOption) = option === options.last() && option.text.isEmpty() && options.size > MarmotPolls.MIN_OPTIONS

    val labels: List<String> get() = options.map { it.label }.filter { it.isNotEmpty() }

    val questionInvalid: Boolean
        get() = question.isNotBlank() && !MarmotPolls.isValidDisplayText(question.trim(), MarmotPolls.MAX_QUESTION_BYTES)

    fun isInvalid(option: DraftOption) = option.label.isNotEmpty() && !MarmotPolls.isValidDisplayText(option.label, MarmotPolls.MAX_OPTION_BYTES)

    /** Two options voters cannot tell apart split the vote for nothing. */
    fun isDuplicate(option: DraftOption) = option.label.isNotEmpty() && options.count { it.label.equals(option.label, ignoreCase = true) } > 1

    val isReady: Boolean
        get() =
            question.isNotBlank() &&
                !questionInvalid &&
                labels.size in MarmotPolls.MIN_OPTIONS..MarmotPolls.MAX_OPTIONS &&
                options.none { isInvalid(it) || isDuplicate(it) }
}

/**
 * Write an encrypted NIP-88 poll for a Marmot group: a question, 2-10 options, one answer or
 * several, and an optional deadline of at most 30 days.
 *
 * The options are drawn the way the poll card draws them, with the glyph a voter will tap
 * (round for one answer, square for several), so what is written is what members see.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMarmotPollSheet(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val draft = remember { PollDraft() }
    var sending by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurfaces.color,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PollSheetHeader(onClose = ::close)

            val firstOption = remember { FocusRequester() }
            QuestionField(
                draft = draft,
                onNext = { firstOption.requestFocus() },
            )

            OptionsSection(draft, firstOption)

            PollSettingsCard(draft)

            AnimatedVisibility(visible = refused, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(stringRes(Res.string.marmot_poll_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                // One poll per tap: a second tap while the first is being sent would post it twice.
                enabled = draft.isReady && !sending,
                onClick = {
                    sending = true
                    refused = false
                    val type = if (draft.multiple) MarmotPolls.PollType.MULTIPLE_CHOICE else MarmotPolls.PollType.SINGLE_CHOICE
                    val endsAt = draft.deadline?.let { TimeUtils.now() + it }
                    scope.launch {
                        if (accountViewModel.createMarmotPoll(nostrGroupId, draft.question.trim(), draft.labels, type, endsAt)) {
                            close()
                        } else {
                            refused = true
                            sending = false
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(MaterialSymbols.Poll, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(Res.string.marmot_poll_create), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PollSheetHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MaterialSymbols.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringRes(Res.string.marmot_poll_new), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    MaterialSymbols.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringRes(Res.string.marmot_poll_not_anonymous),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.close))
        }
    }
}

/** The question, set like a headline: it is what the poll is. Enter moves on to the options. */
@Composable
private fun QuestionField(
    draft: PollDraft,
    onNext: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)

    Column {
        BasicTextField(
            value = draft.question,
            onValueChange = { text ->
                if ('\n' in text) onNext() else draft.question = text
            },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (draft.question.isEmpty()) {
                        Text(stringRes(Res.string.marmot_poll_question), style = style, color = MaterialTheme.colorScheme.placeholderText)
                    }
                    inner()
                }
            },
        )
        AnimatedVisibility(visible = draft.questionInvalid, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                stringRes(Res.string.marmot_poll_invalid_text),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun OptionsSection(
    draft: PollDraft,
    firstOption: FocusRequester,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringRes(Res.string.marmot_poll_options),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringRes(Res.string.marmot_poll_option_count, draft.labels.size, MarmotPolls.MAX_OPTIONS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }

        draft.options.forEachIndexed { index, option ->
            key(option.id) {
                // New rows grow in; the two the sheet opens with are simply there.
                val appear = remember { MutableTransitionState(index < MarmotPolls.MIN_OPTIONS) }.apply { targetState = true }
                AnimatedVisibility(visibleState = appear, enter = fadeIn() + expandVertically()) {
                    OptionRow(
                        option = option,
                        placeholder =
                            if (draft.isTrailing(option)) {
                                stringRes(Res.string.marmot_poll_add_option)
                            } else {
                                stringRes(Res.string.marmot_poll_option, index + 1)
                            },
                        multiple = draft.multiple,
                        trailing = draft.isTrailing(option),
                        error = draft.isInvalid(option) || draft.isDuplicate(option),
                        canRemove = draft.canRemove(option),
                        focusRequester = if (index == 0) firstOption else null,
                        onChange = { text ->
                            if ('\n' in text) {
                                focusManager.moveFocus(FocusDirection.Down)
                            } else {
                                draft.edit(option, text)
                            }
                        },
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onRemove = { draft.remove(option) },
                    )
                }
            }
        }

        val problem =
            when {
                draft.options.any { draft.isInvalid(it) } -> Res.string.marmot_poll_invalid_text
                draft.options.any { draft.isDuplicate(it) } -> Res.string.marmot_poll_duplicate_option
                else -> null
            }
        AnimatedVisibility(visible = problem != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            problem?.let { Text(stringRes(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** One option, shaped like the row a voter will tap in the poll card. */
@Composable
private fun OptionRow(
    option: DraftOption,
    placeholder: String,
    multiple: Boolean,
    trailing: Boolean,
    error: Boolean,
    canRemove: Boolean,
    focusRequester: FocusRequester?,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(
        when {
            error -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
    )
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(if (trailing) CardSurfaces.color else CardSurfaces.well)
                .border(1.dp, borderColor, shape)
                .padding(start = 16.dp, end = 4.dp),
    ) {
        if (trailing) {
            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.placeholderText)
        } else {
            ChoiceGlyph(multiple = multiple, active = option.text.isNotBlank())
        }
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = option.text,
            onValueChange = onChange,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            maxLines = 3,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (option.text.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.placeholderText)
                    inner()
                }
            },
        )
        if (canRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.marmot_poll_remove_option),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** The mark a voter taps: round for one answer, square for several. It morphs as the mode changes. */
@Composable
private fun ChoiceGlyph(
    multiple: Boolean,
    active: Boolean,
) {
    val corner by animateDpAsState(if (multiple) 5.dp else 10.dp)
    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    Box(Modifier.size(20.dp).border(2.dp, color, RoundedCornerShape(corner)))
}

@Composable
private fun PollSettingsCard(draft: PollDraft) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CardSurfaces.well)
                .border(CardSurfaces.border, shape),
    ) {
        SettingRow(
            symbol = if (draft.multiple) MaterialSymbols.DoneAll else MaterialSymbols.RadioButtonChecked,
            title = stringRes(Res.string.marmot_poll_multiple_answers),
            subtitle = stringRes(if (draft.multiple) Res.string.marmot_poll_pick_many else Res.string.marmot_poll_pick_one),
            modifier = Modifier.toggleable(value = draft.multiple, role = Role.Switch, onValueChange = { draft.multiple = it }),
        ) {
            Switch(checked = draft.multiple, onCheckedChange = null)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        val closesAt = remember(draft.deadline) { draft.deadline?.let { (TimeUtils.now() + it) * 1000 } }
        SettingRow(
            symbol = if (draft.deadline == null) MaterialSymbols.TimerOff else MaterialSymbols.Timer,
            title = stringRes(Res.string.marmot_poll_has_deadline),
            subtitle =
                closesAt?.let { stringRes(Res.string.marmot_poll_closes_at, formatDateTime(it, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)) }
                    ?: stringRes(Res.string.marmot_poll_stays_open),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        ) {
            DEADLINES.forEach { seconds ->
                FilterChip(
                    selected = draft.deadline == seconds,
                    onClick = { draft.deadline = seconds },
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(deadlineLabel(seconds)) },
                )
            }
        }
    }
}

@Composable
private fun deadlineLabel(seconds: Long?): String =
    when {
        seconds == null -> stringRes(Res.string.marmot_poll_no_deadline)
        seconds < 24 * 3_600L -> (seconds / 3_600L).toInt().let { pluralStringRes(Res.plurals.marmot_poll_deadline_hours, it, it) }
        else -> (seconds / (24 * 3_600L)).toInt().let { pluralStringRes(Res.plurals.marmot_poll_deadline_days, it, it) }
    }

@Composable
private fun SettingRow(
    symbol: MaterialSymbol,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
