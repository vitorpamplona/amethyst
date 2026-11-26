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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * Where a legacy NIP-34 reply (kind 1622, replaced by NIP-22 comments) sits: the issue or patch
 * its `root` `e` names, as the one-line [GitItemReference] (state, title, repository), or, until
 * that item loads, the repository its `a` names. Nothing when [shownParent], the quote above the
 * reply, is that item or the repository, which already say both.
 */
@Suppress("DEPRECATION")
@Composable
fun GitReplyContext(
    event: GitReplyEvent,
    shownParent: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val rootId = remember(event) { event.rootIssueOrPatch() }
    val repository = remember(event) { event.repository()?.let { Address(it.kind, it.pubKeyHex, it.dTag) } }

    // The quote above already is the item, or the repository (a reply on the repository itself).
    if (shownParent != null && (shownParent.idHex == rootId || shownParent.event is GitRepositoryEvent || (shownParent as? AddressableNote)?.address == repository)) return

    if (rootId != null) {
        LoadNote(rootId) { root ->
            val item = root?.event
            if (item != null && item !is GitRepositoryEvent) {
                GitItemReference(root, nav, bordered = true)
            } else if (repository != null) {
                GitRepositoryLine(repository, accountViewModel, nav)
            }
        }
    } else if (repository != null) {
        GitRepositoryLine(repository, accountViewModel, nav)
    }
}

/** The repository at [address] as one line, its name once its announcement loads, opening it. */
@Composable
private fun GitRepositoryLine(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier = Modifier.clickable { nav.nav(Route.GitRepository(address)) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Code,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        LoadAddressableNote(address) { repoNote ->
            val repo = repoNote?.let { observeNoteEvent<GitRepositoryEvent>(it, accountViewModel).value }
            Text(
                text = repo?.name()?.ifBlank { null } ?: address.dTag,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
