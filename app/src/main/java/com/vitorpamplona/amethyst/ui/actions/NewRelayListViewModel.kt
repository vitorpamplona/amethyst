package com.vitorpamplona.amethyst.ui.actions

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.Constants
import com.vitorpamplona.amethyst.service.relays.RelayPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nostr.postr.events.ContactListEvent

class NewRelayListViewModel: ViewModel() {
    private lateinit var account: Account

    data class Relay(
        val url: String,
        val read: Boolean,
        val write: Boolean,
        val errorCount: Int = 0,
        val downloadCount: Int = 0,
        val uploadCount: Int = 0
    )

    private val _relays = MutableStateFlow<List<Relay>>(emptyList())
    val relays = _relays.asStateFlow()

    fun load(account: Account) {
        this.account = account
        clear()
    }

    fun create() {
        relays.let {
            account.sendNewRelayList(it.value.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) } )
        }

        clear()
    }

    fun clear() {
        _relays.update {
            val relayFile = account.userProfile().relays

            if (relayFile != null)
                relayFile.map {
                    val liveRelay = RelayPool.getRelay(it.key)

                    val errorCounter = liveRelay?.errorCounter ?: 0
                    val eventDownloadCounter = liveRelay?.eventDownloadCounter ?: 0
                    val eventUploadCounter = liveRelay?.eventUploadCounter ?: 0

                    Relay(it.key, it.value.read, it.value.write, errorCounter, eventDownloadCounter, eventUploadCounter)
                }.sortedBy { it.downloadCount }.reversed()
            else
                Constants.defaultRelays.map {
                    val liveRelay = RelayPool.getRelay(it.url)

                    val errorCounter = liveRelay?.errorCounter ?: 0
                    val eventDownloadCounter = liveRelay?.eventDownloadCounter ?: 0
                    val eventUploadCounter = liveRelay?.eventUploadCounter ?: 0

                    Relay(it.url, it.read, it.write, errorCounter, eventDownloadCounter, eventUploadCounter)
                }.sortedBy { it.downloadCount }.reversed()
        }
    }

    fun addRelay(relay: Relay) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update {
            it.plus(relay)
        }
    }

    fun deleteRelay(relay: Relay) {
        _relays.update {
            it.minus(relay)
        }
    }

    fun toggleDownload(relay: Relay) {
        _relays.update {
            it.updated(relay, relay.copy(read = !relay.read))
        }
    }

    fun toggleUpload(relay: Relay) {
        _relays.update {
            it.updated(relay, relay.copy(write = !relay.write))
        }
    }
}

fun <T> Iterable<T>.updated(old: T, new: T): List<T> = map { if (it == old) new else it }