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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.buzz.dm.DmCreatedEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backing ViewModel for [BuzzNewDmScreen]. It gathers 1-8 other participants and a Buzz
 * workspace relay, publishes a kind-41010 open command, then waits for the relay-signed
 * kind-41001 confirmation to land in [BuzzDmRegistry] so it can hand the caller the
 * relay-assigned [GroupId] (we never mint the DM's UUID ourselves).
 */
class BuzzNewDmViewModel : ViewModel() {
    @Volatile private var account: Account? = null

    private val _relay = MutableStateFlow<NormalizedRelayUrl?>(null)
    val relay: StateFlow<NormalizedRelayUrl?> = _relay.asStateFlow()

    private val _relays = MutableStateFlow<List<NormalizedRelayUrl>>(emptyList())
    val relays: StateFlow<List<NormalizedRelayUrl>> = _relays.asStateFlow()

    private val _participants = MutableStateFlow<List<HexKey>>(emptyList())
    val participants: StateFlow<List<HexKey>> = _participants.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    sealed interface Status {
        data object Idle : Status

        data object Sending : Status

        data class Error(
            val message: String,
        ) : Status
    }

    fun bindAccountIfMissing(account: Account) {
        if (this.account != null) return
        this.account = account
        val buzz =
            BuzzRelayDialect.flow.value
                .toList()
                .sortedBy { it.url }
        _relays.value = buzz
        _relay.value = buzz.firstOrNull()
    }

    fun selectRelay(relay: NormalizedRelayUrl) {
        _relay.value = relay
    }

    /**
     * Resolves [input] (npub or 64-char hex) to a pubkey and adds it. Returns an error
     * string to surface, or null on success. Rejects me, duplicates, non-keys and the
     * 8-participant ceiling.
     */
    fun addParticipant(input: String): String? {
        val account = account ?: return "Not ready"
        val hex = decodePublicKeyAsHexOrNull(input.trim())?.takeIf { it.isValid() } ?: return "Not a valid npub or hex key"
        if (hex == account.userProfile().pubkeyHex) return "That's you"
        val current = _participants.value
        if (hex in current) return "Already added"
        if (current.size >= DmOpenEvent.MAX_PARTICIPANTS) return "At most ${DmOpenEvent.MAX_PARTICIPANTS} others"
        _participants.value = current + hex
        return null
    }

    fun removeParticipant(hex: HexKey) {
        _participants.update { it - hex }
    }

    /**
     * Publishes the 41010 and awaits the 41001 confirmation, then invokes [onOpened] with
     * the relay-assigned [GroupId]. On timeout it still calls [onOpened] with null so the
     * screen can fall back to the inbox (the DM will surface there once it confirms).
     */
    fun start(onOpened: (GroupId?) -> Unit) {
        val account = account ?: return
        val relay =
            _relay.value ?: run {
                _status.value = Status.Error("Pick a workspace relay")
                return
            }
        val others = _participants.value
        if (others.isEmpty()) {
            _status.value = Status.Error("Add at least one person")
            return
        }
        val expected = (others + account.userProfile().pubkeyHex).toSet()

        _status.value = Status.Sending
        viewModelScope.launch(Dispatchers.IO) {
            try {
                account.openBuzzDm(relay, others)
                val groupId = awaitConfirmation(account, relay, expected)
                withContext(Dispatchers.Main) { onOpened(groupId) }
            } catch (e: IllegalArgumentException) {
                _status.value = Status.Error(e.message ?: "Could not open the DM")
            }
        }
    }

    /**
     * Polls for the relay-signed 41001 whose participant set matches [expected] on [relay],
     * re-fetching `#p` = me between checks so a fresh confirmation is pulled in. Returns the
     * matched [GroupId], or null after [CONFIRM_TIMEOUT_MS].
     */
    private suspend fun awaitConfirmation(
        account: Account,
        relay: NormalizedRelayUrl,
        expected: Set<HexKey>,
    ): GroupId? {
        val myPubkey = account.userProfile().pubkeyHex
        val filters = listOf(Filter(kinds = listOf(DmCreatedEvent.KIND), tags = mapOf("p" to listOf(myPubkey))))
        val deadline = CONFIRM_TIMEOUT_MS
        var waited = 0L
        while (waited < deadline) {
            account.client.fetchAllPagesFromPool(mapOf(relay to filters)) { _, _ -> }
            val match =
                BuzzDmRegistry.conversations.value.values.firstOrNull {
                    it.relay == relay && it.participants.toSet() == expected
                }
            if (match != null) return GroupId(match.channelId, relay)
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return null
    }

    companion object {
        private const val CONFIRM_TIMEOUT_MS = 6_000L
        private const val POLL_INTERVAL_MS = 500L
    }
}
