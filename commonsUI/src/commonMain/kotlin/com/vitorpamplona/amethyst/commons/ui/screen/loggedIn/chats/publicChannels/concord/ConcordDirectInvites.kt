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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.ConcordDirectInviteSendResult
import com.vitorpamplona.amethyst.commons.model.ConcordInviteResult
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordDirectInviteView
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_accept
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_accept_failed
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_catch_up
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_decline
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_expired
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_explainer
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed_banned
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed_loading
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed_member
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_from
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_hint
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_send
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_sent
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_title
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invites_title
import com.vitorpamplona.amethyst.commons.resources.concord_home_title
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_banned
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_expired
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_invalid
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_not_saved
import com.vitorpamplona.amethyst.commons.ui.components.ConcordInvitePreviewRow
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

/**
 * "Invite by npub" (CORD-05 §6): pick a person with the app's ordinary user typeahead (cache, relay
 * search, NIP-05, a pasted npub/nprofile), then hand them the community's keys as a Direct Invite —
 * a giftwrap to their inbox relays carrying only the private channels their roles grant.
 */
@Composable
fun ConcordDirectInviteDialog(
    communityId: String,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<User?>(null) }
    var sending by remember { mutableStateOf(false) }
    val userSuggestions =
        remember(accountViewModel) {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    LaunchedEffect(query) { userSuggestions.processCurrentWord(query) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringRes(Res.string.concord_direct_invite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.concord_direct_invite_explainer), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        picked = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !sending,
                    label = { Text(stringRes(Res.string.concord_direct_invite_hint)) },
                )
                if (picked == null && query.length > 2) {
                    ShowUserSuggestionList(
                        userSuggestions = userSuggestions,
                        onSelect = { user ->
                            picked = user
                            query = user.toBestDisplayName()
                        },
                        accountViewModel = accountViewModel,
                        modifier = SuggestionListDefaultHeightChat,
                        itemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        showDividers = false,
                        contentPadding = PaddingValues(0.dp),
                    )
                }
            }
        },
        confirmButton = {
            val target = picked
            TextButton(
                enabled = target != null && !sending,
                onClick = {
                    if (target == null) return@TextButton
                    sending = true
                    scope.launch {
                        try {
                            val result = accountViewModel.account.concord.sendConcordDirectInvite(communityId, target.pubkeyHex)
                            accountViewModel.toastManager.toast(Res.string.concord_direct_invite_title, sendResultMessage(result))
                            if (result == ConcordDirectInviteSendResult.SENT) onDismiss()
                        } finally {
                            sending = false
                        }
                    }
                },
            ) {
                Text(stringRes(Res.string.concord_direct_invite_send, picked?.toBestDisplayName() ?: "…"))
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

private fun sendResultMessage(result: ConcordDirectInviteSendResult) =
    when (result) {
        ConcordDirectInviteSendResult.SENT -> Res.string.concord_direct_invite_sent
        ConcordDirectInviteSendResult.ROSTER_NOT_LOADED -> Res.string.concord_direct_invite_failed_loading
        ConcordDirectInviteSendResult.RECIPIENT_BANNED -> Res.string.concord_direct_invite_failed_banned
        ConcordDirectInviteSendResult.NOT_MEMBER, ConcordDirectInviteSendResult.NOT_WRITEABLE -> Res.string.concord_direct_invite_failed_member
        ConcordDirectInviteSendResult.INVALID_RECIPIENT, ConcordDirectInviteSendResult.NOT_DELIVERED -> Res.string.concord_direct_invite_failed
    }

/**
 * The Direct Invites waiting for this account (CORD-05 §6), as cards with Accept / Decline — shown
 * at the top of the Concord communities list. Renders nothing when there are none.
 *
 * Opening the hub sweeps the inbox relays once; wraps the DM pipeline sees arrive on their own.
 * The preview is the bundle's own name and a robohash of the community id — **no** icon fetch, no
 * relay connection to the community, no Join happens before the user taps Accept. The sender is
 * shown by whatever name the cache already has, without fetching their profile.
 */
@Composable
fun ConcordPendingDirectInvites(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val concord = accountViewModel.account.concord
    LaunchedEffect(concord) { runCatching { concord.refreshConcordDirectInvites() } }

    val invites by concord.pendingConcordDirectInvites.collectAsStateWithLifecycle()
    if (invites.isEmpty()) return

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(Res.string.concord_direct_invites_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        invites.forEach { invite ->
            ConcordDirectInviteCard(invite, accountViewModel, nav)
        }
    }
}

@Composable
private fun ConcordDirectInviteCard(
    invite: ConcordDirectInviteView,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scope = rememberCoroutineScope()
    var working by remember(invite.wrapId) { mutableStateOf(false) }
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val senderName = remember(invite.sender) { LocalCache.checkGetOrCreateUser(invite.sender)?.toBestDisplayName() ?: invite.sender.take(12) }

    val subtitle =
        when {
            invite.expired -> stringRes(Res.string.concord_direct_invite_expired)
            invite.catchUp -> stringRes(Res.string.concord_direct_invite_catch_up, invite.channelNames.joinToString(", ") { "#$it" })
            else -> stringRes(Res.string.concord_direct_invite_from, senderName)
        }

    ElevatedCard(Modifier.fillMaxWidth()) {
        ConcordInvitePreviewRow(
            robotSeed = invite.communityId,
            title = invite.name.ifBlank { stringRes(Res.string.concord_home_title) },
            subtitle = subtitle,
            accountViewModel = accountViewModel,
            autoPlayGif = autoPlayGif,
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            OutlinedButton(
                enabled = !working,
                onClick = { accountViewModel.account.concord.declineConcordDirectInvite(invite.wrapId) },
            ) {
                Text(stringRes(Res.string.concord_direct_invite_decline))
            }
            Button(
                enabled = !working && !invite.expired,
                onClick = {
                    working = true
                    scope.launch {
                        try {
                            when (val result = accountViewModel.account.concord.acceptConcordDirectInvite(invite.wrapId)) {
                                is ConcordInviteResult.Joined -> nav.nav(Route.ConcordServer(result.communityId))
                                is ConcordInviteResult.Expired -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_expired)
                                is ConcordInviteResult.Banned -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_banned)
                                is ConcordInviteResult.InvalidLink -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_invalid)
                                is ConcordInviteResult.NotSaved -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_not_saved)
                                else -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_direct_invite_accept_failed)
                            }
                        } finally {
                            working = false
                        }
                    }
                },
            ) {
                Text(stringRes(Res.string.concord_direct_invite_accept))
            }
        }
    }
}
