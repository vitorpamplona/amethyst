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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.service.upload.CompressionQuality

/**
 * Per-post compression quality selector. Visually matches the
 * sibling [ServerSelector] and [PostTypeSelector]: a "Quality:" label
 * + a [TextButton] that opens a [DropdownMenu] of presets. Each
 * dropdown row carries a one-line summary (dimension + tradeoff).
 *
 * When the user has chosen something other than the saved default,
 * the dropdown shows a "Reset to default" row at the bottom so the
 * override can be cleared without sending a post.
 */
@Composable
fun QualitySelectorChip(
    activeQuality: CompressionQuality,
    isOverride: Boolean,
    onSelect: (CompressionQuality) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            "Quality: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(
                    activeQuality.chipLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                CompressionQuality.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    preset.chipLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (preset == activeQuality) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    preset.summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelect(preset)
                            expanded = false
                        },
                    )
                }
                if (isOverride) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Reset to default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            onReset()
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
