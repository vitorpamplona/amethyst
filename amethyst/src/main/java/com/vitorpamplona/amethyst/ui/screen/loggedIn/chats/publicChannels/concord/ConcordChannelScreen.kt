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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The chat screen of one Concord Channel. Messages are real Notes in [LocalCache]
 * attached to the channel (landed on decrypt by
 * [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager]), so the
 * feed reuses the shared [RefreshingChatroomFeedView] — reactions, replies, zaps
 * and OTS render exactly as in every other chat.
 *
 * Only the send path is Concord-specific: it derives the channel plane key and
 * publishes an encrypted wrap to the community relays
 * ([com.vitorpamplona.amethyst.model.Account.sendConcordChannelMessage]).
 * [ConcordChannelSubscription] keeps the channel's plane live while foregrounded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordChannelScreen(
    communityId: String,
    channelId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

    val account = accountViewModel.account
    val channel = remember(account, communityId, channelId) { LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelId)) }

    val feedViewModel: ChannelFeedViewModel =
        viewModel(
            key = channel.channelId.toKey() + "ConcordFeedViewModel",
            factory = ChannelFeedViewModel.Factory(channel, account),
        )
    WatchLifecycleAndUpdateModel(feedViewModel)

    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(channel.toBestDisplayName(), fontWeight = FontWeight.Bold, maxLines = 1)
                        channel.communityName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                RefreshingChatroomFeedView(
                    feedContentState = feedViewModel.feedState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    routeForLastRead = "Concord/$communityId/$channelId",
                    onWantsToReply = {},
                    onWantsToEditDraft = {},
                )
            }

            if (channel.canPost()) {
                ConcordComposer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            draft = ""
                            scope.launch { account.sendConcordChannelMessage(communityId, channelId, text) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConcordComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringRes(com.vitorpamplona.amethyst.R.string.reply_here)) },
            maxLines = 5,
        )
        Box(Modifier.padding(start = 6.dp)) {
            IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                SymbolIcon(
                    symbol = MaterialSymbols.AutoMirrored.Send,
                    contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.send),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
