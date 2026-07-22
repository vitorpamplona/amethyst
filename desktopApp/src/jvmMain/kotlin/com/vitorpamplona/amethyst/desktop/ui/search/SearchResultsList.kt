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

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.moderation.LocalSpamExemptKeys
import com.vitorpamplona.amethyst.commons.search.AdvancedSearchBarState
import com.vitorpamplona.amethyst.commons.search.SearchSortOrder
import com.vitorpamplona.amethyst.commons.ui.components.UserSearchCard
import com.vitorpamplona.amethyst.commons.wot.LocalWoTReady
import com.vitorpamplona.amethyst.commons.wot.LocalWoTService
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.note.DesktopPollCard
import com.vitorpamplona.amethyst.desktop.ui.note.NoteCard
import com.vitorpamplona.amethyst.desktop.ui.note.SpamCheckedNoteRender
import com.vitorpamplona.amethyst.desktop.ui.note.WoTBadge
import com.vitorpamplona.amethyst.desktop.ui.rememberDisplayData
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

@Composable
fun SearchResultsList(
    state: AdvancedSearchBarState,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    localCache: DesktopLocalCache? = null,
    relayManager: DesktopRelayConnectionManager? = null,
    account: AccountState.LoggedIn? = null,
    myPubKeyHex: String? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val people by state.sortedPeopleResults.collectAsState()
    val notes by state.sortedNoteResults.collectAsState()
    val eventSortOrder by state.eventSortOrder.collectAsState()
    val peopleSortOrder by state.peopleSortOrder.collectAsState()

    val hasResults = people.isNotEmpty() || notes.isNotEmpty()

    if (!hasResults) return

    // Group notes by kind
    val textNotes = notes.filter { it.kind == 1 }
    val articles = notes.filter { it.kind == LongTextNoteEvent.KIND }
    val polls = notes.filter { it.kind == PollEvent.KIND }
    val otherNotes = notes.filter { it.kind != 1 && it.kind != LongTextNoteEvent.KIND && it.kind != PollEvent.KIND }

    // Per-section collapsed state (absent = expanded)
    val collapsedSections = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // People section
        if (people.isNotEmpty()) {
            val collapsed = collapsedSections["people"] == true
            stickyHeader(key = "header-people") {
                SortableHeader(
                    title = "People",
                    count = people.size,
                    icon = MaterialSymbols.Person,
                    options = SearchSortOrder.PEOPLE_OPTIONS,
                    selected = peopleSortOrder,
                    onSelect = { state.updatePeopleSortOrder(it) },
                    collapsed = collapsed,
                    onToggleCollapse = { collapsedSections["people"] = !collapsed },
                )
            }
            if (!collapsed) {
                val displayPeople = people.take(5)
                items(displayPeople, key = { "person-${it.pubkeyHex}" }) { user ->
                    UserSearchCard(
                        user = user,
                        onClick = { onNavigateToProfile(user.pubkeyHex) },
                        badge = wotBadgeFor(user.pubkeyHex),
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
                                badge = wotBadgeFor(user.pubkeyHex),
                            )
                        }
                    }
                }
            }
        }

        // Notes section
        if (textNotes.isNotEmpty()) {
            if (people.isNotEmpty()) {
                item(key = "divider-notes") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            }
            val collapsed = collapsedSections["notes"] == true
            stickyHeader(key = "header-notes") {
                SortableHeader(
                    title = "Notes",
                    count = textNotes.size,
                    icon = MaterialSymbols.Description,
                    options = SearchSortOrder.EVENT_OPTIONS,
                    selected = eventSortOrder,
                    onSelect = { state.updateEventSortOrder(it) },
                    collapsed = collapsed,
                    onToggleCollapse = { collapsedSections["notes"] = !collapsed },
                )
            }
            if (!collapsed) {
                val displayNotes = textNotes.take(5)
                items(displayNotes, key = { "note-${it.id}" }) { event ->
                    SpamCheckedNoteRender(
                        displayedEvent = event,
                        noteIdHex = event.id,
                        localCache = localCache,
                    ) {
                        NoteCard(
                            note = event.rememberDisplayData(localCache),
                            localCache = localCache,
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                            onMentionClick = onNavigateToProfile,
                        )
                    }
                }
                if (textNotes.size > 5) {
                    item(key = "notes-expand") {
                        ExpandableSection(
                            remaining = textNotes.drop(5),
                        ) { event ->
                            SpamCheckedNoteRender(
                                displayedEvent = event,
                                noteIdHex = event.id,
                                localCache = localCache,
                            ) {
                                NoteCard(
                                    note = event.rememberDisplayData(localCache),
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Articles section
        if (articles.isNotEmpty()) {
            if (people.isNotEmpty() || textNotes.isNotEmpty()) {
                item(key = "divider-articles") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            }
            val collapsed = collapsedSections["articles"] == true
            stickyHeader(key = "header-articles") {
                SortableHeader(
                    title = "Articles",
                    count = articles.size,
                    icon = MaterialSymbols.AutoMirrored.Article,
                    options = SearchSortOrder.EVENT_OPTIONS,
                    selected = eventSortOrder,
                    onSelect = { state.updateEventSortOrder(it) },
                    collapsed = collapsed,
                    onToggleCollapse = { collapsedSections["articles"] = !collapsed },
                )
            }
            if (!collapsed) {
                items(articles.take(5), key = { "article-${it.id}" }) { event ->
                    SpamCheckedNoteRender(
                        displayedEvent = event,
                        noteIdHex = event.id,
                        localCache = localCache,
                    ) {
                        NoteCard(
                            note = event.rememberDisplayData(localCache),
                            localCache = localCache,
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                            onMentionClick = onNavigateToProfile,
                        )
                    }
                }
                if (articles.size > 5) {
                    item(key = "articles-expand") {
                        ExpandableSection(
                            remaining = articles.drop(5),
                        ) { event ->
                            SpamCheckedNoteRender(
                                displayedEvent = event,
                                noteIdHex = event.id,
                                localCache = localCache,
                            ) {
                                NoteCard(
                                    note = event.rememberDisplayData(localCache),
                                    onClick = { onNavigateToThread(event.id) },
                                    onAuthorClick = onNavigateToProfile,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Polls section (interactive cards — read tallies + vote)
        if (polls.isNotEmpty()) {
            item(key = "divider-polls") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            val collapsed = collapsedSections["polls"] == true
            stickyHeader(key = "header-polls") {
                SortableHeader(
                    title = "Polls",
                    count = polls.size,
                    icon = MaterialSymbols.Poll,
                    options = SearchSortOrder.EVENT_OPTIONS,
                    selected = eventSortOrder,
                    onSelect = { state.updateEventSortOrder(it) },
                    collapsed = collapsed,
                    onToggleCollapse = { collapsedSections["polls"] = !collapsed },
                )
            }
            if (!collapsed) {
                items(polls.take(5), key = { "poll-${it.id}" }) { event ->
                    PollSearchItem(
                        event = event as PollEvent,
                        localCache = localCache,
                        relayManager = relayManager,
                        account = account,
                        myPubKeyHex = myPubKeyHex,
                        onNavigateToThread = onNavigateToThread,
                        onNavigateToProfile = onNavigateToProfile,
                        onHashtagClick = onHashtagClick,
                    )
                }
                if (polls.size > 5) {
                    item(key = "polls-expand") {
                        ExpandableSection(
                            remaining = polls.drop(5),
                        ) { event ->
                            PollSearchItem(
                                event = event as PollEvent,
                                localCache = localCache,
                                relayManager = relayManager,
                                account = account,
                                myPubKeyHex = myPubKeyHex,
                                onNavigateToThread = onNavigateToThread,
                                onNavigateToProfile = onNavigateToProfile,
                                onHashtagClick = onHashtagClick,
                            )
                        }
                    }
                }
            }
        }

        // Other section
        if (otherNotes.isNotEmpty()) {
            item(key = "divider-other") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            val collapsed = collapsedSections["other"] == true
            stickyHeader(key = "header-other") {
                SortableHeader(
                    title = "Other",
                    count = otherNotes.size,
                    icon = MaterialSymbols.Forum,
                    options = SearchSortOrder.EVENT_OPTIONS,
                    selected = eventSortOrder,
                    onSelect = { state.updateEventSortOrder(it) },
                    collapsed = collapsed,
                    onToggleCollapse = { collapsedSections["other"] = !collapsed },
                )
            }
            if (!collapsed) {
                items(otherNotes.take(5), key = { "other-${it.id}" }) { event ->
                    SpamCheckedNoteRender(
                        displayedEvent = event,
                        noteIdHex = event.id,
                        localCache = localCache,
                    ) {
                        NoteCard(
                            note = event.rememberDisplayData(localCache),
                            localCache = localCache,
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                            onMentionClick = onNavigateToProfile,
                        )
                    }
                }
            }
        }

        // Bottom padding
        item(key = "bottom-spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * Returns a WoT-badge lambda for the given pubkey, or null when the
 * badge should be hidden. Same gates as [WoTBadgedAvatar]:
 *   - WoT service is provided
 *   - initial batch fetch has finished (or 2 s startup timeout fired)
 *   - the pubkey is not exempt (self or already followed)
 *   - the score is > 0
 * Inlined here (rather than wrapped in a new composable) because it's
 * only used at the two SearchResultsList person-result call sites.
 */
@Composable
private fun wotBadgeFor(userHex: String): (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? {
    val service = LocalWoTService.current
    val ready = LocalWoTReady.current
    val exempt = LocalSpamExemptKeys.current
    val score =
        if (service != null && ready && userHex !in exempt) {
            service.scores[userHex] ?: 0
        } else {
            0
        }
    return if (score > 0) {
        { WoTBadge(count = score, modifier = Modifier.align(Alignment.BottomEnd)) }
    } else {
        null
    }
}

@Composable
private fun PollSearchItem(
    event: PollEvent,
    localCache: DesktopLocalCache?,
    relayManager: DesktopRelayConnectionManager?,
    account: AccountState.LoggedIn?,
    myPubKeyHex: String?,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onHashtagClick: ((String) -> Unit)?,
) {
    SpamCheckedNoteRender(
        displayedEvent = event,
        noteIdHex = event.id,
        localCache = localCache,
    ) {
        if (localCache != null && relayManager != null) {
            // Interactive: read tallies + vote. Note is resolved from the cache; option
            // rendering comes from the event, so an empty (unconsumed) note still renders.
            DesktopPollCard(
                note = localCache.getOrCreateNote(event.id),
                event = event,
                relayManager = relayManager,
                localCache = localCache,
                account = account,
                myPubKeyHex = myPubKeyHex,
                onNavigateToThread = onNavigateToThread,
                onNavigateToProfile = onNavigateToProfile,
                onHashtagClick = onHashtagClick,
            )
        } else {
            // Read-only fallback when the live cache/relay manager isn't available.
            NoteCard(
                note = event.rememberDisplayData(localCache),
                localCache = localCache,
                onClick = { onNavigateToThread(event.id) },
                onAuthorClick = onNavigateToProfile,
                onMentionClick = onNavigateToProfile,
            )
        }
    }
}

@Composable
private fun SortableHeader(
    title: String,
    count: Int,
    icon: MaterialSymbol,
    options: List<SearchSortOrder>,
    selected: SearchSortOrder,
    onSelect: (SearchSortOrder) -> Unit,
    collapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
) {
    val chevronRotation by animateFloatAsState(if (collapsed) -90f else 0f)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onToggleCollapse).padding(vertical = 4.dp),
        ) {
            Icon(
                MaterialSymbols.ExpandMore,
                contentDescription = if (collapsed) "Expand $title" else "Collapse $title",
                modifier = Modifier.size(18.dp).rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "$title ($count)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            if (!collapsed) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            label = {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }
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
            Icon(MaterialSymbols.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Show all ${remaining.size} more")
        }
    }
}
