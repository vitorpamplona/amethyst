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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNotes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_cover_note
import com.vitorpamplona.amethyst.commons.resources.git_summary_edited_by
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayEditStatus
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent

private val BannerShape = RoundedCornerShape(8.dp)

/**
 * The summary of a NIP-34 issue, patch or PR, with its cover notes (kind 1624) as versions of it.
 *
 * A cover note by the item's author or a confirmed repository member supersedes the item's own
 * description as its summary; anyone else's never counts ([observeCoverNoteVersions]). The latest
 * one shows, in the pinned cover-note frame, and the edit pencil steps through the older ones and
 * then the item's own description ("Original"), the way kind-1 edits do ([EditState]).
 *
 * [description] draws the item's own text. When [descriptionIsSummary] is false (a patch, whose
 * body is the diff) it stays under every version; otherwise it shows only as the Original.
 */
@Composable
fun GitVersionedSummary(
    item: Note,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    coverModifier: Modifier = Modifier,
    descriptionIsSummary: Boolean = true,
    description: @Composable () -> Unit,
) {
    val versions = observeCoverNoteVersions(item)
    if (versions.isEmpty()) {
        description()
        return
    }

    val editState = remember(versions) { EditState().apply { updateModifications(versions) } }
    val shown by editState.modificationToShow
    val cover = shown?.event as? GitCoverNoteEvent

    if (cover != null) {
        CoverNoteBox(cover, descriptionIsSummary, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, coverModifier) {
            DisplayEditStatus(editState)
        }
        if (!descriptionIsSummary) description()
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DisplayEditStatus(editState)
        }
        description()
    }
}

/**
 * The cover notes on [item] that count, as notes, oldest first ([GitCoverNotes.versions], the one
 * rule the banner and every other reader share). The last one is the item's current summary.
 */
@Composable
fun observeCoverNoteVersions(item: Note): List<Note> {
    val versions = observeGitCoverNotes(item)
    return remember(versions) { versions.mapNotNull { LocalCache.getNoteIfExists(it.id) } }
}

/**
 * The current cover note, drawn as the item's description itself, since that is what it is now,
 * with one muted line saying who wrote this version. On a patch, whose description is its commit
 * message, it reads as a note above the diff instead ([asDescription] false).
 */
@Composable
private fun CoverNoteBox(
    cover: GitCoverNoteEvent,
    asDescription: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val tags = remember(cover) { cover.tags.toImmutableListOfLists() }
        TranslatableRichTextViewer(
            content = cover.content,
            canPreview = canPreview,
            quotesLeft = quotesLeft,
            modifier = Modifier.fillMaxWidth(),
            tags = tags,
            backgroundColor = backgroundColor,
            id = cover.id,
            authorPubKey = cover.pubKey,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The version pencil ([trailing]) already marks an edit; a patch's note gets a pin.
            if (!asDescription) {
                Icon(
                    symbol = MaterialSymbols.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.grayText,
                )
            }
            Text(
                text = stringRes(if (asDescription) Res.string.git_summary_edited_by else Res.string.git_cover_note),
                style = gitMetaStyle(),
                maxLines = 1,
            )
            LoadUser(baseUserHex = cover.pubKey) { user ->
                if (user != null) {
                    ProvideTextStyle(gitMetaStyle()) {
                        UsernameDisplay(
                            user,
                            Modifier.weight(1f, fill = false),
                            fontWeight = FontWeight.SemiBold,
                            textColor = MaterialTheme.colorScheme.grayText,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
            Text(text = timeAgo(cover.createdAt, prefix = "· "), style = gitMetaStyle(), maxLines = 1)
            trailing()
        }
    }
}

/** The "Cover note · author · time" line shared by the banner and the standalone card; [trailing] holds the version pencil. */
@Composable
fun GitCoverNoteHeader(
    cover: GitCoverNoteEvent,
    accountViewModel: AccountViewModel,
    showAuthor: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.PushPin,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringRes(Res.string.git_cover_note),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (showAuthor) {
            LoadUser(baseUserHex = cover.pubKey) { user ->
                if (user != null) {
                    UsernameDisplay(user, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Normal, accountViewModel = accountViewModel)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TimeAgo(cover.createdAt)
        trailing()
    }
}
