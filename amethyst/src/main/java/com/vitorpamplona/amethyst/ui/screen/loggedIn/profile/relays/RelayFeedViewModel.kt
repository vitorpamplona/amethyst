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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.relays

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.RelayInfo
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.feeds.InvalidatableContent
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.quartz.nip02FollowList.ReadWrite
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
    var currentJob: Job? = null

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            refreshSuspended()
        }
    }

    fun refreshSuspended() {
        try {
            isRefreshing.value = true

            currentUser?.let {
                val newList = mergeRelays(it.relaysBeingUsed, it.latestContactList?.relays())
                _feedContent.update { newList }
            }
        } finally {
            isRefreshing.value = false
        }
    }

    fun mergeRelays(
        relaysBeingUsed: Map<String, RelayInfo>,
        relays: Map<String, ReadWrite>?,
    ): List<RelayInfo> {
        val userRelaysBeingUsed = relaysBeingUsed.map { it.value }

        val currentUserRelays =
            relays?.mapNotNull {
                val url = RelayUrlFormatter.normalize(it.key)
                if (url !in relaysBeingUsed) {
                    RelayInfo(url, 0, 0)
                } else {
                    null
                }
            } ?: emptyList()

        return (userRelaysBeingUsed + currentUserRelays).sortedWith(order)
    }

    fun subscribeTo(user: User) {
        if (currentUser != user) {
            currentUser = user

            currentJob?.cancel()
            currentJob =
                viewModelScope.launch {
                    combine(currentUser!!.flow().relays.stateFlow, currentUser!!.flow().relayInfo.stateFlow) { relays, relayInfo ->
                        mergeRelays(relays.user.relaysBeingUsed, relayInfo.user.latestContactList?.relays())
                    }.debounce(1000)
                        .collect { newList ->
                            _feedContent.update { newList }
                        }
                }

            invalidateData()
        }
    }

    fun unsubscribeTo(user: User) {
        if (currentUser == user) {
            currentUser = null
            currentJob?.cancel()
            invalidateData()
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
        currentJob?.cancel()
        super.onCleared()
    }
}
