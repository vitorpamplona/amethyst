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
package com.vitorpamplona.amethyst.commons.ui.navigation.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.dock_forget_layout
import com.vitorpamplona.amethyst.commons.resources.dock_layout_name
import com.vitorpamplona.amethyst.commons.resources.dock_layouts
import com.vitorpamplona.amethyst.commons.resources.dock_pin_screen
import com.vitorpamplona.amethyst.commons.resources.dock_reset_layout
import com.vitorpamplona.amethyst.commons.resources.dock_save_layout
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * The quiet buttons at the foot of the dock strip: pin the current screen as a panel, and the
 * layouts menu (saved layouts to go back to, save this one, reset).
 */
@Composable
internal fun DockStripFooter(
    controller: DockController,
    onPinScreen: (() -> Unit)?,
) {
    if (onPinScreen != null) {
        StripAction(MaterialSymbols.Add, stringRes(Res.string.dock_pin_screen), onPinScreen)
    }
    Box {
        var open by remember { mutableStateOf(false) }
        StripAction(MaterialSymbols.Dashboard, stringRes(Res.string.dock_layouts)) { open = true }
        DockLayoutsMenu(controller, open) { open = false }
    }
}

/** A strip button for an action rather than a panel: the same faint look, no badge, no drag. */
@Composable
internal fun StripAction(
    symbol: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    HoverTooltip(label) {
        Box(
            Modifier
                .padding(vertical = 2.dp)
                .size(StripActionSize)
                .clip(RoundedCornerShape(8.dp))
                .background(if (hovered) colors.onSurface.copy(alpha = 0.05f) else Color.Transparent)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = symbol,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = if (hovered) colors.onSurface.copy(alpha = 0.85f) else colors.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

private val StripActionSize = 30.dp

/** The saved layouts, to restore or forget, then save this one and reset. */
@Composable
internal fun DockLayoutsMenu(
    controller: DockController,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    if (!expanded) return
    val saved by controller.savedLayouts.collectAsStateWithLifecycle()
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        saved.keys.sorted().forEach { name ->
            DropdownMenuItem(
                text = { Text(name) },
                leadingIcon = { Icon(symbol = MaterialSymbols.Dashboard, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    val forget = stringRes(Res.string.dock_forget_layout, name)
                    HoverTooltip(forget) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    controller.forgetLayout(name)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(symbol = MaterialSymbols.Close, contentDescription = forget, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                onClick = {
                    onDismiss()
                    controller.restoreLayout(name)
                },
            )
        }
        if (saved.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.dock_save_layout)) },
            leadingIcon = { Icon(symbol = MaterialSymbols.Save, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = {
                onDismiss()
                controller.namingLayout = true
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.dock_reset_layout)) },
            leadingIcon = { Icon(symbol = MaterialSymbols.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = {
                onDismiss()
                controller.reset()
            },
        )
    }
}

/** Asks for a name to save the current layout under, while [DockController.namingLayout] is set. */
@Composable
internal fun DockLayoutNameDialog(controller: DockController) {
    if (!controller.namingLayout) return
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val save = {
        if (name.isNotBlank()) {
            controller.saveLayoutAs(name)
            controller.namingLayout = false
        }
    }
    AlertDialog(
        onDismissRequest = { controller.namingLayout = false },
        title = { Text(stringRes(Res.string.dock_save_layout)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringRes(Res.string.dock_layout_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = save, enabled = name.isNotBlank()) { Text(stringRes(Res.string.save)) } },
        dismissButton = { TextButton(onClick = { controller.namingLayout = false }) { Text(stringRes(Res.string.cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
