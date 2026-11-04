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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.GitPullRequestUpdateIndex
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepoSnapshotCache
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevision
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCiStatusBadge
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitStatusPill
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitVersionedSummary
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitPullRequestPushText
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitItemLabels
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitItemTitle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observePreviousRevision
import com.vitorpamplona.amethyst.commons.nip34Git.ui.shortCommit
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.LoadGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_branch
import com.vitorpamplona.amethyst.commons.resources.git_clone_address
import com.vitorpamplona.amethyst.commons.resources.git_commit
import com.vitorpamplona.amethyst.commons.resources.git_merge_base
import com.vitorpamplona.amethyst.commons.resources.git_pr_revised
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_description
import com.vitorpamplona.amethyst.commons.resources.git_repo_personal_fork
import com.vitorpamplona.amethyst.commons.resources.git_was
import com.vitorpamplona.amethyst.commons.resources.kind_git_issue
import com.vitorpamplona.amethyst.commons.resources.kind_git_patch
import com.vitorpamplona.amethyst.commons.resources.kind_git_pr
import com.vitorpamplona.amethyst.commons.resources.kind_git_pr_update
import com.vitorpamplona.amethyst.commons.resources.kind_git_repo
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.ScopedViewModelStore
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepositoryBrowserViewModelFactory
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoExternalNotice
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLanguageBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLastCommit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoStatTiles
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.computeLanguageBreakdown
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.repoHasFetchableClone
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size16dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size8dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.collections.immutable.ImmutableList

private val ChipShape = RoundedCornerShape(8.dp)
private val HeaderSpacing = Arrangement.spacedBy(Size8dp)
private val LinkRowSpacing = Arrangement.spacedBy(Size8dp)
private val CoverNoteSpacing = Modifier.padding(bottom = Size8dp)

@Composable
private fun GitCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun IconBadge(
    symbol: MaterialSymbol,
    contentDescription: String?,
    background: Color,
    tint: Color,
    size: Dp = 28.dp,
    iconSize: Dp = Size16dp,
) {
    Row(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = tint,
        )
    }
}

