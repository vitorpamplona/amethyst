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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.screen.FeedEmptywithStatus
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90ContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90StatusFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableBox
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
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
fun RenderNostrNIP90ContentDiscoveryScreen(
    dvmID: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    resultFeedViewModel: NostrNIP90ContentDiscoveryFeedViewModel,
    statusFeedViewModel: NostrNIP90StatusFeedViewModel,
) {
    Column(Modifier.fillMaxHeight()) {
        val pagerState = rememberPagerState { 2 }
        val coroutineScope = rememberCoroutineScope()

        // TODO this now shows the first status update but there might be a better way
        var dvmStatus = stringResource(R.string.dvm_no_status)

        val thread =
            Thread {
                var count = 0
                while (resultFeedViewModel.localFilter.feed().isEmpty()) {
                    try {
                        if (statusFeedViewModel.localFilter.feed().isNotEmpty()) {
                            statusFeedViewModel.localFilter.feed()[0].event?.let { dvmStatus = it.content() }
                            println(dvmStatus)
                            break
                        } else if (count > 1000) {
                            // Might not be the best way, but we want to avoid hanging in the loop forever
                        } else {
                            count++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        thread.start()
        thread.join()

        // TODO Maybe render a nice header with image and DVM name from the dvmID
        // TODO How do we get the event information here?, LocalCache.checkGetOrCreateNote() returns note but event is empty
        // TODO oterwise we have the NIP89 info in (note.event as AppDefinitionEvent).appMetaData()
        // Text(text = dvminfo)

        HorizontalPager(state = pagerState) {
            RefresheableBox(resultFeedViewModel, false) {
                SaveableFeedState(resultFeedViewModel, null) { listState ->
                    RenderFeedState(
                        resultFeedViewModel,
                        accountViewModel,
                        listState,
                        nav,
                        null,
                        onEmpty = {
                            // TODO Maybe also show some dvm image/text while waiting for the notes in this custom component
                            FeedEmptywithStatus(status = dvmStatus) {
                            }
                        },
                    )
                }
            }
        }
    }
}
