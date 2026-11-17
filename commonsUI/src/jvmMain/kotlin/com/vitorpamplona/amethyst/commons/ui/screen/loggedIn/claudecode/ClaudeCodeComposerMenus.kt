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
import androidx.compose.foundation.layout.widthIn
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
import com.vitorpamplona.amethyst.commons.resources.claude_code_model_default_named
import com.vitorpamplona.amethyst.commons.resources.claude_code_model_other
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

/** How many models the menu lists before the rest fold under "Other models". */
private const val MODELS_SHOWN = 6

/** The model the chat runs, as the CLI names it ("Opus 5.5"), or what the CLI said it is. */
internal fun currentModel(state: ClaudeChatState): ModelChoice? =
    state.models.firstOrNull { it.value == state.modelChoice }
        ?: state.models.firstOrNull { state.modelChoice == null && it.resolvedModel != null && it.resolvedModel == state.model && it.value != DEFAULT }
        ?: state.models.firstOrNull { state.modelChoice == null && it.value == DEFAULT }

private const val DEFAULT = "default"

/**
 * The model a choice runs, by name: its own, or for "default" the model it stands for now, as
 * another choice names it or as its description begins ("Opus 5.5 · Best for…").
 */
internal fun modelName(
    choice: ModelChoice,
    models: List<ModelChoice>,
): String? {
    if (choice.value != DEFAULT) return choice.displayName.substringBefore(" (")
    return models.firstOrNull { it.value != DEFAULT && it.resolvedModel != null && it.resolvedModel == choice.resolvedModel }?.displayName?.substringBefore(" (")
        ?: choice.description
            .substringBefore(" · ")
            .trim()
            .takeIf { it.isNotEmpty() && it.length <= 24 && it != choice.description }
}

/** "Default (Opus 5.5)" for the default choice, the model's name for the others: on the chip and in the menu alike. */
@Composable
internal fun modelTitle(
    choice: ModelChoice,
    models: List<ModelChoice>,
): String {
    val name = modelName(choice, models)
    return when {
        choice.value != DEFAULT -> name ?: choice.displayName
        name != null -> stringRes(Res.string.claude_code_model_default_named, name)
        else -> choice.displayName.substringBefore(" (")
    }
}

/**
 * The models the menu leads with: all of them when there are few; else the CLI's aliases that
 * follow releases (`opus`, `sonnet`: no version in the name), with the one in use.
 */
internal fun leadingModels(
    models: List<ModelChoice>,
    selected: String,
): List<ModelChoice> {
    if (models.size <= MODELS_SHOWN) return models
    val aliases = models.filter { m -> m.value.none { it.isDigit() } || m.value == selected }
    return if (aliases.size > 1) aliases else models.take(MODELS_SHOWN)
}

/** Which model the next message goes to: a chip under the composer, with the CLI's own list. */
@Composable
internal fun ModelChip(
    state: ClaudeChatState,
    onModel: (String?) -> Unit,
) {
    if (state.models.isEmpty()) return
    val label = currentModel(state)?.let { modelTitle(it, state.models) } ?: state.model ?: return
    var open by remember { mutableStateOf(false) }
    var showOthers by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.grayText
    Box {
        HoverTooltip(stringRes(Res.string.claude_code_model_tooltip)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        showOthers = false
                        open = true
                    }.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Assistant, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val selectedValue = state.modelChoice ?: DEFAULT
            val leading = leadingModels(state.models, selectedValue)
            val others = state.models - leading.toSet()

            @Composable
            fun Option(option: ModelChoice) {
                // The default's title already names its model: its description need not repeat it.
                val description = modelName(option, state.models)?.let { option.description.removePrefix("$it · ") } ?: option.description
                MenuRow(title = modelTitle(option, state.models), description = description.takeIf { it.isNotBlank() }, selected = option.value == selectedValue, descriptionMaxLines = 1) {
                    open = false
                    onModel(option.value.takeIf { it != DEFAULT })
                }
            }
            RoundedMenuSection {
                leading.forEach { Option(it) }
            }
            if (others.isNotEmpty()) {
                RoundedMenuSection {
                    // Dated and older models, folded: few people want them, and they are many.
                    Row(
                        Modifier
                            .widthIn(min = 220.dp, max = 340.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showOthers = !showOthers }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringRes(Res.string.claude_code_model_other), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText, modifier = Modifier.weight(1f))
                        Icon(if (showOthers) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
                    }
                    if (showOthers) others.forEach { Option(it) }
                }
            }
        }
    }
}
