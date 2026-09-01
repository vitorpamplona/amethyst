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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_invite_links_created
import com.vitorpamplona.amethyst.commons.resources.concord_invite_links_empty
import com.vitorpamplona.amethyst.commons.resources.concord_invite_links_unreadable
import com.vitorpamplona.amethyst.commons.resources.concord_invite_revoke_action
import com.vitorpamplona.amethyst.commons.resources.concord_invite_revoke_confirm
import com.vitorpamplona.amethyst.commons.resources.concord_invite_revoke_explainer
import com.vitorpamplona.amethyst.commons.resources.concord_invite_revoke_title
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListEntry
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/** What the screen is currently showing. The unreadable case is deliberately not "empty" — see below. */
private sealed interface LinksState {
    data object Loading : LinksState

    data class Loaded(
        val links: List<ConcordInviteListEntry>,
    ) : LinksState

    /**
     * The kind-13303 list could not be read. Distinct from an empty list on purpose: rendering
     * "no links yet" here would tell a creator that the link they came to kill does not exist.
     */
    data object Unreadable : LinksState
}

/**
 * Every invite link this account minted for one community, with the ability to retire one
 * (CORD-05 §2).
 *
 * The list is the creator's own kind-13303 Invite List, which is where a link's `signer_sk` lives —
 * so this shows only links *this account* minted, from any of its devices. Another admin's links are
 * invisible here and un-revokable from here, because the secret that authors their coordinate was
 * never ours. That is a property of the protocol, not a gap in the screen.
 *
 * Fetched on entry rather than collected from a flow: nothing subscribes to kind 13303 (it is
 * bookkeeping the user never sees), so there is no cache to observe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordInviteLinksScreen(
    communityId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    var state by remember(communityId) { mutableStateOf<LinksState>(LinksState.Loading) }
    var reloads by remember(communityId) { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf<ConcordInviteListEntry?>(null) }
    var revoking by remember { mutableStateOf(false) }

    LaunchedEffect(communityId, reloads) {
        state = LinksState.Loading
        state = account.concord.listConcordInviteLinks(communityId)?.let { LinksState.Loaded(it) } ?: LinksState.Unreadable
    }

    val communityName =
        remember(account, communityId) {
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == communityId }
                ?.name
                .orEmpty()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringRes(R.string.concord_invite_links_title), fontWeight = FontWeight.Bold)
                        if (communityName.isNotBlank()) {
                            Text(communityName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is LinksState.Loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is LinksState.Unreadable -> CenteredMessage(padding, stringRes(Res.string.concord_invite_links_unreadable))

            is LinksState.Loaded ->
                if (current.links.isEmpty()) {
                    CenteredMessage(padding, stringRes(Res.string.concord_invite_links_empty))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        items(current.links, key = { it.token }) { link ->
                            InviteLinkRow(
                                link = link,
                                enabled = !revoking,
                                onCopy = { scope.launch { clipboard.setText(link.url) } },
                                onRevoke = { confirming = link },
                            )
                            HorizontalDivider()
                        }
                    }
                }
        }
    }

    confirming?.let { link ->
        AlertDialog(
            onDismissRequest = { if (!revoking) confirming = null },
            title = { Text(stringRes(Res.string.concord_invite_revoke_title)) },
            text = { Text(stringRes(Res.string.concord_invite_revoke_explainer)) },
            confirmButton = {
                TextButton(
                    enabled = !revoking,
                    onClick = {
                        revoking = true
                        scope.launch {
                            try {
                                val ok = account.concord.revokeConcordInvite(communityId, link.token)
                                accountViewModel.toastManager.toast(
                                    R.string.concord_invite_links_title,
                                    if (ok) R.string.concord_invite_revoked_ok else R.string.concord_invite_revoked_failed,
                                )
                                // Re-read either way: on success the link is gone from the list, and on
                                // failure the list is the only thing that can say whether it changed.
                                reloads++
                            } finally {
                                revoking = false
                                confirming = null
                            }
                        }
                    },
                ) {
                    Text(stringRes(Res.string.concord_invite_revoke_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !revoking, onClick = { confirming = null }) {
                    Text(stringRes(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CenteredMessage(
    padding: PaddingValues,
    message: String,
) {
    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            // This Box sits on the bare window background, so LocalContentColor is still the M3
            // default black — see the sibling invite screen, where that made the text invisible.
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun InviteLinkRow(
    link: ConcordInviteListEntry,
    enabled: Boolean,
    onCopy: () -> Unit,
    onRevoke: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            // The token prefix is what tells two links to the same community apart; their URLs share
            // a long prefix, so they are useless as labels until well past where the row wraps.
            Text(link.token.take(8), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringRes(Res.string.concord_invite_links_created, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(link.createdAt * 1000))),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(link.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(enabled = enabled, onClick = { menuOpen = true }) {
            SymbolIcon(symbol = MaterialSymbols.MoreVert, contentDescription = stringRes(R.string.more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.copy_to_clipboard)) },
                onClick = {
                    menuOpen = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.concord_invite_revoke_action), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onRevoke()
                },
            )
        }
    }
}
