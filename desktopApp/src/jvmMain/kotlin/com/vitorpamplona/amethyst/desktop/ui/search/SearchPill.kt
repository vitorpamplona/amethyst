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
package com.vitorpamplona.amethyst.desktop.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.desktop.SearchHistoryStore
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.amethyst.desktop.ui.theme.hoverHighlight

@Composable
fun SearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortcutHint = if (PlatformInfo.isMacOS) "\u2318F" else "Ctrl+F"
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val history by SearchHistoryStore.history.collectAsState()
    val savedSearches by SearchHistoryStore.savedSearches.collectAsState()

    Surface(
        onClick = { expanded = true },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(36.dp).hoverHighlight(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                MaterialSymbols.Search,
                contentDescription = "Search",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))

            if (expanded) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                    expanded = false
                                    textFieldValue = TextFieldValue("")
                                    focusManager.clearFocus()
                                    true
                                } else {
                                    false
                                }
                            },
                    textStyle =
                        MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    "Search notes, profiles...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                Text(
                    "Search...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.width(8.dp))
            Text(
                shortcutHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }

    // Dropdown below the pill when expanded
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            textFieldValue = TextFieldValue("")
        },
    ) {
        if (history.isNotEmpty()) {
            Text(
                "Recent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            history.take(5).forEach { query ->
                val text = QuerySerializer.serialize(query)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                textFieldValue = TextFieldValue(text, TextRange(text.length))
                            }.hoverHighlight()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(MaterialSymbols.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }

        if (savedSearches.isNotEmpty()) {
            if (history.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            Text(
                "Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            savedSearches.take(5).forEach { saved ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onClick() }
                            .hoverHighlight()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(MaterialSymbols.Bookmark, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(saved.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }

        if (history.isEmpty() && savedSearches.isEmpty()) {
            Text(
                "Type to search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .hoverHighlight()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(MaterialSymbols.AutoMirrored.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Open full search", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (expanded) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}
