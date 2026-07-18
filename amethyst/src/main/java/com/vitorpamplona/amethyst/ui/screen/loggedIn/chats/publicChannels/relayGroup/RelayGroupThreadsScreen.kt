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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteReplyCount
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent

/**
 * A group's forum-style threads (kind 11) — the secondary content type kept out of
 * the kind-9 chat feed. Streams the group's threads + their comments via
 * [RelayGroupOpenThreadsSubscription]; tapping a thread opens the generic thread view
 * ([Route.Note]) with its comment tree. Members can start a new thread.
 */
@Composable
fun RelayGroupThreadsScreen(
    id: HexKey,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val channelId = remember(id, relay) { GroupId(id, relay) }

    LoadRelayGroupChannel(channelId, accountViewModel) { channel ->
        RelayGroupThreads(channel, accountViewModel, nav)
    }
}

@Composable
private fun RelayGroupThreads(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayGroupOpenThreadsSubscription(channel, accountViewModel.dataSources().relayGroupOpenThreads, accountViewModel)

    val threads by channel.threads.collectAsStateWithLifecycle()

    // Only members can post a thread (the relay rejects a non-member's kind-11), so the
    // compose FAB is hidden for everyone else.
    val canPost = channel.membershipOf(accountViewModel.userProfile().pubkeyHex).isMember()

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Column {
                        Text(
                            text = stringRes(R.string.relay_group_threads_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = channel.toBestDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                popBack = nav::popBack,
            )
        },
        floatingActionButton = {
            if (canPost) {
                FloatingActionButton(
                    onClick = {
                        nav.nav(
                            Route.NewShortNote(
                                groupThreadId = channel.groupId.id,
                                groupThreadRelayUrl = channel.groupId.relayUrl.url,
                            ),
                        )
                    },
                    shape = CircleShape,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(R.string.relay_group_thread_new),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) { padding ->
        if (threads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringRes(R.string.relay_group_threads_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                itemsIndexed(threads, key = { _, thread -> thread.idHex }) { index, thread ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    ThreadRow(thread, accountViewModel, nav) { nav.nav(Route.Note(thread.idHex)) }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    // Observe the reply count so a kind-1111 comment arriving on an already-listed thread
    // bumps it live (channel.threads only re-emits on add/remove of a thread).
    val replyCount by observeNoteReplyCount(thread, accountViewModel)
    val event = thread.event as? ThreadEvent
    val title = event?.title()?.takeIf { it.isNotBlank() } ?: stringRes(R.string.relay_group_thread_untitled)
    val preview =
        event
            ?.content
            ?.replace('\n', ' ')
            ?.trim()
            .orEmpty()
    val author = thread.author

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (author != null) {
            UserPicture(author, Size35dp, accountViewModel = accountViewModel, nav = nav)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (author != null) {
                    UsernameDisplay(author, accountViewModel = accountViewModel)
                }
                Icon(
                    symbol = MaterialSymbols.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "$replyCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
