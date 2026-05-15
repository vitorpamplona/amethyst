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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.AddressableNote
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
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.Size8dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

private val CardShape = QuoteBorder
private val ChipShape = RoundedCornerShape(8.dp)
private val CardPadding = PaddingValues(Size10dp)
private val HeaderSpacing = Arrangement.spacedBy(Size8dp)
private val LinkRowSpacing = Arrangement.spacedBy(Size8dp)

@Composable
private fun GitCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val border = MaterialTheme.colorScheme.subtleBorder
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CardShape)
                .border(1.dp, border, CardShape)
                .padding(CardPadding),
    ) {
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
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        maxLines = 1,
        modifier =
            Modifier
                .clip(ChipShape)
                .background(background)
                .padding(horizontal = Size8dp, vertical = 2.dp),
    )
}

@Composable
private fun LinkRow(
    symbol: MaterialSymbol,
    contentDescription: String?,
    url: String,
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
        )
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

            val commit = remember(noteEvent) { noteEvent.commit() }
            commit?.takeIf { it.isNotBlank() }?.let { hash ->
                Text(
                    text = hash.take(7),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = Font12SP),
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
            }
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

        LoadDecryptedContent(note, accountViewModel) { body ->
            val eventContent by
                remember(note.event) {
                    derivedStateOf {
                        val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }

                        if (!subject.isNullOrBlank() && !body.split("\n")[0].contains(subject)) {
                            "### $subject\n$body"
                        } else {
                            body
                        }
                    }
                }

            val isAuthorTheLoggedUser =
                remember(note.event) { accountViewModel.isLoggedUser(note.author) }

            if (makeItShort && isAuthorTheLoggedUser) {
                Text(
                    text = eventContent,
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
                        content = eventContent,
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

                if (note.event?.hasHashtags() == true) {
                    DisplayUncitedHashtags(
                        event = noteEvent,
                        content = eventContent,
                        callbackUri = callbackUri,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
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

        LoadDecryptedContent(note, accountViewModel) { body ->
            val eventContent by
                remember(note.event) {
                    derivedStateOf {
                        val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }

                        if (!subject.isNullOrBlank() && !body.split("\n")[0].contains(subject)) {
                            "### $subject\n$body"
                        } else {
                            body
                        }
                    }
                }

            val isAuthorTheLoggedUser =
                remember(note.event) { accountViewModel.isLoggedUser(note.author) }

            if (makeItShort && isAuthorTheLoggedUser) {
                Text(
                    text = eventContent,
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
                    val tags =
                        remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

                    TranslatableRichTextViewer(
                        content = eventContent,
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

                if (note.event?.hasHashtags() == true) {
                    DisplayUncitedHashtags(noteEvent, eventContent, callbackUri, accountViewModel, nav)
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
    val web = noteEvent.web()
    val clone = noteEvent.clone()

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

        if (web != null || clone != null) {
            Spacer(modifier = HalfDoubleVertSpacer)
            Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
                web?.let {
                    LinkRow(
                        symbol = MaterialSymbols.Public,
                        contentDescription = stringRes(id = R.string.git_web_address),
                        url = it,
                    )
                }
                clone?.let {
                    LinkRow(
                        symbol = MaterialSymbols.AutoMirrored.OpenInNew,
                        contentDescription = stringRes(id = R.string.git_clone_address),
                        url = it,
                    )
                }
            }
        }
    }
}
