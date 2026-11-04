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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.gitDashboard.GitDashboardRepository
import com.vitorpamplona.amethyst.commons.gitDashboard.GitDashboardState
import com.vitorpamplona.amethyst.commons.gitDashboard.GitDashboardViewModel
import com.vitorpamplona.amethyst.commons.gitDashboard.GitInboxEntry
import com.vitorpamplona.amethyst.commons.gitDashboard.GitInvolvement
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitShape
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitStateIcon
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitMetaStyle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitRepositoryNameOf
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitItemTitle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.statusKind
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.gitDashboard.GitDashboardQueryState
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.LoadGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.RepositoryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_continue_working
import com.vitorpamplona.amethyst.commons.resources.git_filter_repositories
import com.vitorpamplona.amethyst.commons.resources.git_followed_repositories
import com.vitorpamplona.amethyst.commons.resources.git_followed_repositories_empty
import com.vitorpamplona.amethyst.commons.resources.git_inbox
import com.vitorpamplona.amethyst.commons.resources.git_inbox_all
import com.vitorpamplona.amethyst.commons.resources.git_inbox_all_read
import com.vitorpamplona.amethyst.commons.resources.git_inbox_author
import com.vitorpamplona.amethyst.commons.resources.git_inbox_empty
import com.vitorpamplona.amethyst.commons.resources.git_inbox_mark_all_read
import com.vitorpamplona.amethyst.commons.resources.git_inbox_mentioned
import com.vitorpamplona.amethyst.commons.resources.git_inbox_new_count
import com.vitorpamplona.amethyst.commons.resources.git_inbox_participant
import com.vitorpamplona.amethyst.commons.resources.git_inbox_unread
import com.vitorpamplona.amethyst.commons.resources.git_inbox_watching
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_button
import com.vitorpamplona.amethyst.commons.resources.git_show_less
import com.vitorpamplona.amethyst.commons.resources.git_show_more
import com.vitorpamplona.amethyst.commons.resources.git_untitled
import com.vitorpamplona.amethyst.commons.resources.git_your_repositories
import com.vitorpamplona.amethyst.commons.resources.git_your_repositories_empty
import com.vitorpamplona.amethyst.commons.resources.loading_feed
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/** Above this width the dashboard splits into the inbox and the repository panels side by side. */
private val TwoColumnWidth = 760.dp
private const val INBOX_ROWS = 8
private const val REPOSITORY_ROWS = 6
private const val FILTER_AT = 8

/** At most this many repositories keep a live subscription for their issues and PRs. */
private const val WATCHED_SUBSCRIPTIONS = 20

/**
 * The git dashboard, as gitworkshop lays out its own: an inbox of the issues and PRs the reader
 * should hear about (one row per item, unread first in weight), where they left off, and their
 * own and followed repositories. Two columns on a wide window, one on a phone with the
 * repositories first.
 */
@Composable
fun GitDashboard(
    accountViewModel: AccountViewModel,
    nav: INav,
    onExplore: () -> Unit,
) {
    val viewModel: GitDashboardViewModel =
        viewModel(
            key = "GitDashboard" + accountViewModel.account.signer.pubKey,
            factory = GitDashboardViewModel.Factory(accountViewModel.account),
        )
    // Back from an item's thread: its read marker moved.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    GitDashboardSubscriptions(accountViewModel)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state

    current?.let { WatchRepositories(it, accountViewModel) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoColumnWidth
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(LocalDisappearingScaffoldPadding.current)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (current == null) {
                Text(stringRes(Res.string.loading_feed), style = gitMetaStyle(), modifier = Modifier.padding(24.dp))
                return@Column
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        InboxPanel(current, viewModel, accountViewModel, nav)
                        ContinuePanel(current, nav)
                    }
                    Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        RepositoriesPanel(current.myRepositories, own = true, accountViewModel, nav, onExplore)
                        RepositoriesPanel(current.followedRepositories, own = false, accountViewModel, nav, onExplore)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    RepositoriesPanel(current.myRepositories, own = true, accountViewModel, nav, onExplore)
                    RepositoriesPanel(current.followedRepositories, own = false, accountViewModel, nav, onExplore)
                    InboxPanel(current, viewModel, accountViewModel, nav)
                    ContinuePanel(current, nav)
                }
            }
            Spacer(Modifier.padding(bottom = 72.dp))
        }
    }
}

