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
package com.vitorpamplona.amethyst.ui.screen

import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.RelayInfo
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.ui.actions.relays.AllRelayListView
import com.vitorpamplona.amethyst.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.note.RelayCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.ammolite.relays.BundledUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class RelayFeedViewModel :
    ViewModel(),
    InvalidatableContent {
    val order =
        compareByDescending<RelayInfo> { it.lastEvent }
            .thenByDescending { it.counter }
            .thenBy { it.url }

    private val _feedContent = MutableStateFlow<List<RelayInfo>>(emptyList())
    val feedContent = _feedContent.asStateFlow()

    var currentUser: User? = null

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) { refreshSuspended() }
    }

    fun refreshSuspended() {
        val beingUsed = currentUser?.relaysBeingUsed?.values ?: emptyList()
        val beingUsedSet = currentUser?.relaysBeingUsed?.keys ?: emptySet()

        val newRelaysFromRecord =
            currentUser?.latestContactList?.relays()?.entries?.mapNotNull {
                if (it.key !in beingUsedSet) {
                    RelayInfo(it.key, 0, 0)
                } else {
                    null
                }
            }
                ?: emptyList()

        val newList = (beingUsed + newRelaysFromRecord).sortedWith(order)

        _feedContent.update { newList }
    }

    val listener: (UserState) -> Unit = { invalidateData() }

    fun subscribeTo(user: User) {
        if (currentUser != user) {
            currentUser = user
            user.live().relays.observeForever(listener)
            user.live().relayInfo.observeForever(listener)
            invalidateData()
        }
    }

    fun unsubscribeTo(user: User) {
        if (currentUser == user) {
            user.live().relays.removeObserver(listener)
            user.live().relayInfo.removeObserver(listener)
            currentUser = null
        }
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO)

    override fun invalidateData(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing) {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            refreshSuspended()
        }
    }

    override fun onCleared() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        bundler.cancel()
        super.onCleared()
    }
}

@Composable
fun RelayFeedView(
    viewModel: RelayFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    enablePullRefresh: Boolean = true,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    var wantsToAddRelay by remember { mutableStateOf("") }

    if (wantsToAddRelay.isNotEmpty()) {
        AllRelayListView({ wantsToAddRelay = "" }, wantsToAddRelay, accountViewModel, nav = nav)
    }

    RefresheableBox(viewModel, enablePullRefresh) {
        val listState = rememberLazyListState()

        LazyColumn(
            contentPadding = FeedPadding,
            state = listState,
        ) {
            itemsIndexed(feedState, key = { _, item -> item.url }) { _, item ->
                RelayCompose(
                    item,
                    accountViewModel = accountViewModel,
                    onAddRelay = { wantsToAddRelay = item.url },
                    onRemoveRelay = { wantsToAddRelay = item.url },
                )
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}
