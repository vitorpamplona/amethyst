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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.minichat

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.LocalSuppressReplyToNoteId
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelHistorySubAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelHistorySubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The minichat ("chat within a chat") of a single chat message: the message pinned at
 * top, then its kind-1111 thread replies as chat bubbles, with a composer that always
 * posts a kind-1111 rooted at that message (flat — replying inside a minichat doesn't
 * spawn sub-threads). Opened from the "N replies" chip on any chat message.
 *
 * Self-sufficient regardless of where it's opened from: it mounts its own datasource so
 * replies keep flowing (the Concord plane subscription when the root is a Concord message;
 * a relay reply subscription for public chats), and drives the list from a reactive
 * [MinichatFeedViewModel] so new replies appear (and auto-scroll into view) live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinichatScreen(
    rootId: String,
    concordCommunityId: String? = null,
    concordChannelId: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val rootNote = remember(rootId) { LocalCache.getOrCreateNote(rootId) }

    // Resolve the Concord channel from the (loaded) root note's gatherer, else from the ids threaded
    // in by the reply that opened this minichat. The latter is what lets a reply-to-an-unloaded-parent
    // still pick the plane + relays: the reply arrived over the channel plane, so its channel id is known.
    val concordChannel = remember(rootNote) { rootNote.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } }
    val communityId = concordCommunityId ?: concordChannel?.channelId?.communityId
    val channelId = concordChannelId ?: concordChannel?.channelId?.channelId
    val isConcord = communityId != null && channelId != null

    // Datasource: keep the thread replies flowing no matter the entry point. Concord replies arrive
    // over the channel plane, so mount the plane subscription plus this channel's backward-history
    // pager and page it until the parent message loads (see [ConcordMinichatBackfillUntilRoot]);
    // public-chat (NIP-28/NIP-29) replies come over a relay REQ for this message's kind-1111 children.
    if (isConcord) {
        ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)
        ConcordChannelHistorySubscription(communityId!!, channelId!!, accountViewModel.dataSources().concordChannelHistory, accountViewModel)
        ConcordMinichatBackfillUntilRoot(rootNote, accountViewModel.dataSources().concordChannelHistory.history)
    }
    EventFinderFilterAssemblerSubscription(rootNote, accountViewModel)

    val feedViewModel: MinichatFeedViewModel =
        viewModel(
            key = rootId + "MinichatFeedViewModel",
            factory = MinichatFeedViewModel.Factory(rootNote, accountViewModel.account),
        )
    val replies by feedViewModel.replies.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    // Auto-scroll to the newest reply as they arrive (root is item 0, replies follow).
    LaunchedEffect(replies.size) {
        if (replies.isNotEmpty()) listState.animateScrollToItem(replies.size)
    }

    val composer = remember { TextFieldState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canPost by remember { derivedStateOf { composer.text.isNotBlank() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.chat_minichat_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
                actions = {
                    // For a Concord thread, always offer a jump to the full channel — the "chat room
                    // itself", where the whole timeline loads and (if you aren't a member yet) you can
                    // join. This is the way out when the pinned root is still backfilling or unavailable.
                    if (isConcord) {
                        IconButton(onClick = { nav.nav(Route.Concord(communityId!!, channelId!!)) }) {
                            SymbolIcon(symbol = MaterialSymbols.Forum, contentDescription = stringRes(R.string.concord_open_channel))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxHeight().padding(padding)) {
            // Every reply here is rooted at [rootNote], which is already pinned at the top — so
            // suppress the redundant reply-to-root preview each reply would otherwise render.
            CompositionLocalProvider(LocalSuppressReplyToNoteId provides rootId) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, true)) {
                    item("root") {
                        ChatroomMessageCompose(
                            baseNote = rootNote,
                            routeForLastRead = null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onWantsToReply = {},
                            onWantsToEditDraft = {},
                        )
                        HorizontalDivider()
                    }
                    items(replies, key = { it.idHex }) { reply ->
                        ChatroomMessageCompose(
                            baseNote = reply,
                            routeForLastRead = null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onWantsToReply = {},
                            onWantsToEditDraft = {},
                        )
                    }
                }
            }

            Column(modifier = EditFieldModifier) {
                ThinPaddingTextField(
                    state = composer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = EditFieldBorder,
                    placeholder = {
                        Text(
                            text = stringRes(R.string.reply_here),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    trailingIcon = {
                        ThinSendButton(
                            isActive = canPost,
                            modifier = EditFieldTrailingIconModifier,
                        ) {
                            val text = composer.text.toString().trim()
                            if (text.isNotEmpty()) {
                                composer.clearText()
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        accountViewModel.account.sendMinichatReply(rootNote, text)
                                    } catch (e: Exception) {
                                        launch(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )
            }
        }
    }
}

/**
 * Pages the Concord channel's backward history until the minichat's parent message loads (or the
 * relays are exhausted). Reaching a minichat from a reply notification can land on a parent that
 * isn't in the live tail; the reply itself pinned the channel context, so we can walk the plane
 * history back to fetch the parent — after which its whole kind-1111 thread projects normally. The
 * `!loading` gate serializes pages; once the root's event is present, or nothing is left, it latches off.
 */
@Composable
private fun ConcordMinichatBackfillUntilRoot(
    rootNote: Note,
    history: ConcordChannelHistorySubAssembler,
) {
    LaunchedEffect(rootNote, history) {
        combine(history.loadingMore, history.status) { loading, status ->
            rootNote.event == null && !loading && !status.exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect { history.advanceAll() }
    }
}