@Composable
private fun GitDashboardSubscriptions(accountViewModel: AccountViewModel) {
    val state =
        remember(accountViewModel.account) {
            val account = accountViewModel.account
            GitDashboardQueryState(account, account.outboxRelays.flow, account.notificationRelays.flow)
        }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().gitDashboard)
}

/** Each listed repository's announcements, and its issues and PRs, as its own screen loads them. */
@Composable
private fun WatchRepositories(
    state: GitDashboardState,
    accountViewModel: AccountViewModel,
) {
    val repositories = (state.myRepositories + state.followedRepositories).take(WATCHED_SUBSCRIPTIONS)
    LoadGitRepositoryAnnouncements(remember(repositories) { repositories.map { it.note.address } })
    repositories.forEach { repository ->
        key(repository.note.idHex) {
            val event by observeNoteEvent<GitRepositoryEvent>(repository.note, accountViewModel)
            event?.let { RepositoryFilterAssemblerSubscription(repository.note, it, accountViewModel.dataSources().gitRepository) }
        }
    }
}

// ---------------------------------------------------------------------------
// Inbox
// ---------------------------------------------------------------------------

@Composable
private fun InboxPanel(
    state: GitDashboardState,
    viewModel: GitDashboardViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val entries = if (unreadOnly) state.inbox.filter { it.unread > 0 } else state.inbox
    val shown = if (expanded) entries.take(50) else entries.take(INBOX_ROWS)

    Column {
        PanelHeader(MaterialSymbols.Notifications, stringRes(Res.string.git_inbox), count = state.unreadCount) {
            Toggle(stringRes(Res.string.git_inbox_all), !unreadOnly) { unreadOnly = false }
            Toggle(stringRes(Res.string.git_inbox_unread), unreadOnly) { unreadOnly = true }
            if (state.unreadCount > 0) {
                TextAction(stringRes(Res.string.git_inbox_mark_all_read)) { viewModel.markAllRead() }
            }
        }
        when {
            state.inbox.isEmpty() -> EmptyPanel(stringRes(Res.string.git_inbox_empty))
            entries.isEmpty() -> EmptyPanel(stringRes(Res.string.git_inbox_all_read))
            else ->
                Column(Modifier.fillMaxWidth().gitCard()) {
                    shown.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)
                        InboxRow(entry, showUnread = true, time = entry.lastActivity, nav = nav)
                    }
                }
        }
        if (entries.size > INBOX_ROWS) ShowMore(expanded) { expanded = !expanded }
    }
}

@Composable
private fun ContinuePanel(
    state: GitDashboardState,
    nav: INav,
) {
    if (state.recentWork.isEmpty()) return
    Column {
        PanelHeader(MaterialSymbols.History, stringRes(Res.string.git_continue_working), count = 0) {}
        Column(Modifier.fillMaxWidth().gitCard()) {
            state.recentWork.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)
                InboxRow(entry, showUnread = false, time = entry.myLastActivity, nav = nav)
            }
        }
    }
}

/**
 * One issue or PR: an unread dot, its state, its title, then "ngit · You commented · 3 new" and
 * when it last moved. Tapping it opens the thread, which marks it read.
 */
@Composable
private fun InboxRow(
    entry: GitInboxEntry,
    showUnread: Boolean,
    time: Long,
    nav: INav,
) {
    val item = entry.item
    val event = item.event ?: return
    val kind = observeGitStatus(item)?.statusKind() ?: StatusKind.OPEN
    val title = observeGitItemTitle(item)
    val repository = remember(event) { gitRepositoryNameOf(event) }
    val unread = showUnread && entry.unread > 0

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.Note(item.idHex)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background.copy(alpha = 0f)))
        GitStateIcon(kind = kind, forIssue = event is GitIssueEvent, contentDescription = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title ?: stringRes(Res.string.git_untitled),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val parts =
                listOfNotNull(
                    repository,
                    stringRes(involvementLabel(entry.involvement)),
                    if (unread) pluralStringRes(Res.plurals.git_inbox_new_count, entry.unread, entry.unread) else null,
                )
            Text(text = parts.joinToString(" · "), style = gitMetaStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = timeAgo(time, prefix = ""), style = gitMetaStyle(), maxLines = 1)
    }
}

