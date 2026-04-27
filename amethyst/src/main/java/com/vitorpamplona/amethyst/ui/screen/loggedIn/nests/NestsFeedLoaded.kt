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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.RenderLiveActivityThumb
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.NestActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.NestBridge
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NestsFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                NestFeedCard(
                    baseNote = item,
                    modifier = Modifier.fillMaxWidth(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

/**
 * Audio-rooms list card. Mirrors [RenderLiveActivityThumb] visually but
 * routes a tap straight into [NestActivity] when the underlying event
 * is a [MeetingSpaceEvent], instead of the thread view that the generic
 * `ChannelCardCompose` → `ClickableNote` chain would otherwise open.
 */
@Composable
private fun NestFeedCard(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val meetingEvent = baseNote.event as? MeetingSpaceEvent ?: return
    val context = LocalContext.current

    val onClick =
        remember(meetingEvent) {
            {
                val service = meetingEvent.service()
                val endpoint = meetingEvent.endpoint()
                val dTag = meetingEvent.address().dTag
                if (!service.isNullOrBlank() && !endpoint.isNullOrBlank() && dTag.isNotBlank()) {
                    NestBridge.set(accountViewModel)
                    NestActivity.launch(
                        context = context,
                        addressValue = meetingEvent.address().toValue(),
                        authBaseUrl = service,
                        endpoint = endpoint,
                        hostPubkey = meetingEvent.pubKey,
                        roomId = dTag,
                        kind = meetingEvent.kind,
                    )
                } else {
                    nav.nav { routeFor(baseNote, accountViewModel.account) }
                }
            }
        }

    Column(modifier.clickable(onClick = onClick)) {
        Column(StdPadding) {
            RenderLiveActivityThumb(baseNote, accountViewModel, nav)
        }
    }
}
