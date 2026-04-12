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

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.nip86RelayManagement.INip86Retriever
import com.vitorpamplona.amethyst.commons.model.nip86RelayManagement.RelayManagementState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PubkeyUser(
    val user: User,
    val reason: String?,
)

@Stable
class RelayManagementViewModel(
    relayUrl: NormalizedRelayUrl,
    account: Account,
    retriever: INip86Retriever,
) : ViewModel() {
    val state =
        RelayManagementState(
            client = Nip86Client(relayUrl, account.signer),
            retriever = retriever,
            scope = viewModelScope,
        )

    // Delegate all core state from RelayManagementState
    val client get() = state.client
    val supportedMethods get() = state.supportedMethods
    val bannedPubkeys get() = state.bannedPubkeys
    val allowedPubkeys get() = state.allowedPubkeys
    val bannedEvents get() = state.bannedEvents
    val eventsNeedingModeration get() = state.eventsNeedingModeration
    val allowedKinds get() = state.allowedKinds
    val blockedIps get() = state.blockedIps
    val isLoading get() = state.isLoading
    val error get() = state.error

    // Android-specific: resolve pubkey hex to User objects via LocalCache
    val bannedPubkeyUsers: Flow<List<PubkeyUser>> =
        state.bannedPubkeys.map { list ->
            list
                .mapNotNull { entry ->
                    LocalCache.checkGetOrCreateUser(entry.pubkey)?.let { PubkeyUser(it, entry.reason) }
                }.sortedByDescending { account.isKnown(it.user) }
        }

    val allowedPubkeyUsers: Flow<List<PubkeyUser>> =
        state.allowedPubkeys.map { list ->
            list
                .mapNotNull { entry ->
                    LocalCache.checkGetOrCreateUser(entry.pubkey)?.let { PubkeyUser(it, entry.reason) }
                }.sortedByDescending { account.isKnown(it.user) }
        }

    // Delegate all actions
    fun loadSupportedMethods() = state.loadSupportedMethods()

    fun loadBannedPubkeys() = state.loadBannedPubkeys()

    fun loadAllowedPubkeys() = state.loadAllowedPubkeys()

    fun loadBannedEvents() = state.loadBannedEvents()

    fun loadEventsNeedingModeration() = state.loadEventsNeedingModeration()

    fun loadAllowedKinds() = state.loadAllowedKinds()

    fun loadBlockedIps() = state.loadBlockedIps()

    fun banPubkey(
        pubkey: String,
        reason: String? = null,
    ) = state.banPubkey(pubkey, reason)

    fun unbanPubkey(pubkey: String) = state.unbanPubkey(pubkey)

    fun allowPubkey(
        pubkey: String,
        reason: String? = null,
    ) = state.allowPubkey(pubkey, reason)

    fun unallowPubkey(pubkey: String) = state.unallowPubkey(pubkey)

    fun banEvent(
        eventId: String,
        reason: String? = null,
    ) = state.banEvent(eventId, reason)

    fun allowEvent(
        eventId: String,
        reason: String? = null,
    ) = state.allowEvent(eventId, reason)

    fun changeRelayName(newName: String) = state.changeRelayName(newName)

    fun changeRelayDescription(newDescription: String) = state.changeRelayDescription(newDescription)

    fun changeRelayIcon(newIconUrl: String) = state.changeRelayIcon(newIconUrl)

    fun allowKind(kind: Int) = state.allowKind(kind)

    fun disallowKind(kind: Int) = state.disallowKind(kind)

    fun blockIp(
        ip: String,
        reason: String? = null,
    ) = state.blockIp(ip, reason)

    fun unblockIp(ip: String) = state.unblockIp(ip)

    fun clearError() = state.clearError()

    fun loadAllLists() = state.loadAllLists()
}
