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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.desktop.SearchHistoryStore
import com.vitorpamplona.amethyst.desktop.ui.theme.hoverHighlight

@Composable
fun SearchSpotlight(
    onSelectProfile: (String) -> Unit,
    onSelectNote: (String) -> Unit,
    onSelectHashtag: (String) -> Unit,
    onOpenFullSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val history by SearchHistoryStore.history.collectAsState()
    val savedSearches by SearchHistoryStore.savedSearches.collectAsState()

    val hasQuery = textFieldValue.text.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        // Full-screen scrim + centered card
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onDismiss()
                    },
            contentAlignment = Alignment.TopCenter,
        ) {
            // Search card — offset 15% from top
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier =
                    Modifier
                        .padding(top = 80.dp)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(0.9f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // consume clicks so they don't dismiss
                        },
            ) {
                Column(
                    modifier =
                        Modifier
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                    onDismiss()
                                    true
                                } else {
                                    false
                                }
                            },
                ) {
                    // Search input
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(
                            MaterialSymbols.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            "Search notes, profiles, hashtags...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Results or history
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                    ) {
                        if (!hasQuery) {
                            // Recent searches
                            if (history.isNotEmpty()) {
                                item {
                                    Text(
                                        "Recent",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                                    )
                                }
                                items(history.take(5)) { query ->
                                    val text = QuerySerializer.serialize(query)
                                    SpotlightRow(
                                        icon = { Icon(MaterialSymbols.History, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        text = text,
                                        onClick = {
                                            textFieldValue = TextFieldValue(text, TextRange(text.length))
                                        },
                                    )
                                }
                            }

                            // Saved searches
                            if (savedSearches.isNotEmpty()) {
                                item {
                                    Text(
                                        "Saved",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                                    )
                                }
                                items(savedSearches.take(5)) { saved ->
                                    SpotlightRow(
                                        icon = { Icon(MaterialSymbols.Bookmark, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                                        text = saved.label,
                                        onClick = { onOpenFullSearch(QuerySerializer.serialize(saved.query)) },
                                    )
                                }
                            }

                            if (history.isEmpty() && savedSearches.isEmpty()) {
                                item {
                                    Text(
                                        "Type to search notes, profiles, and hashtags",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        } else {
                            // Placeholder for live results — Phase 2 will wire AdvancedSearchBarState
                            item {
                                SpotlightRow(
                                    icon = { Icon(MaterialSymbols.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    text = "Search for \"${textFieldValue.text}\"",
                                    onClick = { onOpenFullSearch(textFieldValue.text) },
                                )
                            }

                            item {
                                SpotlightRow(
                                    icon = { Icon(MaterialSymbols.Tag, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    text = "#${textFieldValue.text.removePrefix("#")}",
                                    onClick = { onSelectHashtag(textFieldValue.text.removePrefix("#")) },
                                )
                            }

                            // "Open full search" at bottom
                            item {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                SpotlightRow(
                                    icon = {
                                        Icon(
                                            MaterialSymbols.AutoMirrored.OpenInNew,
                                            null,
                                            Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    text = "Open full search",
                                    textColor = MaterialTheme.colorScheme.primary,
                                    onClick = { onOpenFullSearch(textFieldValue.text) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SpotlightRow(
    icon: @Composable () -> Unit,
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .hoverHighlight()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
        )
    }
}
