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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuFirstRowInset
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuItemModifier
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier

private val MenuItemHeight = 36.dp

/** One row of the dropdown: an action, or a group of actions folded into a submenu. */
private sealed interface MenuEntry {
    class Single(
        val action: NoteAction,
    ) : MenuEntry

    class Submenu(
        val group: NoteActionGroup,
        val actions: List<NoteAction>,
    ) : MenuEntry
}

/**
 * Folds each run of consecutive actions that share a [NoteActionGroup] into one submenu. A group
 * with a single visible action stays a plain row: a submenu of one only adds a hover.
 */
private fun foldGroups(section: List<NoteAction>): List<MenuEntry> {
    val entries = mutableListOf<MenuEntry>()
    var i = 0
    while (i < section.size) {
        val group = section[i].group
        var end = i + 1
        if (group != null) {
            while (end < section.size && section[end].group == group) end++
        }
        if (group != null && end - i > 1) {
            entries.add(MenuEntry.Submenu(group, section.subList(i, end)))
        } else {
            entries.add(MenuEntry.Single(section[i]))
        }
        i = end
    }
    return entries
}

/**
 * The note actions as a dropdown attached to the ⋮ button, for large windows (the rail and
 * docked-drawer tiers), where a centered dialog covering the feed reads as a phone pattern.
 *
 * Renders the same [sections] the dialog and the chat long-press sheet do, each in a rounded group.
 * Actions of one [NoteActionGroup] fold into a submenu that opens on hover or click and closes
 * when the pointer moves to another row, so the full inventory fits under the button.
 */
@Composable
fun NoteActionDropdownMenu(
    sections: List<List<NoteAction>>,
    onDismiss: () -> Unit,
) {
    var openGroup by remember { mutableStateOf<NoteActionGroup?>(null) }

    RoundedDropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        sections.forEach { section ->
            RoundedMenuSection {
                foldGroups(section).forEach { entry ->
                    when (entry) {
                        is MenuEntry.Single -> {
                            ActionItem(
                                label = entry.action.label,
                                symbol = entry.action.symbol,
                                isDestructive = entry.action.isDestructive,
                                onHover = { openGroup = null },
                                onClick = entry.action.onClick,
                            )
                        }

                        is MenuEntry.Submenu -> {
                            SubmenuItem(
                                group = entry.group,
                                actions = entry.actions,
                                open = openGroup == entry.group,
                                onOpen = { openGroup = entry.group },
                                onClose = { if (openGroup == entry.group) openGroup = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmenuItem(
    group: NoteActionGroup,
    actions: List<NoteAction>,
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(Modifier.onSizeChanged { rowSize = it }) {
        ActionItem(
            label = stringRes(group.label),
            symbol = group.symbol,
            isDestructive = false,
            onHover = onOpen,
            onClick = onOpen,
            trailing = MaterialSymbols.AutoMirrored.KeyboardArrowRight,
        )

        // Offset by the row's width so it opens beside the row; the menu's positioning flips it to
        // the other side when there is no room, as a desktop submenu does near the window's edge.
        val offset =
            with(density) {
                DpOffset(rowSize.width.toDp(), -rowSize.height.toDp() - RoundedMenuFirstRowInset)
            }

        // Not focusable: a focusable popup takes the pointer, so hovering another row of the parent
        // menu would not reach it and the submenu could not switch or close. A click outside
        // still dismisses the parent menu, and this one with it.
        RoundedDropdownMenu(
            expanded = open,
            onDismissRequest = onClose,
            offset = offset,
            properties = PopupProperties(focusable = false),
        ) {
            RoundedMenuSection {
                actions.forEach { action ->
                    ActionItem(
                        label = action.label,
                        symbol = action.symbol,
                        isDestructive = action.isDestructive,
                        onHover = {},
                        onClick = action.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    label: String,
    symbol: MaterialSymbol,
    isDestructive: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
    trailing: MaterialSymbol? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val currentOnHover by rememberUpdatedState(onHover)
    LaunchedEffect(hovered) {
        if (hovered) currentOnHover()
    }

    val colors =
        if (isDestructive) {
            MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error,
            )
        } else {
            MenuDefaults.itemColors()
        }

    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        onClick = onClick,
        modifier = RoundedMenuItemModifier.height(MenuItemHeight),
        leadingIcon = { Icon(symbol = symbol, contentDescription = null, modifier = Size20Modifier) },
        trailingIcon =
            trailing?.let {
                { Icon(symbol = it, contentDescription = null, modifier = Size20Modifier) }
            },
        colors = colors,
        interactionSource = interactionSource,
    )
}
