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

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.buzz.AgentFleetAggregator
import com.vitorpamplona.amethyst.commons.model.buzz.AgentFleetMetrics
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.filter
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricEvent
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricPayload
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Backing ViewModel for the [AgentConsoleScreen] — the workspace owner's read-only
 * dashboard over their AI-agent fleet.
 *
 * It reads two Buzz kinds the owner authored or received:
 * - **Personas** ([PersonaEvent], `kind:30175`) — plaintext, authored by the owner.
 * - **Turn metrics** ([AgentTurnMetricEvent], `kind:44200`) — NIP-44 ciphertext an
 *   agent published to the owner (`p` tag = owner). Either party can decrypt; here the
 *   owner's [Account.signer] does. Decryption is cached by event id so a refresh that
 *   re-reads the cache never re-runs NIP-44 on an event it already opened.
 *
 * [refresh] fetches both kinds from the Buzz-dialect relays plus the owner's outbox set,
 * lets [LocalCache] consume them, then re-derives the aggregate via the pure
 * [AgentFleetAggregator] (which owns the cost/token semantics). The ViewModel is keyed by
 * the owner pubkey in the screen, so one instance survives navigation in and out.
 */
class AgentConsoleViewModel : ViewModel() {
    @Volatile private var account: Account? = null

    /** event id -> decrypted payload (null = decryption failed; don't retry). */
    private val decryptCache = HashMap<HexKey, AgentTurnMetricPayload?>()
    private val refreshMutex = Mutex()

    private val _metrics = MutableStateFlow(AgentFleetMetrics.EMPTY)
    val metrics: StateFlow<AgentFleetMetrics> = _metrics.asStateFlow()

    private val _personas = MutableStateFlow<List<PersonaCard>>(emptyList())
    val personas: StateFlow<List<PersonaCard>> = _personas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun bindAccountIfMissing(account: Account) {
        if (this.account != null) return
        this.account = account
        refresh()
    }

    fun refresh() {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                _isLoading.value = true
                try {
                    fetchFromRelays(account)
                    reloadFromCache(account)
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * One-shot paged fetch of the owner's personas (authored by owner) and turn metrics
     * (addressed to the owner via the `p` tag) from every Buzz-dialect relay and the
     * owner's own outbox relays. Events land in [LocalCache] via the normal consume path;
     * this only primes the cache before [reloadFromCache] reads it back.
     */
    private suspend fun fetchFromRelays(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val relays = BuzzRelayDialect.flow.value + account.outboxRelays.flow.value
        if (relays.isEmpty()) return

        val filters =
            listOf(
                Filter(kinds = listOf(AgentTurnMetricEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                Filter(kinds = listOf(PersonaEvent.KIND), authors = listOf(myPubkey)),
            )

        account.client.fetchAllPagesFromPool(relays.associateWith { filters }) { _, _ -> }
    }

    private suspend fun reloadFromCache(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val signer = account.signer

        _personas.value =
            LocalCache.addressables
                .filter(PersonaEvent.KIND, myPubkey) { _, note -> note.event is PersonaEvent }
                .mapNotNull { note ->
                    val event = note.event as? PersonaEvent ?: return@mapNotNull null
                    val content = event.personaOrNull()
                    PersonaCard(
                        slug = event.slug() ?: "",
                        displayName = content?.displayName ?: event.slug() ?: "",
                        model = content?.model,
                        runtime = content?.runtime,
                        provider = content?.provider,
                        systemPrompt = content?.systemPrompt,
                    )
                }.sortedBy { it.displayName.lowercase() }

        val metricNotes =
            LocalCache.filter(
                Filter(kinds = listOf(AgentTurnMetricEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
            )

        val decrypted =
            metricNotes.mapNotNull { note ->
                val event = note.event as? AgentTurnMetricEvent ?: return@mapNotNull null
                val payload =
                    if (decryptCache.containsKey(event.id)) {
                        decryptCache[event.id]
                    } else {
                        event.decryptOrNull(signer).also { decryptCache[event.id] = it }
                    } ?: return@mapNotNull null
                val agent = event.agentPubKey() ?: event.pubKey
                agent to payload
            }

        _metrics.value = AgentFleetAggregator.aggregate(decrypted)
    }

    /** A persona rendered on the Personas tab; a flattened projection of [PersonaEvent]. */
    @Immutable
    data class PersonaCard(
        val slug: String,
        val displayName: String,
        val model: String?,
        val runtime: String?,
        val provider: String?,
        val systemPrompt: String?,
    )
}
