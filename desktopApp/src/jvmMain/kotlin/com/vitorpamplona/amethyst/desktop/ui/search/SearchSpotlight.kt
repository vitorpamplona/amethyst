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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.commons.search.UserSearchEngine
import com.vitorpamplona.amethyst.commons.ui.search.SearchFieldState
import com.vitorpamplona.amethyst.commons.ui.search.TokenizedSearchField
import com.vitorpamplona.amethyst.commons.ui.search.rememberChipNames
import com.vitorpamplona.amethyst.commons.ui.search.rememberPersonCandidates
import com.vitorpamplona.amethyst.desktop.SearchHistoryStore
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.search.DesktopRelayUserSearchDelegate
import com.vitorpamplona.amethyst.desktop.ui.relay.LocalRelayCategories
import com.vitorpamplona.amethyst.desktop.ui.theme.hoverHighlight

@Composable
fun SearchSpotlight(
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    onSelectProfile: (String) -> Unit,
    onSelectNote: (String) -> Unit,
    onSelectHashtag: (String) -> Unit,
    onOpenFullSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val fieldState = remember { SearchFieldState() }

    // The people a half-written `from:`/`to:` token offers, and the names their finished
    // chips draw. Same engine the full search screen uses: cache hits first, then whatever
    // the account's search relays answer with.
    val searchRelays by LocalRelayCategories.current.searchRelays.collectAsState()
    val userSearch =
        remember(relayManager, localCache) {
            UserSearchEngine(localCache, scope).apply {
                relayDelegate = DesktopRelayUserSearchDelegate(relayManager, localCache, { searchRelays }, scope)
            }
        }
    val personCandidates = rememberPersonCandidates(userSearch)
    val chipNames = rememberChipNames(userSearch)

    val history by SearchHistoryStore.history.collectAsState()
    val savedSearches by SearchHistoryStore.savedSearches.collectAsState()

    val hasQuery = fieldState.text.isNotBlank()

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
                        // Top, not centre: the picker opens *below* the field inside the
                        // same column, and centring would drag the leading icon halfway
                        // down it. With no picker up the two are the same height, so the
                        // resting layout is unchanged.
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(
                            MaterialSymbols.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        // The tokenized field: `from:`/`to:`, `since:`/`until:`, `#tag`,
                        // `group:` and the NIP-73 scopes draw as chips here and become filter
                        // fields downstream, so what the box shows is what the REQ asks for.
                        TokenizedSearchField(
                            state = fieldState,
                            modifier = Modifier.weight(1f),
                            fieldModifier = Modifier.focusRequester(focusRequester),
                            placeholder = "Search notes, profiles, hashtags...",
                            people = personCandidates,
                            displayName = chipNames,
                            onPeopleQuery = { userSearch.search(it) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            onSubmit = { onOpenFullSearch(fieldState.text) },
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
                                            fieldState.setText(text)
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
                                    text = "Search for \"${fieldState.text}\"",
                                    onClick = { onOpenFullSearch(fieldState.text) },
                                )
                            }

                            item {
                                SpotlightRow(
                                    icon = { Icon(MaterialSymbols.Tag, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    text = "#${fieldState.text.removePrefix("#")}",
                                    onClick = { onSelectHashtag(fieldState.text.removePrefix("#")) },
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
                                    onClick = { onOpenFullSearch(fieldState.text) },
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
