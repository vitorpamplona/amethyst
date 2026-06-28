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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.model.GitPullRequestUpdateIndex
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.clickableNoteModifier
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.ui.note.types.GitStatusPill
import com.vitorpamplona.amethyst.ui.note.types.StatusKind
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent

/**
 * Compact feed renderer for the repository screen's Issues and Patches & PRs tabs.
 *
 * Unlike the generic [com.vitorpamplona.amethyst.ui.note.NoteCompose], which is tuned
 * for items appearing inside a regular feed, this shows a simple one-line-per-item list:
 * author picture, name, NIP-05, subject, time and the shared 3-dot options. It still
 * layers the same gating NoteCompose applies — event loading ([WatchNoteEvent]),
 * mute/block/report hiding ([CheckHiddenFeedWatchBlockAndReport]) and the long-press
 * quick-action menu ([LongPressToQuickAction]) — so blocked authors and reported items
 * are hidden here exactly as they are elsewhere. No note body or media is rendered, so
 * sensitive content never reaches this list in the first place.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GitItemFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
    labelFilter: String? = null,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val list =
        remember(items, labelFilter) {
            if (labelFilter == null) items.list else items.list.filter { labelFilter in gitLabelsOf(it.event) }
        }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(
            list,
            key = { _, item -> item.idHex },
            contentType = { _, item -> item.event?.kind ?: -1 },
        ) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                GitItemRow(
                    note = item,
                    isHiddenFeed = items.showHidden,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
private fun GitItemRow(
    note: Note,
    isHiddenFeed: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val modifier = Modifier.fillMaxWidth()

    WatchNoteEvent(
        baseNote = note,
        accountViewModel = accountViewModel,
        nav = nav,
        modifier = modifier,
    ) {
        CheckHiddenFeedWatchBlockAndReport(
            note = note,
            modifier = modifier,
            showHiddenWarning = false,
            ignoreAllBlocksAndReports = isHiddenFeed,
            accountViewModel = accountViewModel,
            nav = nav,
        ) { _ ->
            LongPressToQuickAction(baseNote = note, accountViewModel = accountViewModel, nav = nav) { showPopup ->
                GitItemRowContent(
                    note = note,
                    modifier = modifier,
                    showPopup = showPopup,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun GitItemRowContent(
    note: Note,
    modifier: Modifier,
    showPopup: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier =
            clickableNoteModifier(note, modifier, accountViewModel, showPopup, nav)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WatchAuthor(note, accountViewModel) { author ->
            UserPicture(
                user = author,
                size = Size40dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NoteUsernameDisplay(note, Modifier.weight(1f), accountViewModel = accountViewModel)
                TimeAgo(note)
                MoreOptionsButton(note, accountViewModel = accountViewModel, nav = nav)
            }

            ObserveDisplayNip05Status(note, accountViewModel, nav)

            val subject = remember(note.event) { gitSubjectOf(note.event) }
            Text(
                text = subject ?: stringRes(R.string.git_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val labels = remember(note.event) { gitLabelsOf(note.event) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                GitStatusPill(targetIdHex = note.idHex, defaultIfMissing = StatusKind.OPEN)
                if (note.event is GitPullRequestEvent) {
                    GitRevisedChip(note.idHex)
                }
                labels.take(6).forEach { LabelChip(it) }
            }
        }
    }
}

@Composable
private fun LabelChip(label: String) {
    Text(
        text = "#$label",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Small "Revised" badge shown when a pull request has a later kind-1619 update. */
@Composable
private fun GitRevisedChip(prIdHex: String) {
    LaunchedEffect(Unit) { GitPullRequestUpdateIndex.startIfNeeded() }
    val index by GitPullRequestUpdateIndex.latestByPullRequest.collectAsStateWithLifecycle()
    if (index?.get(prIdHex) == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Sync,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringRes(R.string.git_pr_revised),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun gitSubjectOf(event: Event?): String? =
    when (event) {
        is GitIssueEvent -> event.subject()?.takeIf { it.isNotBlank() }
        is GitPullRequestEvent -> event.subject()?.takeIf { it.isNotBlank() }
        is GitPatchEvent -> event.subject()?.takeIf { it.isNotBlank() }
        else -> null
    }

internal fun gitLabelsOf(event: Event?): List<String> =
    when (event) {
        is GitIssueEvent -> event.topics()
        is GitPullRequestEvent -> event.labels()
        else -> emptyList()
    }.filter { it.isNotBlank() }.distinct()
