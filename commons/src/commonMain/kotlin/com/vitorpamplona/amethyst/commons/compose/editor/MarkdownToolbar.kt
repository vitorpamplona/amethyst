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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Toolbar for inserting markdown formatting at cursor position.
 * Each button calls [onInsert] with the prefix and suffix to wrap around selection.
 */
@Composable
fun MarkdownToolbar(
    onInsert: (prefix: String, suffix: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToolbarButton(label = "B", fontWeight = FontWeight.Bold) {
            onInsert("**", "**")
        }
        ToolbarButton(label = "I") {
            onInsert("*", "*")
        }
        ToolbarButton(label = "H") {
            onInsert("## ", "")
        }
        ToolbarButton(label = "[]") {
            onInsert("[", "](url)")
        }
        ToolbarButton(label = "img") {
            onInsert("![alt](", ")")
        }
        ToolbarButton(label = "<>") {
            onInsert("```\n", "\n```")
        }
        ToolbarButton(label = ">") {
            onInsert("> ", "")
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    fontWeight: FontWeight = FontWeight.Normal,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick) {
        Text(
            text = label,
            fontWeight = fontWeight,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
