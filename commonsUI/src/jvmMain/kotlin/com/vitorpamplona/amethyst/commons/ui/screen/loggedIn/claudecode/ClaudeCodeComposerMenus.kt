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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.agents.ModelChoice
import com.vitorpamplona.agents.SlashCommand
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeChatState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_model_tooltip
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/** How many commands the menu lists at once: past that, typing narrows it. */
private const val COMMANDS_SHOWN = 8

/**
 * The commands that fit what is typed: a `/` and part of a name, before any argument. Names that
 * start with it come first, then names (and aliases) that contain it.
 */
internal fun slashMatches(
    text: String,
    commands: List<SlashCommand>,
): List<SlashCommand> {
    if (!text.startsWith("/") || text.any { it.isWhitespace() } || commands.isEmpty()) return emptyList()
    val typed = text.drop(1).lowercase()
    val (starts, rest) =
        commands
            .filter { c -> c.name.lowercase().contains(typed) || c.aliases.any { it.lowercase().contains(typed) } }
            .partition { it.name.lowercase().startsWith(typed) }
    return (starts.sortedBy { it.name.length } + rest).take(COMMANDS_SHOWN)
}

/** The commands that fit what is typed, above the composer; the keys pick one (Composer). */
@Composable
internal fun SlashCommandMenu(
    matches: List<SlashCommand>,
    selected: Int,
    onPick: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            matches.forEachIndexed { index, command ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (index == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(command) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "/" + command.name + (if (command.argumentHint.isNotBlank()) " " + command.argumentHint else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        command.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The model the chat runs, as the CLI names it ("Opus 5.5"), or what the CLI said it is. */
internal fun modelLabel(state: ClaudeChatState): String? {
    val choice =
        state.models.firstOrNull { it.value == state.modelChoice }
            ?: state.models.firstOrNull { state.modelChoice == null && it.resolvedModel != null && it.resolvedModel == state.model && it.value != "default" }
            ?: state.models.firstOrNull { state.modelChoice == null && it.value == "default" }
    return choice?.displayName?.substringBefore(" (") ?: state.model
}

/** Which model the next message goes to: a chip under the composer, with the CLI's own list. */
@Composable
internal fun ModelChip(
    state: ClaudeChatState,
    onModel: (String?) -> Unit,
) {
    val label = modelLabel(state) ?: return
    if (state.models.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.grayText
    Box {
        HoverTooltip(stringRes(Res.string.claude_code_model_tooltip)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { open = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Assistant, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection {
                state.models.forEach { option: ModelChoice ->
                    val selected = option.value == (state.modelChoice ?: "default")
                    MenuRow(title = option.displayName, description = option.description, selected = selected) {
                        open = false
                        onModel(option.value.takeIf { it != "default" })
                    }
                }
            }
        }
    }
}
