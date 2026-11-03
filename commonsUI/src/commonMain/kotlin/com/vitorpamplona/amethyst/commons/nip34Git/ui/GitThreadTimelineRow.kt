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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.GitPullRequestUpdateIndex
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevision
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_cover_note_current
import com.vitorpamplona.amethyst.commons.resources.git_cover_note_not_applied
import com.vitorpamplona.amethyst.commons.resources.git_cover_note_superseded
import com.vitorpamplona.amethyst.commons.resources.git_pr_pushed
import com.vitorpamplona.amethyst.commons.resources.git_pr_pushed_from
import com.vitorpamplona.amethyst.commons.resources.git_pr_pushed_rebased
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_description
import com.vitorpamplona.amethyst.commons.resources.git_timeline_cover_note
import com.vitorpamplona.amethyst.commons.resources.git_timeline_pushed
import com.vitorpamplona.amethyst.commons.resources.git_timeline_pushed_from
import com.vitorpamplona.amethyst.commons.resources.git_timeline_pushed_rebased
import com.vitorpamplona.amethyst.commons.resources.git_timeline_status_closed
import com.vitorpamplona.amethyst.commons.resources.git_timeline_status_draft
import com.vitorpamplona.amethyst.commons.resources.git_timeline_status_merged
import com.vitorpamplona.amethyst.commons.resources.git_timeline_status_open
import com.vitorpamplona.amethyst.commons.resources.git_timeline_status_resolved
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.gitStatusStyle
import com.vitorpamplona.amethyst.commons.ui.note.types.repositoryAddressOf
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

private val LabelShape = RoundedCornerShape(6.dp)

/**
 * The NIP-34 events a thread draws as one-line timeline entries instead of posts: status changes
 * (1630-1633), cover notes (1624) and PR updates (1619). They record something that happened to
 * the issue, patch or PR rather than saying something about it, the way a forge's timeline lists
 * "closed this" or "pushed abc1234" between comments. The full cards (with the item they act on
 * quoted below) stay for the feed, the notifications and quotes, where the item isn't on screen.
 */
fun isGitTimelineEvent(event: Event?): Boolean = event is GitStatusEvent || event is GitCoverNoteEvent || event is GitPullRequestUpdateEvent

/** A git object id shortened to its 7-character prefix for display. */
fun String.shortCommit(): String = if (length > 7) take(7) else this

/**
 * One timeline entry in the thread of the issue, patch or PR that [baseNote] acts on. The thread
 * shows that item above, so the entry names only the change: who, what and when.
 */
@Composable
fun GitThreadTimelineRow(
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val event = baseNote.event) {
        is GitStatusEvent -> StatusRow(event, baseNote, modifier, background, accountViewModel, nav)
        is GitCoverNoteEvent -> CoverNoteRow(event, baseNote, modifier, background, accountViewModel, nav)
        is GitPullRequestUpdateEvent -> UpdateRow(event, baseNote, modifier, background, accountViewModel, nav)
        else -> {}
    }
}

