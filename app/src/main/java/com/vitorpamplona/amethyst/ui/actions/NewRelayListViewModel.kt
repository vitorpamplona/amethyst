package com.vitorpamplona.amethyst.ui.actions

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.RelayPool
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NewRelayListViewModel : ViewModel() {
    private lateinit var account: Account

    private val _relays = MutableStateFlow<List<RelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    fun load(account: Account) {
        this.account = account
        clear()
    }

    fun create() {
        relays.let {
            account.saveRelayList(it.value)
        }

        clear()
    }

    fun clear() {
        _relays.update {
            var relayFile = account.userProfile().latestContactList?.relays()

            // Ugly, but forces nostr.band as the only search-supporting relay today.
            // TODO: Remove when search becomes more available.
            if (relayFile?.none { it.key == Constants.forcedRelayForSearch.url } == true) {
                relayFile = relayFile + Pair(
                    Constants.forcedRelayForSearch.url,
                    ContactListEvent.ReadWrite(Constants.forcedRelayForSearch.read, Constants.forcedRelayForSearch.write)
                )
            }

            if (relayFile != null) {
                relayFile.map {
                    val liveRelay = RelayPool.getRelay(it.key)
                    val localInfoFeedTypes = account.localRelays.filter { localRelay -> localRelay.url == it.key }.firstOrNull()?.feedTypes ?: FeedType.values().toSet().toImmutableSet()

                    val errorCounter = liveRelay?.errorCounter ?: 0
                    val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                    val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                    val spamCounter = liveRelay?.spamCounter ?: 0

                    RelaySetupInfo(it.key, it.value.read, it.value.write, errorCounter, eventDownloadCounter, eventUploadCounter, spamCounter, localInfoFeedTypes)
                }.sortedBy { it.downloadCountInBytes }.reversed()
            } else {
                account.localRelays.map {
                    val liveRelay = RelayPool.getRelay(it.url)

                    val errorCounter = liveRelay?.errorCounter ?: 0
                    val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                    val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                    val spamCounter = liveRelay?.spamCounter ?: 0

                    RelaySetupInfo(it.url, it.read, it.write, errorCounter, eventDownloadCounter, eventUploadCounter, spamCounter, it.feedTypes)
                }.sortedBy { it.downloadCountInBytes }.reversed()
            }
        }
    }

    fun addRelay(relay: RelaySetupInfo) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update {
            it.plus(relay)
        }
    }

    fun deleteRelay(relay: RelaySetupInfo) {
        _relays.update {
            it.minus(relay)
        }
    }

    fun toggleDownload(relay: RelaySetupInfo) {
        _relays.update {
            it.updated(relay, relay.copy(read = !relay.read))
        }
    }

    fun toggleUpload(relay: RelaySetupInfo) {
        _relays.update {
            it.updated(relay, relay.copy(write = !relay.write))
        }
    }

    fun toggleFollows(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.FOLLOWS)
        _relays.update {
            it.updated(relay, relay.copy(feedTypes = newTypes))
        }
    }

    fun toggleMessages(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.PRIVATE_DMS)
        _relays.update {
            it.updated(relay, relay.copy(feedTypes = newTypes))
        }
    }

    fun togglePublicChats(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.PUBLIC_CHATS)
        _relays.update {
            it.updated(relay, relay.copy(feedTypes = newTypes))
        }
    }

    fun toggleGlobal(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.GLOBAL)
        _relays.update {
            it.updated(relay, relay.copy(feedTypes = newTypes))
        }
    }

    fun toggleSearch(relay: RelaySetupInfo) {
        val newTypes = togglePresenceInSet(relay.feedTypes, FeedType.SEARCH)
        _relays.update {
            it.updated(relay, relay.copy(feedTypes = newTypes))
        }
    }
}

fun <T> Iterable<T>.updated(old: T, new: T): List<T> = map { if (it == old) new else it }

fun <T> togglePresenceInSet(set: Set<T>, item: T): Set<T> {
    return if (set.contains(item)) set.minus(item) else set.plus(item)
}
