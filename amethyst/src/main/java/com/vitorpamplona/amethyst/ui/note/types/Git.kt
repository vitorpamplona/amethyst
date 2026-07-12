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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepoSnapshotCache
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.GitPullRequestUpdateIndex
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryBrowserViewModelFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.RepoExternalNotice
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.RepoLanguageBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.RepoLastCommit
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.RepoStatTiles
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.computeLanguageBreakdown
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.repoHasFetchableClone
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.Size8dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

private val ChipShape = RoundedCornerShape(8.dp)
private val HeaderSpacing = Arrangement.spacedBy(Size8dp)
private val LinkRowSpacing = Arrangement.spacedBy(Size8dp)

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
        ClickableUrl(
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

/** Shortens a 40-char git object id to its 7-char prefix for display. */
private fun String.shortCommit(): String = if (length > 7) take(7) else this

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
    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
        ) {
            TypeChip(
                text = stringRes(id = R.string.kind_git_patch),
                background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.tertiary,
            )

            GitStatusPill(targetIdHex = note.idHex, defaultIfMissing = StatusKind.OPEN)
        }

        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null) {
            Spacer(modifier = StdVertSpacer)
            LoadAddressableNote(repository, accountViewModel) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
        }

        val commit = remember(noteEvent) { noteEvent.commit()?.takeIf { it.isNotBlank() } }
        if (commit != null) {
            Spacer(modifier = StdVertSpacer)
            GitMetaRow(
                symbol = MaterialSymbols.Commit,
                contentDescription = stringRes(id = R.string.git_commit),
                value = commit.shortCommit(),
            )
        }

        Spacer(modifier = HalfDoubleVertSpacer)

        // In a collapsed feed preview keep the lightweight markdown body; in the
        // full (thread) view parse the patch into a proper file-by-file diff.
        val parsed = if (makeItShort) null else remember(noteEvent) { UnifiedDiffParser.parse(noteEvent.content) }
        if (parsed != null && parsed.hasDiff) {
            if (parsed.message.isNotBlank()) {
                Text(
                    text = parsed.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = HalfDoubleVertSpacer)
            }
            GitDiffView(parsed, Modifier.fillMaxWidth())
        } else {
            GitMarkdownBody(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
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
    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
        ) {
            TypeChip(
                text = stringRes(id = R.string.kind_git_issue),
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            )

            GitStatusPill(targetIdHex = note.idHex, defaultIfMissing = StatusKind.OPEN)
        }

        val subject = remember(noteEvent) { noteEvent.subject()?.takeIf { it.isNotBlank() } }
        if (subject != null) {
            GitSubjectTitle(subject)
        }

        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null) {
            Spacer(modifier = StdVertSpacer)
            LoadAddressableNote(repository, accountViewModel) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
        }

        Spacer(modifier = HalfDoubleVertSpacer)

        GitMarkdownBody(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, renderedSubject = subject)

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
    // A later pull-request update (kind 1619) revises this PR with a newer commit /
    // merge base. Fold the most recent one in so the card reflects the current state.
    val updateIndex by GitPullRequestUpdateIndex.latestByPullRequest.collectAsStateWithLifecycle()
    val update = updateIndex?.get(note.idHex)

    GitCardContainer {
        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null) {
            LoadAddressableNote(repository, accountViewModel) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
            Spacer(modifier = StdVertSpacer)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
        ) {
            TypeChip(
                text = stringRes(id = R.string.kind_git_pr),
                background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.secondary,
                symbol = MaterialSymbols.CallMerge,
            )

            GitStatusPill(targetIdHex = note.idHex, defaultIfMissing = StatusKind.OPEN)

            if (update != null) {
                TypeChip(
                    text = stringRes(id = R.string.git_pr_revised),
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    symbol = MaterialSymbols.Sync,
                )
            }
        }

        val subject = remember(noteEvent) { noteEvent.subject()?.takeIf { it.isNotBlank() } }
        if (subject != null) {
            GitSubjectTitle(subject)
        }

        val branch = remember(noteEvent) { noteEvent.branchName()?.takeIf { it.isNotBlank() } }
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
                (update?.cloneUrls()?.filter { it.isNotBlank() }?.ifEmpty { null } ?: noteEvent.cloneUrls().filter { it.isNotBlank() })
            }

        if (branch != null || currentCommit != null || mergeBase != null) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                branch?.let {
                    GitMetaRow(MaterialSymbols.AltRoute, stringRes(id = R.string.git_branch), it)
                }
                currentCommit?.let {
                    GitMetaRow(MaterialSymbols.Commit, stringRes(id = R.string.git_commit), it.shortCommit())
                }
                mergeBase?.let {
                    GitMetaRow(MaterialSymbols.CallMerge, stringRes(id = R.string.git_merge_base), it.shortCommit())
                }
            }
        }

        if (cloneUrls.isNotEmpty()) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                cloneUrls.forEach { url ->
                    LinkRow(
                        symbol = MaterialSymbols.CloudDownload,
                        contentDescription = stringRes(id = R.string.git_clone_address),
                        url = url,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = Font12SP),
                    )
                }
            }
        }

        Spacer(modifier = HalfDoubleVertSpacer)

        GitMarkdownBody(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, renderedSubject = subject)

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
    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
        ) {
            TypeChip(
                text = stringRes(id = R.string.kind_git_pr_update),
                background = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                symbol = MaterialSymbols.Sync,
            )
        }

        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null) {
            Spacer(modifier = StdVertSpacer)
            LoadAddressableNote(repository, accountViewModel) {
                if (it != null) {
                    RenderShortRepositoryHeader(it, accountViewModel, nav)
                }
            }
        }

        Spacer(modifier = HalfDoubleVertSpacer)
        Text(
            text = stringRes(id = R.string.git_pr_update_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
        )

        val currentCommit = remember(noteEvent) { noteEvent.currentCommit()?.takeIf { it.isNotBlank() } }
        val mergeBase = remember(noteEvent) { noteEvent.mergeBase()?.takeIf { it.isNotBlank() } }
        val cloneUrls = remember(noteEvent) { noteEvent.cloneUrls().filter { it.isNotBlank() } }

        if (currentCommit != null || mergeBase != null) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                currentCommit?.let {
                    GitMetaRow(MaterialSymbols.Commit, stringRes(id = R.string.git_commit), it.shortCommit())
                }
                mergeBase?.let {
                    GitMetaRow(MaterialSymbols.CallMerge, stringRes(id = R.string.git_merge_base), it.shortCommit())
                }
            }
        }

        if (cloneUrls.isNotEmpty()) {
            Spacer(modifier = StdVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                cloneUrls.forEach { url ->
                    LinkRow(
                        symbol = MaterialSymbols.CloudDownload,
                        contentDescription = stringRes(id = R.string.git_clone_address),
                        url = url,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = Font12SP),
                    )
                }
            }
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
                    text = stringRes(id = R.string.kind_git_repo),
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
                    text = stringRes(id = R.string.git_repo_personal_fork),
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
