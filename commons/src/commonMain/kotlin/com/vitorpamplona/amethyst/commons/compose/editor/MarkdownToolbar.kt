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
package com.vitorpamplona.amethyst.commons.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown toolbar with Material icons, grouped formatting buttons, and active state.
 * Uses [MarkdownEditorState] for selection-aware toggle behavior.
 *
 * Buttons use `focusProperties { canFocus = false }` to prevent stealing focus
 * from the editor TextField, preserving the user's text selection.
 */
@Composable
fun MarkdownToolbar(
    state: MarkdownEditorState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // --- Headings ---
        ToolbarButton(label = "H1", active = state.headingLevel == 1) { state.setHeading(if (state.headingLevel == 1) null else 1) }
        ToolbarButton(label = "H2", active = state.headingLevel == 2) { state.setHeading(if (state.headingLevel == 2) null else 2) }
        ToolbarButton(label = "H3", active = state.headingLevel == 3) { state.setHeading(if (state.headingLevel == 3) null else 3) }

        Separator()

        // --- Inline formatting ---
        ToolbarIconButton(Icons.Default.FormatBold, "Bold", state.isBold) { state.toggleBold() }
        ToolbarIconButton(Icons.Default.FormatItalic, "Italic", state.isItalic) { state.toggleItalic() }
        ToolbarIconButton(Icons.Default.FormatStrikethrough, "Strikethrough", state.isStrikethrough) { state.toggleStrikethrough() }
        ToolbarIconButton(Icons.Default.Code, "Inline code", state.isInlineCode) { state.toggleInlineCode() }

        Separator()

        // --- Lists ---
        ToolbarIconButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet list", state.isUnorderedList) { state.toggleUnorderedList() }
        ToolbarIconButton(Icons.Default.FormatListNumbered, "Numbered list", state.isOrderedList) { state.toggleOrderedList() }
        ToolbarIconButton(Icons.Default.Checklist, "Task list", state.isTaskList) { state.toggleTaskList() }

        Separator()

        // --- Block elements ---
        ToolbarIconButton(Icons.Default.FormatQuote, "Blockquote", state.isBlockquote) { state.toggleBlockquote() }
        ToolbarButton(label = "```", active = false) { state.toggleCodeBlock() }
        ToolbarIconButton(Icons.Default.HorizontalRule, "Horizontal rule", false) { state.insertHorizontalRule() }

        Separator()

        // --- Insert ---
        ToolbarIconButton(Icons.Default.Link, "Link", false) { state.insertLink() }
        ToolbarIconButton(Icons.Default.Image, "Image", false) { state.insertImage() }
    }
}

@Composable
private fun Separator() {
    VerticalDivider(
        modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier =
            Modifier
                .size(32.dp)
                .focusProperties { canFocus = false },
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier =
            Modifier
                .size(32.dp)
                .focusProperties { canFocus = false },
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = contentColor,
        )
    }
}
