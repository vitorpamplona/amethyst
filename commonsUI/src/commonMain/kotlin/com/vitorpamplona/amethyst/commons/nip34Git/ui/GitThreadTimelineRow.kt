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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevision
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_pr_pushed
import com.vitorpamplona.amethyst.commons.resources.git_pr_pushed_rebased
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_description
import com.vitorpamplona.amethyst.commons.resources.git_proposed
import com.vitorpamplona.amethyst.commons.resources.git_proposed_explanation
import com.vitorpamplona.amethyst.commons.resources.git_timeline_patch_added
import com.vitorpamplona.amethyst.commons.resources.git_timeline_patch_revision
import com.vitorpamplona.amethyst.commons.resources.git_timeline_pushed
import com.vitorpamplona.amethyst.commons.resources.git_timeline_pushed_rebased
import com.vitorpamplona.amethyst.commons.resources.git_timeline_renamed
import com.vitorpamplona.amethyst.commons.resources.git_timeline_renamed_was
import com.vitorpamplona.amethyst.commons.resources.git_timeline_set_status
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GitStatusPill
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.gitStatusStyle
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

private val LabelShape = RoundedCornerShape(6.dp)

/**
 * The NIP-34 events a thread draws as one-line timeline entries instead of posts: status changes
 * (1630-1633) and PR updates (1619). They record something that happened to the issue, patch or
 * PR rather than saying something about it, the way a forge's timeline lists "set status to
 * Closed" or "pushed abc1234" between comments. The full cards (with the item they act on quoted
 * below) stay for the feed, the notifications and quotes, where the item isn't on screen.
 */
fun isGitTimelineEvent(event: Event?): Boolean =
    event is GitStatusEvent ||
        event is GitPullRequestUpdateEvent ||
        // The rest of a patch series and its revisions: pushes, not posts.
        event is GitPatchEvent ||
        // A NIP-34 rename (`#subject`); other labels never link into a thread.
        (event is LabelEvent && event.subjectEdit() != null)

/**
 * Cover notes (1624) are versions of the item's summary: the item shows the current one and its
 * edit pencil steps through the rest, as kind-1 edits do, so the thread lists none of them.
 */
fun isHiddenInGitThread(event: Event?): Boolean = event is GitCoverNoteEvent

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
        is GitPullRequestUpdateEvent -> UpdateRow(event, baseNote, modifier, background, accountViewModel, nav)
        is GitPatchEvent -> PatchRow(event, baseNote, modifier, background, accountViewModel, nav)
        is LabelEvent -> RenameRow(event, baseNote, modifier, background, accountViewModel, nav)
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
    val target = baseNote.replyTo?.lastOrNull()?.event
    val isIssue = target is GitIssueEvent
    val style = gitStatusStyle(kind, isIssue)
    // Anyone may publish a status; only the author's or a member's changes the item.
    val authorised = rememberIsAuthorisedChange(event, target)

    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = style.symbol,
        container = if (authorised) style.container else MaterialTheme.colorScheme.surfaceVariant,
        content = if (authorised) style.content else MaterialTheme.colorScheme.onSurfaceVariant,
        action = stringRes(Res.string.git_timeline_set_status),
        accountViewModel = accountViewModel,
        nav = nav,
        inline = {
            GitStatusPill(kind, forIssue = isIssue)
            if (!authorised) TimelineLabel(stringRes(Res.string.git_proposed))
        },
    ) {
        if (!authorised) {
            Text(
                text = stringRes(Res.string.git_proposed_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
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

/**
 * A later patch of the series, or the first patch of a new revision, pushed onto the root patch
 * the thread is about. Tapping it opens the patch with its diff.
 */
@Composable
private fun PatchRow(
    event: GitPatchEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(event) { event.title() }
    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier.clickable { nav.nav(Route.Note(baseNote.idHex)) },
        background = background,
        symbol = MaterialSymbols.Commit,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        action = stringRes(if (event.isRootRevision()) Res.string.git_timeline_patch_revision else Res.string.git_timeline_patch_added),
        accountViewModel = accountViewModel,
        nav = nav,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A rename (NIP-32 `#subject` label) of the item the thread is about: "changed the title to …". */
@Composable
private fun RenameRow(
    event: LabelEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val itemNote = baseNote.replyTo?.lastOrNull()
    val item = itemNote?.event
    val announcements = observeGitRepositoryAnnouncements(item)
    val authorised = remember(event, item, announcements) { item != null && LocalCache.gitAuthority.isMemberAt(event.pubKey, item, event.createdAt) }
    val previous =
        remember(event, itemNote, announcements) {
            itemNote?.let { GitItemTitles.renamesOf(it, LocalCache.gitAuthority).firstOrNull { rename -> rename.event.id == event.id }?.from }
        }

    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = MaterialSymbols.Edit,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        action = stringRes(Res.string.git_timeline_renamed),
        accountViewModel = accountViewModel,
        nav = nav,
        inline = {
            if (!authorised) TimelineLabel(stringRes(Res.string.git_proposed))
        },
    ) {
        Text(
            text = event.subjectEdit().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (previous != null) {
            Text(
                text = stringRes(Res.string.git_timeline_renamed_was, previous),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
        if (!authorised) {
            Text(
                text = stringRes(Res.string.git_proposed_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
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
    val pullRequestNote = remember(update) { update.parentPullRequestId()?.let { LocalCache.getOrCreateNote(it) } } ?: return null
    val siblings = observeGitPullRequestUpdates(pullRequestNote)
    return remember(update, siblings, pullRequest) { GitPullRequestRevisions.previous(update, siblings, pullRequest) }
}

/**
 * "rebased and pushed new commits": what a PR update changed, compared with the [previous]
 * revision, in words rather than commit hashes, which a reader can do nothing with. [sentence]
 * picks the capitalized form a card shows on its own over the lowercase one a timeline entry puts
 * after the author's name. Null when [current] names no commit.
 */
@Composable
fun gitPullRequestPushText(
    current: GitPullRequestRevision,
    previous: GitPullRequestRevision?,
    sentence: Boolean,
): String? {
    if (current.commit == null) return null
    val rebased = previous != null && current.rebasedFrom(previous)
    return when {
        rebased -> stringRes(if (sentence) Res.string.git_pr_pushed_rebased else Res.string.git_timeline_pushed_rebased)
        else -> stringRes(if (sentence) Res.string.git_pr_pushed else Res.string.git_timeline_pushed)
    }
}

/**
 * The shared shape of a timeline entry: a colored icon for the kind of change, then the author's
 * picture, name and time on one line and the change itself under it ([action] and [inline]
 * flowing together), with [body] for its detail.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    inline: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
                inline()
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