@Composable
private fun StatusRow(
    event: GitStatusEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val kind = remember(event) { event.statusKind() }
    val style = gitStatusStyle(kind)
    val isIssue = baseNote.replyTo?.lastOrNull()?.event is GitIssueEvent
    val action =
        when (kind) {
            StatusKind.OPEN -> stringRes(Res.string.git_timeline_status_open)
            StatusKind.APPLIED -> stringRes(if (isIssue) Res.string.git_timeline_status_resolved else Res.string.git_timeline_status_merged)
            StatusKind.CLOSED -> stringRes(Res.string.git_timeline_status_closed)
            StatusKind.DRAFT -> stringRes(Res.string.git_timeline_status_draft)
        }

    TimelineRowLayout(baseNote, modifier, background, style.symbol, style.container, style.content, action, accountViewModel, nav) {
        if (event.content.isNotBlank()) {
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun CoverNoteRow(
    event: GitCoverNoteEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val target = baseNote.replyTo?.lastOrNull()?.event
    val rootId = remember(event) { event.rootEventId() }
    val state =
        if (target == null || rootId == null) {
            null
        } else {
            val repository = remember(target) { repositoryAddressOf(target) }
            val authorised = observeCoverNoteAuthors(target.pubKey, repository, accountViewModel)
            val displayed = observeDisplayedCoverNote(rootId, target.pubKey, repository, accountViewModel)
            when {
                displayed?.id == event.id -> Res.string.git_cover_note_current
                event.pubKey in authorised -> Res.string.git_cover_note_superseded
                else -> Res.string.git_cover_note_not_applied
            }
        }

    var expanded by remember(baseNote) { mutableStateOf(false) }

    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = MaterialSymbols.PushPin,
        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        content = MaterialTheme.colorScheme.primary,
        action = stringRes(Res.string.git_timeline_cover_note),
        accountViewModel = accountViewModel,
        nav = nav,
        onClick = { expanded = !expanded },
        trailing = {
            if (state != null) TimelineLabel(stringRes(state))
            Icon(
                symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        },
    ) {
        if (expanded) {
            val backgroundColor = remember { mutableStateOf(background) }
            val tags = remember(event) { event.tags.toImmutableListOfLists() }
            Spacer(Modifier.size(4.dp))
            TranslatableRichTextViewer(
                content = event.content,
                canPreview = true,
                quotesLeft = 1,
                modifier = Modifier.fillMaxWidth(),
                tags = tags,
                backgroundColor = backgroundColor,
                id = baseNote.idHex,
                callbackUri = baseNote.toNostrUri(),
                authorPubKey = event.pubKey,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpdateRow(
    event: GitPullRequestUpdateEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val targetNote = baseNote.replyTo?.lastOrNull()
    val pullRequest = targetNote?.let { observeNoteEvent<GitPullRequestEvent>(it, accountViewModel).value }
    val previous = observePreviousRevision(event, pullRequest)
    val action = gitPullRequestPushText(GitPullRequestRevision.of(event), previous, sentence = false) ?: stringRes(Res.string.git_pr_update_description)

    // The thread's PR already lists its clone URLs: name only the ones this update adds.
    val newCloneUrls =
        remember(event, pullRequest) {
            val known = pullRequest?.cloneUrls().orEmpty().toSet()
            event.cloneUrls().filter { it.isNotBlank() && it !in known }
        }

    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = MaterialSymbols.Sync,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        action = action,
        accountViewModel = accountViewModel,
        nav = nav,
    ) {
        newCloneUrls.forEach {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The revision [update] replaced, from the other updates of its PR or the PR itself. */
@Composable
fun observePreviousRevision(
    update: GitPullRequestUpdateEvent,
    pullRequest: GitPullRequestEvent?,
): GitPullRequestRevision? {
    val updates by GitPullRequestUpdateIndex.byPullRequest.collectAsStateWithLifecycle()
    return remember(update, updates, pullRequest) {
        val siblings = update.parentPullRequestId()?.let { updates?.get(it) }.orEmpty()
        GitPullRequestRevisions.previous(update, siblings, pullRequest)
    }
}

/**
 * "pushed 4246e71 (was 70d31ba), rebased onto bca8dc9": what a PR update changed, compared with the
 * [previous] revision. [sentence] picks the capitalized form a card shows on its own over the
 * lowercase one a timeline entry puts after the author's name. Null when [current] names no commit.
 */
@Composable
fun gitPullRequestPushText(
    current: GitPullRequestRevision,
    previous: GitPullRequestRevision?,
    sentence: Boolean,
): String? {
    val commit = current.commit?.shortCommit() ?: return null
    val was = previous?.commit?.takeIf { it != current.commit }?.shortCommit()
    val onto = if (previous != null && current.rebasedFrom(previous)) current.mergeBase?.shortCommit() else null

    return when {
        was != null && onto != null -> stringRes(if (sentence) Res.string.git_pr_pushed_rebased else Res.string.git_timeline_pushed_rebased, commit, was, onto)
        was != null -> stringRes(if (sentence) Res.string.git_pr_pushed_from else Res.string.git_timeline_pushed_from, commit, was)
        else -> stringRes(if (sentence) Res.string.git_pr_pushed else Res.string.git_timeline_pushed, commit)
    }
}

/**
 * The shared shape of a timeline entry: a colored icon for the kind of change, then the author's
 * picture, name and time on one line and the change itself under it, with [body] for its detail.
 */
@Composable
private fun TimelineRowLayout(
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    symbol: MaterialSymbol,
    container: Color,
    content: Color,
    action: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                // Match NoteComposeLayout's 12dp gutters so the icon lines up with reply avatars.
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteAuthorPicture(baseNote = baseNote, size = 18.dp, accountViewModel = accountViewModel, nav = nav)
                Spacer(modifier = Modifier.width(6.dp))
                NoteUsernameDisplay(baseNote, Modifier.weight(1f, fill = false), accountViewModel = accountViewModel, nav = nav)
                TimeAgo(baseNote)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.weight(1f),
                )
                trailing()
            }

            body()
        }
    }
}

@Composable
private fun TimelineLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            Modifier
                .clip(LabelShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
