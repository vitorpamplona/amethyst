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
package com.vitorpamplona.amethyst.ui.actions.relays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.RelayPool
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class Kind3RelayListViewModel : ViewModel() {
    private lateinit var account: Account

    private val _relays = MutableStateFlow<List<RelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    var hasModified = false

    fun load(account: Account) {
        this.account = account
        clear()
        loadRelayDocuments()
    }

    fun create() {
        if (hasModified) {
            viewModelScope.launch(Dispatchers.IO) {
                account.saveKind3RelayList(relays.value)
                clear()
            }
        }
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _relays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    dirtyUrl = item.url,
                    onInfo = {
                        togglePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }
        }
    }

    fun clear() {
        hasModified = false
        _relays.update {
            var relayFile = account.userProfile().latestContactList?.relays()

            if (relayFile != null) {
                relayFile
                    .map {
                        val liveRelay = RelayPool.getRelay(it.key)
                        val localInfoFeedTypes =
                            account.localRelays
                                .filter { localRelay -> localRelay.url == it.key }
                                .firstOrNull()
                                ?.feedTypes
                                ?: Constants.defaultRelays
                                    .filter { defaultRelay -> defaultRelay.url == it.key }
                                    .firstOrNull()
                                    ?.feedTypes
                                ?: FeedType.values().toSet().toImmutableSet()

                        val errorCounter = liveRelay?.errorCounter ?: 0
                        val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                        val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                        val spamCounter = liveRelay?.spamCounter ?: 0

                        RelaySetupInfo(
                            it.key,
                            it.value.read,
                            it.value.write,
                            errorCounter,
                            eventDownloadCounter,
                            eventUploadCounter,
                            spamCounter,
                            localInfoFeedTypes,
                        )
                    }
                    .distinctBy { it.url }
                    .sortedBy { it.downloadCountInBytes }
                    .reversed()
            } else {
                account.localRelays
                    .map {
                        val liveRelay = RelayPool.getRelay(it.url)

                        val errorCounter = liveRelay?.errorCounter ?: 0
                        val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                        val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                        val spamCounter = liveRelay?.spamCounter ?: 0

                        RelaySetupInfo(
                            it.url,
                            it.read,
                            it.write,
                            errorCounter,
                            eventDownloadCounter,
                            eventUploadCounter,
                            spamCounter,
                            it.feedTypes,
                        )
                    }
                    .distinctBy { it.url }
                    .sortedBy { it.downloadCountInBytes }
                    .reversed()
            }
        }
    }

    fun addRelay(relay: RelaySetupInfo) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update { it.plus(relay) }

        hasModified = true
    }

    fun deleteRelay(relay: RelaySetupInfo) {
        _relays.update { it.minus(relay) }
        hasModified = true
    }

    fun deleteAll() {
        _relays.update { relays -> emptyList() }
        hasModified = true
    }

    fun toggleDownload(relay: RelaySetupInfo) {
        _relays.update { it.updated(relay, relay.copy(read = !relay.read)) }
        hasModified = true
    }

    fun toggleUpload(relay: RelaySetupInfo) {
        _relays.update { it.updated(relay, relay.copy(write = !relay.write)) }
        hasModified = true
    }

    fun toggleFollows(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.FOLLOWS)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun toggleMessages(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.PRIVATE_DMS)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun togglePublicChats(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.PUBLIC_CHATS)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun toggleGlobal(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.GLOBAL)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun toggleSearch(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.SEARCH)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun togglePaidRelay(
        relay: RelaySetupInfo,
        paid: Boolean,
    ) {
        _relays.update { it.updated(relay, relay.copy(paidRelay = paid)) }
    }
}

fun <T> Iterable<T>.updated(
    old: T,
    new: T,
): List<T> = map { if (it == old) new else it }

fun <T> togglePresenceInSet(
    set: Set<T>,
    item: T,
): Set<T> {
    return if (set.contains(item)) set.minus(item) else set.plus(item)
}
