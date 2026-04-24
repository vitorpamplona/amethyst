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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.search.ContentPreset
import com.vitorpamplona.amethyst.commons.search.DateUtils
import com.vitorpamplona.amethyst.commons.search.KindRegistry
import com.vitorpamplona.amethyst.commons.search.QueryParser
import com.vitorpamplona.amethyst.commons.search.SearchQuery

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchPanel(
    query: SearchQuery,
    onKindsChanged: (List<Int>) -> Unit,
    onPseudoKindsChanged: (List<String>) -> Unit,
    onAuthorAdded: (String) -> Unit,
    onAuthorRemoved: (String) -> Unit,
    onDateRangeChanged: (Long?, Long?) -> Unit,
    onHashtagAdded: (String) -> Unit,
    onHashtagRemoved: (String) -> Unit,
    onExcludeAdded: (String) -> Unit,
    onExcludeRemoved: (String) -> Unit,
    onLanguageChanged: (String?) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Content type presets
            Text(
                "Content Type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KindRegistry.presets.forEach { (name, preset) ->
                    FilterChip(
                        selected = preset.isSelected(query.kinds.toList(), query.pseudoKinds.toList()),
                        onClick = { togglePreset(preset, query, onKindsChanged, onPseudoKindsChanged) },
                        label = { Text(name) },
                    )
                }
            }

            // Author field
            AuthorInputField(
                authors = query.authors.toList() + query.authorNames.toList(),
                onAuthorAdded = onAuthorAdded,
                onAuthorRemoved = onAuthorRemoved,
            )

            // Date range
            DateRangeFields(
                since = query.since,
                until = query.until,
                onChanged = onDateRangeChanged,
            )

            // Hashtags
            ChipGroupWithInput(
                label = "Tags",
                items = query.hashtags.toList(),
                prefix = "#",
                placeholder = "Add tag...",
                onAdd = onHashtagAdded,
                onRemove = onHashtagRemoved,
            )

            // Exclude terms
            ChipGroupWithInput(
                label = "Exclude",
                items = query.excludeTerms.toList(),
                prefix = "-",
                placeholder = "Exclude term...",
                onAdd = onExcludeAdded,
                onRemove = onExcludeRemoved,
            )

            // Language
            LanguageSelector(
                selected = query.language,
                onChanged = onLanguageChanged,
            )

            // Clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onClear) {
                    Text("Clear All")
                }
            }
        }
    }
}

private fun togglePreset(
    preset: ContentPreset,
    query: SearchQuery,
    onKindsChanged: (List<Int>) -> Unit,
    onPseudoKindsChanged: (List<String>) -> Unit,
) {
    val pseudo = preset.pseudoKind
    if (pseudo != null) {
        val current = query.pseudoKinds.toList()
        if (pseudo in current) {
            onPseudoKindsChanged(current - pseudo)
        } else {
            onPseudoKindsChanged(current + pseudo)
        }
    } else {
        val current = query.kinds.toList()
        if (current.containsAll(preset.kinds)) {
            onKindsChanged(current - preset.kinds.toSet())
        } else {
            onKindsChanged((current + preset.kinds).distinct())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorInputField(
    authors: List<String>,
    onAuthorAdded: (String) -> Unit,
    onAuthorRemoved: (String) -> Unit,
) {
    Column {
        Text(
            "Author",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (authors.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                authors.forEach { author ->
                    AssistChip(
                        onClick = { onAuthorRemoved(author) },
                        label = {
                            Text(
                                if (author.length > 16) author.take(8) + "..." + author.takeLast(4) else author,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingIcon = {
                            Icon(MaterialSymbols.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        var authorInput by remember { mutableStateOf("") }
        OutlinedTextField(
            value = authorInput,
            onValueChange = { authorInput = it },
            modifier =
                Modifier.fillMaxWidth().onKeyEvent {
                    if (it.key == Key.Enter && authorInput.isNotBlank()) {
                        onAuthorAdded(authorInput.trim())
                        authorInput = ""
                        true
                    } else {
                        false
                    }
                },
            placeholder = { Text("npub or name...") },
            singleLine = true,
            trailingIcon = {
                if (authorInput.isNotBlank()) {
                    IconButton(onClick = {
                        onAuthorAdded(authorInput.trim())
                        authorInput = ""
                    }) {
                        Icon(MaterialSymbols.Add, contentDescription = "Add author")
                    }
                }
            },
        )
    }
}

@Composable
private fun DateRangeFields(
    since: Long?,
    until: Long?,
    onChanged: (Long?, Long?) -> Unit,
) {
    // Local text is source of truth while typing.
    // Only propagate valid timestamps (or null when cleared).
    // Only sync from external when the timestamp changes to something we didn't produce.
    var sinceText by remember { mutableStateOf(since?.let { DateUtils.timestampToDate(it) } ?: "") }
    var lastSince by remember { mutableStateOf(since) }
    if (since != lastSince) {
        sinceText = since?.let { DateUtils.timestampToDate(it) } ?: ""
        lastSince = since
    }

    var untilText by remember { mutableStateOf(until?.let { DateUtils.timestampToDate(it) } ?: "") }
    var lastUntil by remember { mutableStateOf(until) }
    if (until != lastUntil) {
        untilText = until?.let { DateUtils.timestampToDate(it) } ?: ""
        lastUntil = until
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Since",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = sinceText,
                onValueChange = {
                    sinceText = it
                    val ts = QueryParser.parseDateToTimestamp(it)
                    if (ts != null || it.isBlank()) {
                        lastSince = ts
                        onChanged(ts, until)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Until",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = untilText,
                onValueChange = {
                    untilText = it
                    val ts = QueryParser.parseDateToTimestamp(it)
                    if (ts != null || it.isBlank()) {
                        lastUntil = ts
                        onChanged(since, ts)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroupWithInput(
    label: String,
    items: List<String>,
    prefix: String,
    placeholder: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                AssistChip(
                    onClick = { onRemove(item) },
                    label = { Text("$prefix$item", style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = {
                        Icon(MaterialSymbols.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                    },
                )
            }
        }
        if (items.isNotEmpty()) Spacer(Modifier.height(4.dp))
        var inputText by remember { mutableStateOf("") }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier =
                    Modifier.weight(1f).onKeyEvent {
                        if (it.key == Key.Enter && inputText.isNotBlank()) {
                            onAdd(inputText.trim())
                            inputText = ""
                            true
                        } else {
                            false
                        }
                    },
                placeholder = { Text(placeholder) },
                singleLine = true,
            )
            TextButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onAdd(inputText.trim())
                        inputText = ""
                    }
                },
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    selected: String?,
    onChanged: (String?) -> Unit,
) {
    val languages =
        listOf(
            null to "Any",
            "en" to "English",
            "es" to "Spanish",
            "pt" to "Portuguese",
            "ja" to "Japanese",
            "zh" to "Chinese",
            "de" to "German",
            "fr" to "French",
        )

    Column {
        Text(
            "Language",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            languages.forEach { (code, name) ->
                FilterChip(
                    selected = selected == code,
                    onClick = { onChanged(code) },
                    label = { Text(name) },
                )
            }
        }
    }
}
