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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.newUser

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.service.followimport.FollowEntry
import com.vitorpamplona.amethyst.service.followimport.FollowListImporter
import com.vitorpamplona.amethyst.service.followimport.FollowListResult
import com.vitorpamplona.amethyst.service.followimport.Kind3EventData
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ImportFollowState {
    data object Idle : ImportFollowState()

    data class Resolving(
        val identifier: String,
    ) : ImportFollowState()

    data class Fetching(
        val pubkeyHex: String,
    ) : ImportFollowState()

    data class Preview(
        val sourcePubkeyHex: String,
        val follows: List<FollowEntry>,
        val selected: Set<String>,
        /** Non-null if the source was resolved via Namecoin blockchain */
        val namecoinSource: String? = null,
    ) : ImportFollowState() {
        val selectedCount get() = selected.size
        val totalCount get() = follows.size
    }

    data class Applying(
        val count: Int,
    ) : ImportFollowState()

    data class Done(
        val count: Int,
    ) : ImportFollowState()

    data class Error(
        val message: String,
    ) : ImportFollowState()
}

@Stable
class ImportFollowListViewModel : ViewModel() {
    private val namecoinResolver = NamecoinNameResolver(electrumxClient = ElectrumXClient())
    private val importer = FollowListImporter(resolveNamecoin = namecoinResolver::resolveDetailed)
    private val _state = MutableStateFlow<ImportFollowState>(ImportFollowState.Idle)
    val state: StateFlow<ImportFollowState> = _state.asStateFlow()

    private var fetchEventFn: (suspend (Int, String, Int, (Kind3EventData) -> Unit) -> AutoCloseable?)? = null
    private var resolveNip05Fn: (suspend (String) -> String?)? = null
    private var relayUrls: List<String> =
        listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.nostr.band",
            "wss://purplepag.es",
        )

    fun configure(
        fetchEvent: suspend (Int, String, Int, (Kind3EventData) -> Unit) -> AutoCloseable?,
        resolveNip05: (suspend (String) -> String?)? = null,
        relayUrls: List<String>? = null,
    ) {
        this.fetchEventFn = fetchEvent
        this.resolveNip05Fn = resolveNip05
        if (relayUrls != null) this.relayUrls = relayUrls
    }

    fun startImport(identifier: String) {
        if (identifier.isBlank()) {
            _state.value = ImportFollowState.Error("Please enter an identifier.")
            return
        }
        val fetch =
            fetchEventFn ?: run {
                _state.value = ImportFollowState.Error("Not connected to relays.")
                return
            }

        viewModelScope.launch {
            _state.value = ImportFollowState.Resolving(identifier)
            val result =
                importer.fetchFollowList(
                    identifier = identifier,
                    relayUrls = relayUrls,
                    fetchEvent = fetch,
                    resolveNip05 = resolveNip05Fn,
                )
            _state.value =
                when (result) {
                    is FollowListResult.Success -> {
                        if (result.follows.isEmpty()) {
                            ImportFollowState.Error("This user's follow list is empty.")
                        } else {
                            ImportFollowState.Preview(
                                sourcePubkeyHex = result.sourcePubkeyHex,
                                follows = result.follows,
                                selected = result.follows.map { it.pubkeyHex }.toSet(),
                                namecoinSource = result.resolvedViaNamecoin,
                            )
                        }
                    }

                    is FollowListResult.NoFollowList -> {
                        ImportFollowState.Error("No follow list found on relays for this user.")
                    }

                    is FollowListResult.InvalidIdentifier -> {
                        ImportFollowState.Error(result.reason)
                    }

                    is FollowListResult.Error -> {
                        ImportFollowState.Error(result.message)
                    }
                }
        }
    }

    fun toggleSelection(pubkeyHex: String) {
        val c = _state.value as? ImportFollowState.Preview ?: return
        _state.value = c.copy(selected = if (pubkeyHex in c.selected) c.selected - pubkeyHex else c.selected + pubkeyHex)
    }

    fun setSelectAll(all: Boolean) {
        val c = _state.value as? ImportFollowState.Preview ?: return
        _state.value = c.copy(selected = if (all) c.follows.map { it.pubkeyHex }.toSet() else emptySet())
    }

    fun applySelectedFollows(applyFollows: suspend (List<FollowEntry>) -> Unit) {
        val c = _state.value as? ImportFollowState.Preview ?: return
        val sel = c.follows.filter { it.pubkeyHex in c.selected }
        _state.value = ImportFollowState.Applying(sel.size)
        viewModelScope.launch {
            try {
                applyFollows(sel)
                _state.value = ImportFollowState.Done(sel.size)
            } catch (e: Exception) {
                _state.value = ImportFollowState.Error("Failed: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value = ImportFollowState.Idle
    }
}
