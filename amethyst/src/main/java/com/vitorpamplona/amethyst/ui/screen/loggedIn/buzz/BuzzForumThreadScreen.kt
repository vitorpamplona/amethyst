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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.delay

/**
 * The detail view of a Buzz **forum thread**: the root post (kind 45001) and its comment
 * replies (kind 45003). Replies compose through the same rich chat composer everything else
 * uses ([EditFieldRow] + [ForumReplyNewMessageViewModel]), which just swaps the built event for
 * a kind-45003 [ForumCommentEvent]. Kept apart from the generic note thread screen because Buzz
 * forum comments use NIP-10 `e` tags (not the kind-1111 NIP-22 shape), so they need their own
 * root-scoped fetch.
 */
@Composable
fun BuzzForumThreadScreen(
    channelId: String,
    relayUrl: String,
    rootId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val rootNote = remember(rootId) { LocalCache.getOrCreateNote(rootId) }
    val channel = remember(channelId, relay) { LocalCache.getOrCreateRelayGroupChannel(GroupId(channelId, relay)) }

    // The replies to this root. Seed from cache (the forum-list REQ already pulls 45003 for the
    // channel), then live-subscribe by the root `e` tag so replies arrive while the thread is open.
    val replies = remember(rootId) { mutableStateListOf<ForumCommentEvent>() }
    val seen = remember(rootId) { HashSet<HexKey>() }
    val replyFilter =
        remember(rootId) {
            Filter(kinds = listOf(ForumCommentEvent.KIND), tags = mapOf("e" to listOf(rootId)))
        }

    fun accept(event: ForumCommentEvent) {
        if (event.threadRoot() != rootId && event.replyTo() != rootId) return
        if (!seen.add(event.id)) return
        val at = replies.indexOfFirst { it.createdAt > event.createdAt }
        if (at < 0) replies.add(event) else replies.add(at, event)
    }

    // Bumped after we post a reply. The relay stores our reply but doesn't re-deliver it on our already-open
    // subscription, so a bump restarts the REQ — a fresh fetch returns every reply, including the new one.
    var refreshKey by remember(rootId) { mutableIntStateOf(0) }

    LaunchedEffect(rootId, refreshKey) {
        if (refreshKey > 0) delay(600) // let our just-sent reply persist on the relay before re-fetching
        LocalCache.filter(replyFilter).forEach { (it.event as? ForumCommentEvent)?.let(::accept) }
        accountViewModel.account.client.subscribeAsFlow(relay, replyFilter).collect { events ->
            events.filterIsInstance<ForumCommentEvent>().forEach(::accept)
        }
    }

    val canPost = channel.canPost(accountViewModel.userProfile().pubkeyHex)

    val replyModel: ForumReplyNewMessageViewModel = viewModel(key = "forum-reply-$rootId")
    replyModel.init(accountViewModel)
    replyModel.loadForumThread(channel, rootId)

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.buzz_forum_thread_title), nav) },
        bottomBar = {
            if (canPost) {
                // Reuse the full chat composer; a send publishes the kind-45003 reply and then bumps
                // refreshKey to re-fetch (the relay doesn't echo our reply on the open subscription).
                EditFieldRow(replyModel, accountViewModel, onSendNewMessage = { refreshKey++ }, nav)
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "root") {
                ForumPostBody(rootNote.event as? ForumPostEvent, rootNote.author?.pubkeyHex ?: rootId, accountViewModel, nav)
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(replies, key = { it.id }) { reply ->
                ForumReplyRow(reply, accountViewModel, nav)
                HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ForumPostBody(
    event: ForumPostEvent?,
    authorPubkey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val author = remember(authorPubkey) { LocalCache.getOrCreateUser(authorPubkey) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserPicture(author, Size35dp, accountViewModel = accountViewModel, nav = nav)
            UsernameDisplay(author, accountViewModel = accountViewModel, fontWeight = FontWeight.SemiBold)
        }
        Text(text = event?.body().orEmpty(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ForumReplyRow(
    reply: ForumCommentEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val author = remember(reply.pubKey) { LocalCache.getOrCreateUser(reply.pubKey) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(author, Size35dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            UsernameDisplay(author, accountViewModel = accountViewModel)
            Text(text = reply.body(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
