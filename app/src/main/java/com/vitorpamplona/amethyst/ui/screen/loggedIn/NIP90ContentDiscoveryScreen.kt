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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90ContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView

@Composable
fun NIP90ContentDiscoveryScreen(
    DVMID: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val resultFeedViewModel: NostrNIP90ContentDiscoveryFeedViewModel =
        viewModel(
            key = "NostrNIP90ContentDiscoveryFeedViewModel",
            factory = NostrNIP90ContentDiscoveryFeedViewModel.Factory(accountViewModel.account),
        )

    val userState by accountViewModel.account.decryptBookmarks.observeAsState()

    LaunchedEffect(userState) {
        resultFeedViewModel.invalidateData()
    }

    RenderNostrNIP90ContentDiscoveryScreen(DVMID, accountViewModel, nav, resultFeedViewModel)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderNostrNIP90ContentDiscoveryScreen(
    DVMID: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    resultFeedViewModel: NostrNIP90ContentDiscoveryFeedViewModel,
) {
    Column(Modifier.fillMaxHeight()) {
        val pagerState = rememberPagerState { 2 }
        val coroutineScope = rememberCoroutineScope()

        if (DVMID != null) {
            Text(text = "Debug: DVM KEY:\n " + DVMID)
        }

        if (DVMID != null) {
            // TODO 1 Send KIND 5300 Event with p tag = DVMID (crashes, because cant map to event)

            /*val signer: NostrSigner = NostrSignerInternal(accountViewModel.account.keyPair)
            println(accountViewModel.account.keyPair.pubKey.toHexKey())
            NIP90ContentDiscoveryRequestEvent.create(DVMID, signer) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            } */

            // var keyPair = accountViewModel.account.keyPair

            // TODO 2 PARSE AND LOAD RESULTS FROM KIND 6300 REPLY to resultfeedmodel (RN this doesnt show events)

            // TODO 3 Render Results (hopefully works when 2 is working)

            HorizontalPager(state = pagerState) {
                RefresheableFeedView(
                    resultFeedViewModel,
                    null,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}
