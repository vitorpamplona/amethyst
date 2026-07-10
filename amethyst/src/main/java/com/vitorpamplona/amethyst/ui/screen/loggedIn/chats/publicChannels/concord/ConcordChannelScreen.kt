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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The chat screen of one Concord Channel. Messages come from the community
 * session's decrypted, ordered flow (not LocalCache notes); posting derives the
 * channel plane key and publishes an encrypted wrap to the community's relays.
 *
 * Mounts [ConcordChannelSubscription] so the channel's plane stays live while the
 * screen is foregrounded (and so a just-opened channel re-subscribes once its
 * Control Plane folds).
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
    val session = remember(account, communityId) { account.concordSessions.sessionFor(communityId) }
    val channel = remember(account, communityId, channelId) { LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelId)) }

    val messages by (session?.messagesFlow(channelId) ?: remember { MutableStateFlow(emptyList()) })
        .collectAsStateWithLifecycle()

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
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages.asReversed(), key = { it.id }) { message ->
                    val mine = message.author == account.signer.pubKey
                    ConcordMessageBubble(
                        author = message.author,
                        content = message.content,
                        mine = mine,
                        accountViewModel = accountViewModel,
                    )
                }
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
private fun ConcordMessageBubble(
    author: String,
    content: String,
    mine: Boolean,
    accountViewModel: AccountViewModel,
) {
    val user = remember(author) { LocalCache.getOrCreateUser(author) }
    val name by observeUserName(user, accountViewModel)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (!mine) {
                    Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text(content, style = MaterialTheme.typography.bodyMedium)
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
