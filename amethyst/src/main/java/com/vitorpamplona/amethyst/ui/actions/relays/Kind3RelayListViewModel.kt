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
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.ammolite.relays.Constants.activeTypesGlobalChats
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.relays.RelayStats
import com.vitorpamplona.quartz.encoders.RelayUrlFormatter
import com.vitorpamplona.quartz.utils.MinimumRelayListProcessor
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class Kind3RelayListViewModel : ViewModel() {
    private lateinit var account: Account

    private val _relays = MutableStateFlow<List<Kind3BasicRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    private val _proposedRelays = MutableStateFlow<List<Kind3RelayProposalSetupInfo>>(emptyList())
    val proposedRelays = _proposedRelays.asStateFlow()

    var hasModified = false

    fun load(account: Account) {
        this.account = account
        clear()
        loadRelayDocuments()
    }

    fun create() {
        if (hasModified) {
            viewModelScope.launch(Dispatchers.IO) {
                account.saveKind3RelayList(
                    relays.value.map {
                        RelaySetupInfo(
                            it.url,
                            it.read,
                            it.write,
                            it.feedTypes,
                        )
                    },
                )
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
                        val localInfoFeedTypes =
                            account.localRelays
                                .filter { localRelay -> localRelay.url == it.key }
                                .firstOrNull()
                                ?.feedTypes
                                ?: Constants.defaultRelays
                                    .filter { defaultRelay -> defaultRelay.url == it.key }
                                    .firstOrNull()
                                    ?.feedTypes
                                ?: activeTypesGlobalChats.toImmutableSet()

                        Kind3BasicRelaySetupInfo(
                            url = RelayUrlFormatter.normalize(it.key),
                            read = it.value.read,
                            write = it.value.write,
                            feedTypes = localInfoFeedTypes,
                            relayStat = RelayStats.get(it.key),
                        )
                    }.distinctBy { it.url }
                    .sortedBy { it.relayStat.receivedBytes }
                    .reversed()
            } else {
                account.localRelays
                    .map {
                        Kind3BasicRelaySetupInfo(
                            url = RelayUrlFormatter.normalize(it.url),
                            read = it.read,
                            write = it.write,
                            feedTypes = it.feedTypes,
                            relayStat = RelayStats.get(it.url),
                        )
                    }.distinctBy { it.url }
                    .sortedBy { it.relayStat.receivedBytes }
                    .reversed()
            }
        }

        refreshProposals()
    }

    private fun refreshProposals() {
        _proposedRelays.update {
            val proposed =
                MinimumRelayListProcessor
                    .reliableRelaySetFor(
                        account.liveKind3Follows.value.users.mapNotNull {
                            account.getNIP65RelayList(it)
                        },
                        relayUrlsToIgnore =
                            _relays.value.mapNotNullTo(HashSet()) {
                                if (it.read && FeedType.FOLLOWS in it.feedTypes) {
                                    it.url
                                } else {
                                    null
                                }
                            },
                        hasOnionConnection = false,
                    ).sortedByDescending { it.users.size }

            proposed.mapNotNull {
                if (it.requiredToNotMissEvents) {
                    Kind3RelayProposalSetupInfo(
                        url = RelayUrlFormatter.normalize(it.url),
                        read = true,
                        write = false,
                        feedTypes = setOf(FeedType.FOLLOWS),
                        relayStat = RelayStats.get(it.url),
                        users = it.users,
                    )
                } else {
                    null
                }
            }
        }
    }

    fun addAll(defaultRelays: Array<RelaySetupInfo>) {
        hasModified = true

        _relays.update {
            defaultRelays
                .map {
                    Kind3BasicRelaySetupInfo(
                        url = RelayUrlFormatter.normalize(it.url),
                        read = it.read,
                        write = it.write,
                        feedTypes = it.feedTypes,
                        relayStat = RelayStats.get(it.url),
                    )
                }.distinctBy { it.url }
                .sortedBy { it.relayStat.receivedBytes }
                .reversed()
        }
    }

    fun addRelay(relay: Kind3BasicRelaySetupInfo) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update { it.plus(relay) }

        refreshProposals()

        hasModified = true
    }

    fun addRelay(relay: Kind3RelayProposalSetupInfo) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update { it.plus(Kind3BasicRelaySetupInfo(relay.url, relay.read, relay.write, relay.feedTypes, relay.relayStat, relay.paidRelay)) }

        refreshProposals()

        hasModified = true
    }

    fun deleteRelay(relay: Kind3BasicRelaySetupInfo) {
        _relays.update { it.minus(relay) }

        refreshProposals()

        hasModified = true
    }

    fun deleteAll() {
        _relays.update { relays -> emptyList() }

        refreshProposals()

        hasModified = true
    }

    fun toggleDownload(relay: Kind3BasicRelaySetupInfo) {
        _relays.update { it.updated(relay, relay.copy(read = !relay.read)) }
        hasModified = true
    }

    fun toggleUpload(relay: Kind3BasicRelaySetupInfo) {
        _relays.update { it.updated(relay, relay.copy(write = !relay.write)) }
        hasModified = true
    }

    fun toggleFollows(relay: Kind3BasicRelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.FOLLOWS)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun toggleMessages(relay: Kind3BasicRelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.PRIVATE_DMS)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun togglePublicChats(relay: Kind3BasicRelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.PUBLIC_CHATS)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun toggleGlobal(relay: Kind3BasicRelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.GLOBAL)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun toggleSearch(relay: Kind3BasicRelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.SEARCH)
        _relays.update { it.updated(relay, relay.copy(feedTypes = newTypes)) }
        hasModified = true
    }

    fun togglePaidRelay(
        relay: Kind3BasicRelaySetupInfo,
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
): Set<T> = if (set.contains(item)) set.minus(item) else set.plus(item)
