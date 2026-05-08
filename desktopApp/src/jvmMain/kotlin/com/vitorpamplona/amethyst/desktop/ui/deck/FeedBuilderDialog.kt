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
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedBuilderState
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.custom.RefreshMode
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedBuilderDialog(
    initial: FeedDefinition? = null,
    localCache: DesktopLocalCache? = null,
    authorQuery: String = "",
    onAuthorQueryChange: (String) -> Unit = {},
    authorSuggestions: List<com.vitorpamplona.amethyst.commons.model.User> = emptyList(),
    authorRelayResults: List<com.vitorpamplona.amethyst.commons.model.User> = emptyList(),
    authorSearching: Boolean = false,
    onSave: (FeedDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember(initial) { FeedBuilderState(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier =
            Modifier.padding(horizontal = 32.dp, vertical = 32.dp).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.S &&
                    (event.isMetaPressed || event.isCtrlPressed)
                ) {
                    if (state.isValid) onSave(state.toDefinition())
                    true
                } else {
                    false
                }
            },
        title = { Text(if (initial != null) "Edit Feed" else "Create Feed") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Name + emoji
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.emoji,
                        onValueChange = { state.emoji = it.take(2) },
                        label = { Text("Icon") },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { state.name = it },
                        label = { Text("Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                // Authors (with search + npub decode)
                AuthorInputSection(
                    authors = state.authors,
                    localCache = localCache,
                    query = authorQuery,
                    onQueryChange = onAuthorQueryChange,
                    suggestions = authorSuggestions,
                    relayResults = authorRelayResults,
                    isSearching = authorSearching,
                    onAdd = { hex ->
                        if (hex !in state.authors) state.authors.add(hex)
                        onAuthorQueryChange("")
                    },
                    onRemove = { state.authors.remove(it) },
                )

                // Hashtags
                ChipInputField(
                    label = "Hashtags",
                    items = state.hashtags,
                    placeholder = "Add hashtag...",
                    onAdd = { state.hashtags.add(it.removePrefix("#").lowercase()) },
                    onRemove = { state.hashtags.remove(it) },
                )

                // Relays
                ChipInputField(
                    label = "Relays",
                    items = state.relays,
                    placeholder = "wss://...",
                    onAdd = { state.relays.add(it) },
                    onRemove = { state.relays.remove(it) },
                )

                // Kind filter
                KindFilterSection(kinds = state.kinds)

                // Exclude authors
                if (localCache != null) {
                    AuthorInputSection(
                        label = "Exclude Authors",
                        authors = state.excludeAuthors,
                        localCache = localCache,
                        onAdd = { hex -> if (hex !in state.excludeAuthors) state.excludeAuthors.add(hex) },
                        onRemove = { state.excludeAuthors.remove(it) },
                    )
                }

                // Exclude keywords
                ChipInputField(
                    label = "Exclude keywords",
                    items = state.excludeKeywords,
                    placeholder = "Add keyword to exclude...",
                    onAdd = { state.excludeKeywords.add(it) },
                    onRemove = { state.excludeKeywords.remove(it) },
                )

                // Refresh mode
                Text("Refresh", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RefreshMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.refreshMode == mode,
                            onClick = { state.refreshMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index, RefreshMode.entries.size),
                        ) {
                            Text(
                                when (mode) {
                                    RefreshMode.LIVE_STREAM -> "Live"
                                    RefreshMode.POLL_5MIN -> "Every 5 min"
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(state.toDefinition()) },
                enabled = state.isValid,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// -- Author search field with npub decode + profile lookup --

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorInputSection(
    label: String = "Authors",
    authors: List<String>,
    localCache: DesktopLocalCache?,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    suggestions: List<com.vitorpamplona.amethyst.commons.model.User> = emptyList(),
    relayResults: List<com.vitorpamplona.amethyst.commons.model.User> = emptyList(),
    isSearching: Boolean = false,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))

        if (authors.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                authors.forEach { hex ->
                    val user = localCache?.getUserIfExists(hex)
                    val displayName = user?.toBestDisplayName() ?: hex.take(12) + "..."
                    InputChip(
                        selected = true,
                        onClick = { onRemove(hex) },
                        label = { Text(displayName, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search name or paste npub...") },
            modifier =
                Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        if (query.isNotBlank()) {
                            val top =
                                suggestions.firstOrNull { it.pubkeyHex !in authors }
                                    ?: relayResults.firstOrNull { it.pubkeyHex !in authors }
                            if (top != null) {
                                onAdd(top.pubkeyHex)
                            } else {
                                onAdd(decodePublicKeyAsHexOrNull(query.trim()) ?: query.trim())
                            }
                        }
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
        )

        val filteredLocal = suggestions.filter { it.pubkeyHex !in authors }
        val filteredRelay =
            relayResults.filter { r ->
                r.pubkeyHex !in authors && filteredLocal.none { it.pubkeyHex == r.pubkeyHex }
            }

        val hasResults = filteredLocal.isNotEmpty() || filteredRelay.isNotEmpty()
        val showNoResults = query.length >= 2 && !hasResults && !isSearching

        if (hasResults || isSearching || showNoResults) {
            Spacer(Modifier.height(4.dp))
            Surface(
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            ) {
                LazyColumn {
                    items(filteredLocal, key = { "c-${it.pubkeyHex}" }) { user ->
                        AuthorRow(user) { onAdd(user.pubkeyHex) }
                    }
                    if (filteredRelay.isNotEmpty()) {
                        item {
                            Text(
                                "From relays",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        items(filteredRelay, key = { "r-${it.pubkeyHex}" }) { user ->
                            AuthorRow(user) { onAdd(user.pubkeyHex) }
                        }
                    }
                    if (isSearching) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Searching relays...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (showNoResults) {
                        item {
                            Text(
                                "No users found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorRow(
    user: com.vitorpamplona.amethyst.commons.model.User,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.vitorpamplona.amethyst.commons.ui.components.UserAvatar(
            userHex = user.pubkeyHex,
            pictureUrl = user.profilePicture(),
            size = 28.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.toBestDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                user.pubkeyNpub().take(20) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -- Kind filter checkboxes --

private data class KindOption(
    val label: String,
    val kinds: List<Int>,
)

private val KIND_OPTIONS =
    listOf(
        KindOption("Notes", listOf(1)),
        KindOption("Reposts", listOf(6, 16)),
        KindOption("Articles", listOf(30023)),
        KindOption("Highlights", listOf(9802)),
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindFilterSection(kinds: MutableList<Int>) {
    Column {
        Text("Event kinds", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KIND_OPTIONS.forEach { option ->
                val isSelected = option.kinds.any { it in kinds }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            kinds.removeAll(option.kinds)
                        } else {
                            kinds.addAll(option.kinds)
                        }
                    },
                    label = { Text(option.label) },
                )
            }
        }
        if (kinds.isEmpty()) {
            Text(
                "No filter = all kinds",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -- Simple chip input field (hashtags, relays, keywords) --

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipInputField(
    label: String,
    items: List<String>,
    placeholder: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        if (items.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(item) },
                        label = { Text(item, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        var input by remember { mutableStateOf("") }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(placeholder) },
            modifier =
                Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        if (input.isNotBlank()) {
                            onAdd(input.trim())
                            input = ""
                        }
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            trailingIcon = {
                if (input.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onAdd(input.trim())
                            input = ""
                        },
                    ) {
                        Text("Add")
                    }
                }
            },
        )
    }
}
