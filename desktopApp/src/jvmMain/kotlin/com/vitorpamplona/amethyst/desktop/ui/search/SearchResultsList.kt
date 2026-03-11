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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.search.AdvancedSearchBarState
import com.vitorpamplona.amethyst.commons.search.KindRegistry
import com.vitorpamplona.amethyst.commons.ui.components.UserSearchCard
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent

@Composable
fun SearchResultsList(
    state: AdvancedSearchBarState,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val people by state.peopleResults.collectAsState()
    val notes by state.noteResults.collectAsState()
    val isSearching by state.isSearching.collectAsState()

    val hasResults = people.isNotEmpty() || notes.isNotEmpty()

    if (!hasResults && isSearching) {
        Text(
            "Searching relays...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }

    if (!hasResults) return

    // Group notes by kind
    val textNotes = notes.filter { it.kind == 1 }
    val articles = notes.filter { it.kind == LongTextNoteEvent.KIND }
    val otherNotes = notes.filter { it.kind != 1 && it.kind != LongTextNoteEvent.KIND }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // People section
        if (people.isNotEmpty()) {
            stickyHeader(key = "header-people") {
                SectionHeader("People", people.size, Icons.Default.Person)
            }
            val displayPeople = people.take(5)
            items(displayPeople, key = { "person-${it.pubkeyHex}" }) { user ->
                UserSearchCard(
                    user = user,
                    onClick = { onNavigateToProfile(user.pubkeyHex) },
                )
            }
            if (people.size > 5) {
                item(key = "people-expand") {
                    ExpandableSection(
                        remaining = people.drop(5),
                    ) { user ->
                        UserSearchCard(
                            user = user,
                            onClick = { onNavigateToProfile(user.pubkeyHex) },
                        )
                    }
                }
            }
        }

        // Notes section
        if (textNotes.isNotEmpty()) {
            if (people.isNotEmpty()) {
                item(key = "divider-notes") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            }
            stickyHeader(key = "header-notes") {
                SectionHeader("Notes", textNotes.size, Icons.Default.Description)
            }
            val displayNotes = textNotes.take(5)
            items(displayNotes, key = { "note-${it.id}" }) { event ->
                NotePreviewCard(event = event, onClick = { onNavigateToThread(event.id) })
            }
            if (textNotes.size > 5) {
                item(key = "notes-expand") {
                    ExpandableSection(
                        remaining = textNotes.drop(5),
                    ) { event ->
                        NotePreviewCard(event = event, onClick = { onNavigateToThread(event.id) })
                    }
                }
            }
        }

        // Articles section
        if (articles.isNotEmpty()) {
            if (people.isNotEmpty() || textNotes.isNotEmpty()) {
                item(key = "divider-articles") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            }
            stickyHeader(key = "header-articles") {
                SectionHeader("Articles", articles.size, Icons.Default.Article)
            }
            items(articles.take(5), key = { "article-${it.id}" }) { event ->
                NotePreviewCard(event = event, onClick = { onNavigateToThread(event.id) })
            }
            if (articles.size > 5) {
                item(key = "articles-expand") {
                    ExpandableSection(
                        remaining = articles.drop(5),
                    ) { event ->
                        NotePreviewCard(event = event, onClick = { onNavigateToThread(event.id) })
                    }
                }
            }
        }

        // Other section
        if (otherNotes.isNotEmpty()) {
            item(key = "divider-other") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            stickyHeader(key = "header-other") {
                SectionHeader("Other", otherNotes.size, Icons.Default.Forum)
            }
            items(otherNotes.take(5), key = { "other-${it.id}" }) { event ->
                NotePreviewCard(event = event, onClick = { onNavigateToThread(event.id) })
            }
        }

        // Bottom padding
        item(key = "bottom-spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    icon: ImageVector,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "$title ($count)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotePreviewCard(
    event: Event,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Kind badge
                val kindName = KindRegistry.nameFor(event.kind) ?: "kind ${event.kind}"
                Text(
                    kindName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Author (hex truncated)
                Text(
                    event.pubKey.take(8) + "..." + event.pubKey.takeLast(4),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // Timestamp
                Text(
                    event.createdAt.toTimeAgo(withDot = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            // Content preview
            Text(
                event.content.take(200),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun <T> ExpandableSection(
    remaining: List<T>,
    content: @Composable (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            remaining.forEach { item ->
                content(item)
            }
        }
    } else {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Show all ${remaining.size} more")
        }
    }
}
