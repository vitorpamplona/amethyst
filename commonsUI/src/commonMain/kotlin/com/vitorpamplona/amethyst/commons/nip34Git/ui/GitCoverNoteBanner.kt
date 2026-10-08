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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNoteIndex
import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNotes
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_cover_note
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

private val BannerShape = RoundedCornerShape(8.dp)

/**
 * The pinned cover note (kind 1624) of a NIP-34 issue, patch or PR, drawn above its description.
 * Shows the latest note by the item's author or a repository maintainer ([GitCoverNotes]); notes
 * by anyone else never reach this banner. Renders nothing when there is no such note.
 */
@Composable
fun GitCoverNoteBanner(
    rootIdHex: HexKey,
    rootAuthor: HexKey,
    repositoryAddress: Address?,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val index by GitCoverNoteIndex.byRoot.collectAsStateWithLifecycle()
    val notes = index?.get(rootIdHex) ?: return

    if (repositoryAddress == null) {
        ResolvedCoverNote(notes, rootIdHex, GitCoverNotes.authorisedAuthors(rootAuthor, null, null), canPreview, quotesLeft, backgroundColor, accountViewModel, nav, modifier)
    } else {
        LoadAddressableNote(repositoryAddress) { repoNote ->
            val repository = repoNote?.let { observeRepository(it, accountViewModel) }
            val authorised = remember(rootAuthor, repositoryAddress, repository) { GitCoverNotes.authorisedAuthors(rootAuthor, repositoryAddress, repository) }
            ResolvedCoverNote(notes, rootIdHex, authorised, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, modifier)
        }
    }
}

@Composable
private fun observeRepository(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
): GitRepositoryEvent? {
    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    return event
}

@Composable
private fun ResolvedCoverNote(
    notes: List<GitCoverNoteEvent>,
    rootIdHex: HexKey,
    authorised: Set<HexKey>,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    val cover = remember(notes, rootIdHex, authorised) { GitCoverNotes.latestAuthorised(notes, rootIdHex, authorised) } ?: return
    CoverNoteBox(cover, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, modifier)
}

@Composable
private fun CoverNoteBox(
    cover: GitCoverNoteEvent,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BannerShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), BannerShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GitCoverNoteHeader(cover, accountViewModel)

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
    }
}

/** The "Cover note · author · time" line shared by the banner and the standalone card. */
@Composable
fun GitCoverNoteHeader(
    cover: GitCoverNoteEvent,
    accountViewModel: AccountViewModel,
    showAuthor: Boolean = true,
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
    }
}