private fun involvementLabel(involvement: GitInvolvement) =
    when (involvement) {
        GitInvolvement.AUTHOR -> Res.string.git_inbox_author
        GitInvolvement.PARTICIPANT -> Res.string.git_inbox_participant
        GitInvolvement.MENTIONED -> Res.string.git_inbox_mentioned
        GitInvolvement.WATCHING -> Res.string.git_inbox_watching
    }

// ---------------------------------------------------------------------------
// Repositories
// ---------------------------------------------------------------------------

@Composable
private fun RepositoriesPanel(
    repositories: List<GitDashboardRepository>,
    own: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onExplore: () -> Unit,
) {
    var query by rememberSaveable(own) { mutableStateOf("") }
    var expanded by rememberSaveable(own) { mutableStateOf(false) }
    val filtered =
        remember(repositories, query) {
            if (query.isBlank()) {
                repositories
            } else {
                repositories.filter { repo ->
                    val event = repo.note.event as? GitRepositoryEvent
                    (event?.name() ?: repo.note.address.dTag).contains(query, ignoreCase = true) ||
                        event?.description()?.contains(query, ignoreCase = true) == true
                }
            }
        }
    val shown = if (expanded) filtered else filtered.take(REPOSITORY_ROWS)

    Column {
        PanelHeader(
            symbol = if (own) MaterialSymbols.Code else MaterialSymbols.Bookmark,
            title = stringRes(if (own) Res.string.git_your_repositories else Res.string.git_followed_repositories),
            count = 0,
        ) {
            if (own) {
                TextAction(stringRes(Res.string.git_new_issue_button), MaterialSymbols.Add) { nav.nav(Route.NewGitRepository) }
            }
        }
        if (repositories.size > FILTER_AT) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringRes(Res.string.git_filter_repositories), style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                shape = GitShape,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.subtleBorder),
                leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (repositories.isEmpty()) {
            EmptyPanel(stringRes(if (own) Res.string.git_your_repositories_empty else Res.string.git_followed_repositories_empty), onClick = if (own) null else onExplore)
        } else {
            Column(Modifier.fillMaxWidth().gitCard()) {
                shown.forEachIndexed { index, repo ->
                    if (index > 0) HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)
                    RepositoryRow(repo.note, repo.lastActivity, accountViewModel, nav)
                }
            }
            if (filtered.size > REPOSITORY_ROWS) ShowMore(expanded) { expanded = !expanded }
        }
    }
}

@Composable
private fun RepositoryRow(
    note: AddressableNote,
    lastActivity: Long,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    val owner = note.author
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.GitRepository(note.address)) }
                .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (owner != null) {
            UserPicture(user = owner, size = 22.dp, accountViewModel = accountViewModel, nav = nav)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = event?.name() ?: note.address.dTag,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            event?.description()?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = gitMetaStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (lastActivity > 0) {
            Text(text = timeAgo(lastActivity, prefix = ""), style = gitMetaStyle(), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun PanelHeader(
    symbol: MaterialSymbol,
    title: String,
    count: Int,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.grayText)
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun Toggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText,
        modifier = Modifier.clip(GitShape).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun TextAction(
    label: String,
    symbol: MaterialSymbol? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clip(GitShape).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        symbol?.let { Icon(symbol = it, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
private fun EmptyPanel(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = text,
        style = gitMetaStyle(),
        modifier =
            Modifier
                .fillMaxWidth()
                .gitCard()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(PaddingValues(horizontal = 14.dp, vertical = 16.dp)),
    )
}

@Composable
private fun ShowMore(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = stringRes(if (expanded) Res.string.git_show_less else Res.string.git_show_more),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(GitShape).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
    )
}
