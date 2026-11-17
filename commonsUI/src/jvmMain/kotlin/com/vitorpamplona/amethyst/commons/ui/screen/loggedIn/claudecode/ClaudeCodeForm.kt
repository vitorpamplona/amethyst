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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_short_accept_edits
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_short_auto
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_short_default
import com.vitorpamplona.amethyst.commons.resources.claude_code_mode_short_plan
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

// The pieces the coding-agent screens build their forms from, in the app's form look
// (`FormFields.kt`): labels above, filled fields, switches for settings, segments for choices.

// The spacing these screens share, on a 4 dp scale. One value per job, so the edges line up and
// the gaps say what belongs together: the same job never gets two values.

/** The one inset of a screen's column: messages, composer, banners, cards and lists all start here. */
internal val Gutter = 16.dp

/** The inside of a card, a banner or a callout. */
internal val CardPadding = 16.dp

/** The inside of a dense row (a menu item, a suggestion, a commit). */
internal val RowPaddingH = 12.dp
internal val RowPaddingV = 8.dp

/** Between an icon and its text, or two small things on a line. */
internal val InlineGap = 8.dp

/** Between the parts of one thing: a reply and its tool calls, the fields of one step. */
internal val PartGap = 12.dp

/** Space between the groups of a form: a label, its field and its help text are one group. */
internal val FormGap = 18.dp

/** Between groups that stand apart: a new turn of the chat, the blocks of a screen. */
internal val GroupGap = 24.dp

/** Between sections and steps: the largest break on a screen. */
internal val SectionGap = 32.dp

/**
 * Lays [content] out at its drawn size: chips and inline text buttons otherwise keep a 48 dp touch
 * area that adds gaps nobody chose. For rows of them, where the row itself is easy to hit.
 */
@Composable
internal fun Dense(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = content)
}

/**
 * A setting that is on or off, with what it does: the whole row toggles it, as the settings
 * screens' rows do.
 */
@Composable
internal fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
        }
        Spacer(Modifier.width(PartGap))
        // The row handles the click: the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A box to tick, with its sentence: the whole row ticks it. */
@Composable
internal fun CheckRow(
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(PartGap))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * One of a few options that exclude each other, all in sight and one tap away: the form look's
 * segmented row, without the check icon (the fill already says which is chosen).
 */
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A permission mode in a word, for a segment: its sentence goes under the row. */
@Composable
internal fun modeShortLabel(mode: PermissionMode): String =
    when (mode) {
        PermissionMode.ACCEPT_EDITS -> stringRes(Res.string.claude_code_mode_short_accept_edits)
        PermissionMode.PLAN -> stringRes(Res.string.claude_code_mode_short_plan)
        PermissionMode.AUTO -> stringRes(Res.string.claude_code_mode_short_auto)
        else -> stringRes(Res.string.claude_code_mode_short_default)
    }

/**
 * The background of a dialog with form fields: a tone below the fields' fill, which is the
 * dialog's own default tone and would make them vanish into it.
 */
@Composable
internal fun formDialogColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

/**
 * A quiet action set inline with text (a status line, a heading): 32 dp tall, so it doesn't make
 * its line taller than the text beside it.
 */
@Composable
internal fun InlineAction(
    text: String,
    onClick: () -> Unit,
) {
    Dense {
        TextButton(onClick = onClick, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(text) }
    }
}
