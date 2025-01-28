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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.RelayInfo
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.ui.feeds.InvalidatableContent
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
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

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) { refreshSuspended() }
    }

    fun refreshSuspended() {
        try {
            isRefreshing.value = true

            val beingUsed = currentUser?.relaysBeingUsed?.values ?: emptyList()
            val beingUsedSet = currentUser?.relaysBeingUsed?.keys ?: emptySet()

            val newRelaysFromRecord =
                currentUser?.latestContactList?.relays()?.entries?.mapNotNullTo(HashSet()) {
                    val url = RelayUrlFormatter.normalize(it.key)
                    if (url !in beingUsedSet) {
                        RelayInfo(url, 0, 0)
                    } else {
                        null
                    }
                }
                    ?: emptyList()

            val newList = (beingUsed + newRelaysFromRecord).sortedWith(order)

            _feedContent.update { newList }
        } finally {
            isRefreshing.value = false
        }
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
