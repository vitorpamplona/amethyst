/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.screen.DVMStatusView
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90ContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90StatusFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryRequestEvent

@Composable
fun NIP90ContentDiscoveryScreen(
    DVMID: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var requestID = ""
    val thread =
        Thread {
            try {
                NIP90ContentDiscoveryRequestEvent.create(DVMID, accountViewModel.account.signer) {
                    Client.send(it)
                    requestID = it.id
                    println("REQUESTID: " + requestID)
                    LocalCache.justConsume(it, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    thread.start()
    thread.join()

    val resultFeedViewModel: NostrNIP90ContentDiscoveryFeedViewModel =
        viewModel(
            key = "NostrNIP90ContentDiscoveryFeedViewModel",
            factory = NostrNIP90ContentDiscoveryFeedViewModel.Factory(accountViewModel.account, dvmkey = DVMID, requestid = requestID),
        )

    val statusFeedViewModel: NostrNIP90StatusFeedViewModel =
        viewModel(
            key = "NostrNIP90StatusFeedViewModel",
            factory = NostrNIP90StatusFeedViewModel.Factory(accountViewModel.account, dvmkey = DVMID, requestid = requestID),
        )

    val userState by accountViewModel.account.decryptBookmarks.observeAsState() // TODO

    LaunchedEffect(userState) {
        resultFeedViewModel.invalidateData()
    }

    RenderNostrNIP90ContentDiscoveryScreen(DVMID, accountViewModel, nav, resultFeedViewModel, statusFeedViewModel)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderNostrNIP90ContentDiscoveryScreen(
    DVMID: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    resultFeedViewModel: NostrNIP90ContentDiscoveryFeedViewModel,
    statusFeedViewModel: NostrNIP90StatusFeedViewModel,
) {
    Column(Modifier.fillMaxHeight()) {
        val pagerState = rememberPagerState { 2 }
        val coroutineScope = rememberCoroutineScope()
        // TODO Render a nice header with image and DVM name from the id

       /* if (DVMID != null) {
            LoadNote(baseNoteHex = DVMID, accountViewModel = accountViewModel) {
                if (it != null) {
                    NoteCompose(baseNote = it, quotesLeft = 0, accountViewModel = accountViewModel ) {

                    }
                }
                if (it != null) {
                    Text(text = (it.event as AppDefinitionEvent).content())
                } else {
                    Text(text = "yo")
                }
            }
        } */

        // TODO only show this when the feed below hasnt loaded. I this possible?
        // TODO render this more as a status label rather than a note

        DVMStatusView(
            statusFeedViewModel,
            null,
            enablePullRefresh = false,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        HorizontalPager(state = pagerState) {
            RefresheableFeedView(
                resultFeedViewModel,
                null,
                enablePullRefresh = false,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
