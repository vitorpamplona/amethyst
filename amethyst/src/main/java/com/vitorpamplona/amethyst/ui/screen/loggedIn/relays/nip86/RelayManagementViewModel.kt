/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip86

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.nip86RelayManagement.Nip86Retriever
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BlockedIp
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.EventNeedingModeration
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PubkeyUser(
    val user: User,
    val reason: String?,
)

class RelayManagementViewModel(
    relayUrl: NormalizedRelayUrl,
    signer: NostrSigner,
    private val retriever: Nip86Retriever,
) : ViewModel() {
    val client = Nip86Client(relayUrl, signer)

    private val _supportedMethods = MutableStateFlow<List<String>>(emptyList())
    val supportedMethods: StateFlow<List<String>> = _supportedMethods

    private val _bannedPubkeys = MutableStateFlow<List<BannedPubkey>>(emptyList())
    val bannedPubkeys: StateFlow<List<BannedPubkey>> = _bannedPubkeys

    private val _allowedPubkeys = MutableStateFlow<List<AllowedPubkey>>(emptyList())
    val allowedPubkeys: StateFlow<List<AllowedPubkey>> = _allowedPubkeys

    private val _bannedEvents = MutableStateFlow<List<BannedEvent>>(emptyList())
    val bannedEvents: StateFlow<List<BannedEvent>> = _bannedEvents

    private val _eventsNeedingModeration = MutableStateFlow<List<EventNeedingModeration>>(emptyList())
    val eventsNeedingModeration: StateFlow<List<EventNeedingModeration>> = _eventsNeedingModeration

    private val _allowedKinds = MutableStateFlow<List<Int>>(emptyList())
    val allowedKinds: StateFlow<List<Int>> = _allowedKinds

    private val _blockedIps = MutableStateFlow<List<BlockedIp>>(emptyList())
    val blockedIps: StateFlow<List<BlockedIp>> = _blockedIps

    val bannedPubkeyUsers: Flow<List<PubkeyUser>> =
        _bannedPubkeys.map { list ->
            list.mapNotNull { entry ->
                LocalCache.checkGetOrCreateUser(entry.pubkey)?.let { PubkeyUser(it, entry.reason) }
            }
        }

    val allowedPubkeyUsers: Flow<List<PubkeyUser>> =
        _allowedPubkeys.map { list ->
            list.mapNotNull { entry ->
                LocalCache.checkGetOrCreateUser(entry.pubkey)?.let { PubkeyUser(it, entry.reason) }
            }
        }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadSupportedMethods() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val response = retriever.execute(client, Nip86Request.supportedMethods())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _supportedMethods.value = client.parseSupportedMethods(response) ?: emptyList()
            }
            _isLoading.value = false
        }
    }

    fun loadBannedPubkeys() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listBannedPubkeys())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _bannedPubkeys.value = client.parseBannedPubkeys(response) ?: emptyList()
            }
        }
    }

    fun loadAllowedPubkeys() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listAllowedPubkeys())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _allowedPubkeys.value = client.parseAllowedPubkeys(response) ?: emptyList()
            }
        }
    }

    fun loadBannedEvents() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listBannedEvents())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _bannedEvents.value = client.parseBannedEvents(response) ?: emptyList()
            }
        }
    }

    fun loadEventsNeedingModeration() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listEventsNeedingModeration())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _eventsNeedingModeration.value = client.parseEventsNeedingModeration(response) ?: emptyList()
            }
        }
    }

    fun loadAllowedKinds() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listAllowedKinds())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _allowedKinds.value = client.parseAllowedKinds(response) ?: emptyList()
            }
        }
    }

    fun loadBlockedIps() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listBlockedIps())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _blockedIps.value = client.parseBlockedIps(response) ?: emptyList()
            }
        }
    }

    fun banPubkey(
        pubkey: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.banPubkey(pubkey, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBannedPubkeys()
            }
        }
    }

    fun unbanPubkey(pubkey: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unbanPubkey(pubkey))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBannedPubkeys()
            }
        }
    }

    fun allowPubkey(
        pubkey: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.allowPubkey(pubkey, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedPubkeys()
            }
        }
    }

    fun unallowPubkey(pubkey: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unallowPubkey(pubkey))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedPubkeys()
            }
        }
    }

    fun banEvent(
        eventId: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.banEvent(eventId, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBannedEvents()
            }
        }
    }

    fun allowEvent(
        eventId: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.allowEvent(eventId, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadEventsNeedingModeration()
            }
        }
    }

    fun changeRelayName(newName: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.changeRelayName(newName))
            if (response.error != null) {
                _error.value = response.error
            }
        }
    }

    fun changeRelayDescription(newDescription: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.changeRelayDescription(newDescription))
            if (response.error != null) {
                _error.value = response.error
            }
        }
    }

    fun changeRelayIcon(newIconUrl: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.changeRelayIcon(newIconUrl))
            if (response.error != null) {
                _error.value = response.error
            }
        }
    }

    fun allowKind(kind: Int) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.allowKind(kind))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedKinds()
            }
        }
    }

    fun disallowKind(kind: Int) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.disallowKind(kind))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedKinds()
            }
        }
    }

    fun blockIp(
        ip: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.blockIp(ip, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBlockedIps()
            }
        }
    }

    fun unblockIp(ip: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unblockIp(ip))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBlockedIps()
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun loadAllLists() {
        val methods = _supportedMethods.value
        if (methods.contains("listbannedpubkeys")) loadBannedPubkeys()
        if (methods.contains("listallowedpubkeys")) loadAllowedPubkeys()
        if (methods.contains("listbannedevents")) loadBannedEvents()
        if (methods.contains("listeventsneedingmoderation")) loadEventsNeedingModeration()
        if (methods.contains("listallowedkinds")) loadAllowedKinds()
        if (methods.contains("listblockedips")) loadBlockedIps()
    }
}