@Composable
private fun TypeChip(
    text: String,
    background: Color,
    contentColor: Color,
    symbol: MaterialSymbol? = null,
) {
    Row(
        modifier =
            Modifier
                .clip(ChipShape)
                .background(background)
                .padding(horizontal = Size8dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        symbol?.let {
            Icon(
                symbol = it,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun LinkRow(
    symbol: MaterialSymbol,
    contentDescription: String?,
    url: String,
    style: TextStyle = LocalTextStyle.current,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = LinkRowSpacing,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = Modifier.size(Size16dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        ClickableUrlOrBlossom(
            url = url,
            urlText = url.removePrefix("https://").removePrefix("http://"),
            style = style,
        )
    }
}

@Composable
private fun GitSubjectTitle(text: String) {
    Spacer(modifier = StdVertSpacer)
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        overflow = TextOverflow.Ellipsis,
        maxLines = 3,
    )
}

/**
 * A small icon + value line for a piece of git metadata such as a branch name,
 * commit hash or merge base. [value] is shown verbatim; commit-like hashes are
 * expected to be shortened by the caller via [shortCommit].
 */
@Composable
private fun GitMetaRow(
    symbol: MaterialSymbol,
    contentDescription: String?,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = LinkRowSpacing,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = Modifier.size(Size16dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = Font12SP),
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The labels an issue, patch or PR shows, as small chips under its title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitLabelsRow(labels: ImmutableList<String>) {
    if (labels.isEmpty()) return
    FlowRow(
        modifier = Modifier.padding(top = Size5dp),
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clip(ChipShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * A `git format-patch` message without its mail headers: the `From <sha>` line, `From:`, `Date:`
 * and the (possibly folded) `Subject:`, which the card already shows as the title.
 */
private fun formatPatchBody(message: String): String {
    val lines = message.lines()
    var index = 0
    var inSubject = false
    while (index < lines.size) {
        val line = lines[index]
        val isHeader =
            line.startsWith("From ") || line.startsWith("From:") || line.startsWith("Date:") || line.startsWith("Subject:")
        if (isHeader) {
            inSubject = line.startsWith("Subject:")
        } else if (!(inSubject && (line.startsWith(" ") || line.startsWith("\t")))) {
            break
        }
        index++
    }
    return lines.drop(index).joinToString("\n").trim()
}

/**
 * Some NIP-34 clients duplicate the subject tag as a leading markdown heading of the
 * content ("# Title\n\n…"). The card already renders the subject as its own title, so
 * when the first line is a heading matching [subject], drop it from the body.
 */
private fun stripDuplicatedSubject(
    content: String,
    subject: String?,
): String {
    if (subject.isNullOrBlank()) return content
    val trimmed = content.trimStart()
    if (!trimmed.startsWith("#")) return content
    val firstLineEnd = trimmed.indexOf('\n').let { if (it < 0) trimmed.length else it }
    val heading = trimmed.substring(0, firstLineEnd).trimStart('#').trim()
    if (!heading.equals(subject.trim(), ignoreCase = true)) return content
    return trimmed.substring(firstLineEnd).trimStart()
}

/**
 * The shared markdown body used by patches, issues and pull requests: honors
 * the collapsed [makeItShort] preview for the logged-in user's own posts and
 * otherwise renders the full content with sensitivity warnings and uncited
 * hashtags. The subject, when present, is rendered separately as a title by the
 * caller, so it is not inlined here — pass it as [renderedSubject] so a copy
 * duplicated into the content as a leading heading is stripped.
 */
@Composable
private fun GitMarkdownBody(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    renderedSubject: String? = null,
) {
    LoadDecryptedContent(note, accountViewModel) { rawBody ->
        val body = remember(rawBody, renderedSubject) { stripDuplicatedSubject(rawBody, renderedSubject) }
        val isAuthorTheLoggedUser =
            remember(note.event) { accountViewModel.isLoggedUser(note.author) }

        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = body,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            val callbackUri = remember(note) { note.toNostrUri() }

            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = body,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            val event = note.event
            if (event?.hasHashtags() == true) {
                DisplayUncitedHashtags(
                    event = event,
                    content = body,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

/** [current] shortened, followed by "(was …)" when a later update moved it off [original]. */
@Composable
private fun revisedValue(
    current: String,
    original: String?,
): String =
    if (original.isNullOrBlank() || original == current) {
        current.shortCommit()
    } else {
        stringRes(id = Res.string.git_was, current.shortCommit(), original.shortCommit())
    }

/**
 * The summary of an issue or PR: its current cover note, with the edit pencil stepping back
 * through older ones to the item's own description ([GitVersionedSummary]).
 */
@Composable
private fun GitCoverNoteAndDescription(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    renderedSubject: String?,
) {
    GitVersionedSummary(note, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, CoverNoteSpacing) {
        GitMarkdownBody(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, renderedSubject)
    }
}

@Composable
fun RenderGitPatchEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitPatchEvent ?: return

    RenderGitPatchEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderShortRepositoryHeader(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent by observeNoteEvent<GitRepositoryEvent>(baseNote, accountViewModel)
    val title = noteEvent?.name() ?: baseNote.dTag()
    val summary = noteEvent?.description()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ChipShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .clickable { nav.nav(Route.GitRepository(baseNote.address)) }
                .padding(horizontal = Size10dp, vertical = Size8dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = HeaderSpacing,
    ) {
        IconBadge(
            symbol = MaterialSymbols.Code,
            contentDescription = null,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            tint = MaterialTheme.colorScheme.primary,
            size = 24.dp,
            iconSize = 14.dp,
        )

        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RenderGitPatchEvent(
    noteEvent: GitPatchEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The repository announcements its statuses, updates, labels and cover notes are judged by.
    LoadGitRepositoryAnnouncements(noteEvent)

    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
        ) {
            TypeChip(
                text = stringRes(id = Res.string.kind_git_patch),
                background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.tertiary,
            )

            GitStatusPill(targetIdHex = note.idHex, defaultIfMissing = StatusKind.OPEN)

            GitCiStatusBadge(targetIdHex = note.idHex, accountViewModel = accountViewModel, nav = nav)
        }

        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null) {
            Spacer(modifier = StdVertSpacer)
            LoadAddressableNote(repository) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
        }

        val title = observeGitItemTitle(note)
        if (title != null) {
            GitSubjectTitle(title)
        }
        GitLabelsRow(observeGitItemLabels(note))

        val commit = remember(noteEvent) { noteEvent.commit()?.takeIf { it.isNotBlank() } }
        if (commit != null) {
            Spacer(modifier = StdVertSpacer)
            GitMetaRow(
                symbol = MaterialSymbols.Commit,
                contentDescription = stringRes(id = Res.string.git_commit),
                value = commit.shortCommit(),
            )
        }

        Spacer(modifier = HalfDoubleVertSpacer)

        // A patch's body is its commit message and diff: a cover note sits above it, never in place of it.
        GitVersionedSummary(note, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, CoverNoteSpacing, descriptionIsSummary = false) {
            // In a collapsed feed preview keep the lightweight markdown body; in the
            // full (thread) view parse the patch into a proper file-by-file diff.
            val parsed = if (makeItShort) null else remember(noteEvent) { UnifiedDiffParser.parse(noteEvent.content) }
            if (parsed != null && parsed.hasDiff) {
                // The title is drawn above; the message keeps only the body under git's headers.
                val body = remember(parsed) { formatPatchBody(parsed.message) }
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = HalfDoubleVertSpacer)
                }
                GitDiffView(parsed, Modifier.fillMaxWidth())
            } else {
                GitMarkdownBody(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        if (!makeItShort) {
            GitStatusActions(note, accountViewModel)
        }
    }
}

@Composable
fun RenderGitIssueEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitIssueEvent ?: return

    RenderGitIssueEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderGitIssueEvent(
    noteEvent: GitIssueEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The repository announcements its statuses, updates, labels and cover notes are judged by.
    LoadGitRepositoryAnnouncements(noteEvent)

    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
        ) {
            TypeChip(
                text = stringRes(id = Res.string.kind_git_issue),
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            )

            GitStatusPill(targetIdHex = note.idHex, defaultIfMissing = StatusKind.OPEN, forIssue = true)
        }

        val subject = remember(noteEvent) { noteEvent.subject()?.takeIf { it.isNotBlank() } }
        // A maintainer may have renamed it since (NIP-32 `#subject`): show the current title.
        val title = observeGitItemTitle(note)
        if (title != null) {
            GitSubjectTitle(title)
        }
        GitLabelsRow(observeGitItemLabels(note))

        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null) {
            Spacer(modifier = StdVertSpacer)
            LoadAddressableNote(repository) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
        }

        Spacer(modifier = HalfDoubleVertSpacer)

        GitCoverNoteAndDescription(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, renderedSubject = subject)

        if (!makeItShort) {
            GitStatusActions(note, accountViewModel)
        }
    }
}

@Composable
fun RenderGitPullRequestEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitPullRequestEvent ?: return

    RenderGitPullRequestEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderGitPullRequestEvent(
    noteEvent: GitPullRequestEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The repository announcements its statuses, updates, labels and cover notes are judged by.
    LoadGitRepositoryAnnouncements(noteEvent)

    // A later pull-request update (kind 1619) revises this PR with a newer commit /
    // merge base. Fold the most recent one in so the card reflects the current state.
    val updateIndex by GitPullRequestUpdateIndex.latestByPullRequest.collectAsStateWithLifecycle()
    val update = updateIndex?.get(note.idHex)

    GitCardContainer {
        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null) {
            LoadAddressableNote(repository) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
            Spacer(modifier = StdVertSpacer)
        }

        FlowRow(
            horizontalArrangement = HeaderSpacing,
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            TypeChip(
                text = stringRes(id = Res.string.kind_git_pr),
                background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.secondary,
                symbol = MaterialSymbols.CallMerge,
            )

            GitStatusPill(targetIdHex = note.idHex, defaultIfMissing = StatusKind.OPEN)

            if (update != null) {
                TypeChip(
                    text = stringRes(id = Res.string.git_pr_revised),
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    symbol = MaterialSymbols.Sync,
                )
            }

            GitCiStatusBadge(
                targetIdHex = note.idHex,
                accountViewModel = accountViewModel,
                nav = nav,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        val subject = remember(noteEvent) { noteEvent.subject()?.takeIf { it.isNotBlank() } }
        // A maintainer may have renamed it since (NIP-32 `#subject`): show the current title.
        val title = observeGitItemTitle(note)
        if (title != null) {
            GitSubjectTitle(title)
        }
        GitLabelsRow(observeGitItemLabels(note))

        // Where it comes from, and where it goes when it isn't the default branch (`b`).
        val branch =
            remember(noteEvent) {
                val source = noteEvent.branchName()?.takeIf { it.isNotBlank() }
                val target = noteEvent.targetBranch()
                when {
                    target == null -> source
                    source == null -> "→ $target"
                    else -> "$source → $target"
                }
            }
        val currentCommit =
            remember(noteEvent, update) {
                (update?.currentCommit() ?: noteEvent.currentCommit())?.takeIf { it.isNotBlank() }
            }
        val mergeBase =
            remember(noteEvent, update) {
                (update?.mergeBase() ?: noteEvent.mergeBase())?.takeIf { it.isNotBlank() }
            }
        val cloneUrls =
            remember(noteEvent, update) {
                // The update's servers carry its new tip; the PR's own may still serve it too.
                (update?.cloneUrls().orEmpty() + noteEvent.cloneUrls()).filter { it.isNotBlank() }.distinct()
            }

        if (branch != null || currentCommit != null || mergeBase != null) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                branch?.let {
                    GitMetaRow(MaterialSymbols.AltRoute, stringRes(id = Res.string.git_branch), it)
                }
                // A later update moved the tip: say what it was so the original isn't lost.
                currentCommit?.let {
                    GitMetaRow(MaterialSymbols.Commit, stringRes(id = Res.string.git_commit), revisedValue(it, noteEvent.currentCommit()))
                }
                mergeBase?.let {
                    GitMetaRow(MaterialSymbols.CallMerge, stringRes(id = Res.string.git_merge_base), revisedValue(it, noteEvent.mergeBase()))
                }
            }
        }

        if (cloneUrls.isNotEmpty()) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                cloneUrls.forEach { url ->
                    LinkRow(
                        symbol = MaterialSymbols.CloudDownload,
                        contentDescription = stringRes(id = Res.string.git_clone_address),
                        url = url,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = Font12SP),
                    )
                }
            }
        }

        Spacer(modifier = HalfDoubleVertSpacer)

        GitCoverNoteAndDescription(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, renderedSubject = subject)

        if (!makeItShort) {
            GitPullRequestChanges(cloneUrls, currentCommit, mergeBase, accountViewModel)
            GitStatusActions(note, accountViewModel)
        }
    }
}

@Composable
fun RenderGitPullRequestUpdateEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitPullRequestUpdateEvent ?: return

    RenderGitPullRequestUpdateEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

/**
 * A PR update (kind 1619) on its own, in a feed, a notification or a quote. Says what moved
 * ("Pushed 4246e71 (was 70d31ba), rebased onto bca8dc9") and quotes the PR it updates, since
 * nothing else on screen names it. In the PR's own thread it is a timeline row instead
 * (GitThreadTimelineRow).
 */
@Composable
private fun RenderGitPullRequestUpdateEvent(
    noteEvent: GitPullRequestUpdateEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val targetNote = note.replyTo?.lastOrNull()
    val pullRequest = targetNote?.let { observeNoteEvent<GitPullRequestEvent>(it, accountViewModel).value }
    // The quoted PR carries the repository header and its clone URLs; draw them here only
    // when there is no PR to quote.
    val quotesPullRequest = pullRequest != null && !makeItShort && quotesLeft > 0

    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
        ) {
            TypeChip(
                text = stringRes(id = Res.string.kind_git_pr_update),
                background = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                symbol = MaterialSymbols.Sync,
            )
        }

        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null && !quotesPullRequest) {
            Spacer(modifier = StdVertSpacer)
            LoadAddressableNote(repository) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
        }

        val current = remember(noteEvent) { GitPullRequestRevision.of(noteEvent) }
        val previous = observePreviousRevision(noteEvent, pullRequest)
        val summary = gitPullRequestPushText(current, previous, sentence = true)

        Spacer(modifier = HalfDoubleVertSpacer)
        Text(
            text = summary ?: stringRes(id = Res.string.git_pr_update_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
        )

        // Without an earlier revision to compare with, the summary can't name the commits.
        if (previous == null && (current.commit != null || current.mergeBase != null)) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                current.commit?.let {
                    GitMetaRow(MaterialSymbols.Commit, stringRes(id = Res.string.git_commit), it.shortCommit())
                }
                current.mergeBase?.let {
                    GitMetaRow(MaterialSymbols.CallMerge, stringRes(id = Res.string.git_merge_base), it.shortCommit())
                }
            }
        }

        val cloneUrls =
            remember(noteEvent, pullRequest, quotesPullRequest) {
                val shown = if (quotesPullRequest) pullRequest.cloneUrls().toSet() else emptySet()
                noteEvent.cloneUrls().filter { it.isNotBlank() && it !in shown }
            }

        if (cloneUrls.isNotEmpty()) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                cloneUrls.forEach { url ->
                    LinkRow(
                        symbol = MaterialSymbols.CloudDownload,
                        contentDescription = stringRes(id = Res.string.git_clone_address),
                        url = url,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = Font12SP),
                    )
                }
            }
        }

        if (quotesPullRequest) {
            Spacer(modifier = StdVertSpacer)
            RenderTargetNote(note, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderGitRepositoryEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitRepositoryEvent ?: return

    RenderGitRepositoryEvent(event, baseNote, accountViewModel, nav)
}

@Composable
private fun RenderGitRepositoryEvent(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = noteEvent.name() ?: noteEvent.dTag()
    val summary = noteEvent.description()
    val topics = remember(noteEvent) { noteEvent.hashtags().filter { it.isNotBlank() } }
    val isPersonalFork = remember(noteEvent) { noteEvent.isPersonalFork() }

    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconBadge(
                symbol = MaterialSymbols.Code,
                contentDescription = null,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text(
                    text = stringRes(id = Res.string.kind_git_repo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isPersonalFork) {
                TypeChip(
                    text = stringRes(id = Res.string.git_repo_personal_fork),
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    symbol = MaterialSymbols.AltRoute,
                )
            }
        }

        summary?.let {
            Spacer(modifier = HalfDoubleVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        RepoSnapshotDashboard(noteEvent, note, accountViewModel, nav)

        if (topics.isNotEmpty()) {
            Spacer(modifier = HalfDoubleVertSpacer)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
                verticalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                topics.forEach { topic ->
                    TypeChip(
                        text = "#$topic",
                        background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        contentColor = MaterialTheme.colorScheme.grayText,
                    )
                }
            }
        }
    }
}

/**
 * Loads a shallow snapshot of the repository over smart-HTTP and renders the same
 * stat tiles / language bar / last-commit strip used on the project home, in place of
 * the old web/clone links. Fetches lazily (once) when the card is composed.
 */
@Composable
private fun RepoSnapshotDashboard(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A store per card: in the screen's store, every repo scrolled past kept its model and loaded
    // snapshot until the screen was popped, around GitRepoSnapshotCache's deliberately small LRU.
    // Scrolling back reads that cache first.
    key(note.idHex) {
        ScopedViewModelStore {
            RepoSnapshotDashboardContent(noteEvent, note, accountViewModel, nav)
        }
    }
}

@Composable
private fun RepoSnapshotDashboardContent(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val cacheKey = remember(noteEvent) { noteEvent.address().toValue() }
    val browser: GitRepositoryBrowserViewModel =
        viewModel(
            key = note.idHex + "GitRepoCardBrowser",
            factory = GitRepositoryBrowserViewModelFactory(accountViewModel.httpClientBuilder::okHttpClientForPreview),
        )
    LaunchedEffect(noteEvent) { browser.loadOnce(noteEvent.clones(), cacheKey) }
    val browserState by browser.state.collectAsStateWithLifecycle()
    // Prefer the live state, but fall back to a cached snapshot so an already-fetched repo
    // renders synchronously — including in the share-to-image capture, which won't wait for a
    // fresh clone.
    val snapshot = (browserState as? GitBrowseState.Loaded)?.snapshot ?: remember(cacheKey) { GitRepoSnapshotCache.get(cacheKey) }
    if (snapshot == null) {
        // Repos we can't clone over http(s) (e.g. Iris's htree://) get an open-in-browser notice
        // instead of an indefinitely-empty dashboard.
        if (!repoHasFetchableClone(noteEvent)) {
            Spacer(modifier = HalfDoubleVertSpacer)
            RepoExternalNotice(noteEvent)
        }
        return
    }

    val fileNames = remember(snapshot) { snapshot.walkFileNames() }
    val slices = remember(fileNames) { computeLanguageBreakdown(fileNames) }

    Spacer(modifier = HalfDoubleVertSpacer)
    RepoStatTiles(
        branches = snapshot.branches.size,
        tags = snapshot.tags.size,
        files = fileNames.size,
        updatedEpochSec = snapshot.tipCommit?.authorTimeSec,
    )
    if (slices.isNotEmpty()) {
        Spacer(modifier = HalfDoubleVertSpacer)
        RepoLanguageBar(slices)
    }
    snapshot.tipCommit?.let { commit ->
        Spacer(modifier = HalfDoubleVertSpacer)
        RepoLastCommit(commit) { nav.nav(Route.GitRepository(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())) }
    }
}
