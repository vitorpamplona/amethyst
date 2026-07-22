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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Discovers the Buzz workspace channels the user already belongs to on a relay (joined via the
 * Buzz web/desktop app) so they can add them to Amethyst. Hosted by the relay group-list screen,
 * which folds this membership-scoped section in when the relay is a Buzz relay.
 *
 * Buzz membership is server-side (no NIP-29 kind-10009 join event), so a Buzz relay exposes no
 * public group directory — the generic "browse a relay" flow returns nothing. Instead the relay
 * addresses each member a kind-44100 member-added notification (`#p` = me). This warm-authenticates
 * the relay (NIP-42) and reads those, then fetches each channel's kind-39000 metadata to keep only
 * the **non-DM** workspace channels (DMs surface separately). Adding one calls [Account.follow] to
 * append it to the user's kind-10009 list — which makes it appear in the Messages list and load at
 * boot through the existing relay-group machinery, no separate registry needed.
 */
class BuzzRelayImportViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private var relay: NormalizedRelayUrl? = null

    val relayUrl: NormalizedRelayUrl? get() = relay

    sealed interface Status {
        data object Loading : Status

        data object Ready : Status

        data class Error(
            val message: String,
        ) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The non-DM workspace channels the user is a member of on this relay. */
    private val _channels = MutableStateFlow<List<GroupId>>(emptyList())
    val channels: StateFlow<List<GroupId>> = _channels.asStateFlow()

    /** Channel ids already present in the user's kind-10009 list (seeded + updated as they add). */
    private val _added = MutableStateFlow<Set<String>>(emptySet())
    val added: StateFlow<Set<String>> = _added.asStateFlow()

    fun bind(
        account: Account,
        relayUrlStr: String,
    ) {
        if (this.account != null) return
        val normalized =
            RelayUrlNormalizer.normalizeOrNull(relayUrlStr) ?: run {
                _status.value = Status.Error("Invalid relay URL")
                return
            }
        this.account = account
        this.relay = normalized

        // The user came here to import from THIS relay: remember it as a joined workspace (persisted,
        // marks the Buzz dialect) and pre-approve NIP-42 auth so the `#p=me` read below is served.
        BuzzWorkspaces.join(normalized)
        viewModelScope.launch { account.relayAuthLedger.setDecision(normalized.url, RelayAuthDecision.ALLOW) }

        // Seed "already added" from the current kind-10009 list so channels the user already has
        // render as added rather than offering a duplicate Add.
        _added.value =
            account.relayGroupList.liveRelayGroupList.value
                .filter { RelayUrlNormalizer.normalizeOrNull(it.relayUrl) == normalized }
                .mapTo(mutableSetOf()) { it.groupId }

        discover(account, normalized)
    }

    private fun discover(
        account: Account,
        relay: NormalizedRelayUrl,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _status.value = Status.Loading
            try {
                val myPubkey = account.userProfile().pubkeyHex
                val channelIds = Collections.synchronizedSet(HashSet<String>())

                // 1. Warm-auth then read the relay's kind-44100 member-added notifications for me.
                account.client.fetchAllWithHooks(
                    filters =
                        mapOf(
                            relay to
                                listOf(
                                    Filter(
                                        kinds = listOf(MemberAddedNotificationEvent.KIND),
                                        tags = mapOf("p" to listOf(myPubkey)),
                                    ),
                                ),
                        ),
                    timeoutMs = 8_000,
                    pendingOnAuthRequired = true,
                ) { _, event ->
                    (event as? MemberAddedNotificationEvent)?.channel()?.let { channelIds.add(it) }
                    false
                }

                // 2. Fetch each channel's NIP-29 metadata (39000-39003) so its name + Buzz `t` type load.
                if (channelIds.isNotEmpty()) {
                    account.client.fetchAllWithHooks(
                        filters = mapOf(relay to listOf(Filter(kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf("d" to channelIds.toList())))),
                        timeoutMs = 8_000,
                        pendingOnAuthRequired = true,
                    ) { _, _ -> false }
                }

                // 3. Keep only non-DM workspace channels; a channel whose metadata hasn't arrived
                //    (type unknown) is optimistically shown as a workspace channel.
                _channels.value =
                    channelIds
                        .mapNotNull { id ->
                            val groupId = GroupId(id, relay)
                            val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
                            if (channel.event?.isBuzzDm() == true) null else groupId
                        }.sortedBy { it.id }
                _status.value = Status.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = Status.Error(e.message ?: "Could not load your channels")
            }
        }
    }

    /** Append [groupId] to the user's kind-10009 list (public group tag), so it shows in Messages. */
    fun add(groupId: GroupId) {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
            account.follow(channel)
            _added.update { it + groupId.id }
        }
    }

    fun addAll() {
        _channels.value.forEach(::add)
    }
}
