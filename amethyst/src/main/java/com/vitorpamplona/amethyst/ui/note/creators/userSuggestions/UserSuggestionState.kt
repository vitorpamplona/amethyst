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
package com.vitorpamplona.amethyst.ui.note.creators.userSuggestions

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.SearchQueryState
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.startsWithAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

val userUriPrefixes =
    listOf(
        DualCase("npub"),
        DualCase("nprofile"),
        DualCase("nostr:npub"),
        DualCase("nostr:nprofile"),
    )

/** UI state for Namecoin resolution progress. */
sealed class NamecoinResolutionState {
    data object Idle : NamecoinResolutionState()

    data object Resolving : NamecoinResolutionState()

    data class Resolved(
        val user: User,
        val namecoinName: String,
    ) : NamecoinResolutionState()

    data class Error(
        val message: String,
    ) : NamecoinResolutionState()
}

/** Returns a [Nip05Id] for identifiers that should go through NIP-05 / Namecoin resolution. */
private fun toNip05IdOrNull(prefix: String): Nip05Id? =
    when {
        prefix.contains('@') -> {
            Nip05Id.parse(prefix)
        }

        NamecoinNameResolver.isNamecoinIdentifier(prefix) -> {
            if (prefix.endsWith(".bit", ignoreCase = true)) {
                // Bare .bit domain → synthesize _@domain.bit so Nip05Client routes to Namecoin
                Nip05Id("_", prefix.lowercase())
            } else {
                // d/ or id/ — wrap as NIP-05 so it reaches the resolver
                Nip05Id("_", prefix.lowercase())
            }
        }

        else -> {
            null
        }
    }

@Stable
class UserSuggestionState(
    val account: Account,
    val nip05Client: INip05Client,
) {
    val invalidations = MutableStateFlow(0)
    val currentWord = MutableStateFlow("")
    val searchDataSourceState = SearchQueryState(MutableStateFlow(""), account)

    /** Tracks Namecoin resolution status for the UI. */
    val namecoinState = MutableStateFlow<NamecoinResolutionState>(NamecoinResolutionState.Idle)

    @OptIn(FlowPreview::class)
    val searchTerm =
        currentWord
            .debounce(300)
            .distinctUntilChanged()
            .map(::userSearchTermOrNull)
            .onEach(::updateDataSource)

    @OptIn(FlowPreview::class)
    val nip05ResolutionFlow =
        currentWord
            .debounce(300)
            .distinctUntilChanged()
            .map(::userSearchTermOrNull)
            .map { prefix ->
                if (prefix != null) {
                    val nip05 = toNip05IdOrNull(prefix)
                    if (nip05 != null) {
                        val isNamecoin = NamecoinNameResolver.isNamecoinIdentifier(nip05.toValue())
                        if (isNamecoin) namecoinState.emit(NamecoinResolutionState.Resolving)

                        val user =
                            runCatching {
                                nip05Client.get(nip05)?.let { info ->
                                    val u = account.cache.checkGetOrCreateUser(info.pubkey)
                                    if (u != null) {
                                        info.relays.forEach {
                                            it.normalizeRelayUrlOrNull()?.let { relay ->
                                                account.cache.relayHints.addKey(u.pubkey(), relay)
                                            }
                                        }
                                    }
                                    u
                                }
                            }.getOrNull()

                        if (isNamecoin) {
                            if (user != null) {
                                namecoinState.emit(NamecoinResolutionState.Resolved(user, prefix))
                            } else {
                                namecoinState.emit(NamecoinResolutionState.Error("Could not resolve $prefix via Namecoin"))
                            }
                        } else {
                            namecoinState.emit(NamecoinResolutionState.Idle)
                        }
                        user
                    } else if (prefix.startsWithAny(userUriPrefixes)) {
                        namecoinState.emit(NamecoinResolutionState.Idle)
                        runCatching {
                            Nip19Parser.uriToRoute(prefix)?.entity?.let { parsed ->
                                when (parsed) {
                                    is NSec -> {
                                        account.cache.getOrCreateUser(parsed.toPubKey().toHexKey())
                                    }

                                    is NPub -> {
                                        account.cache.getOrCreateUser(parsed.hex)
                                    }

                                    is NProfile -> {
                                        val user = account.cache.getOrCreateUser(parsed.hex)
                                        parsed.relay.forEach { relay ->
                                            account.cache.relayHints.addKey(user.pubkey(), relay)
                                        }
                                        user
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            }
                        }.getOrNull()
                    } else if (prefix.length == 64 && Hex.isHex64(prefix)) {
                        namecoinState.emit(NamecoinResolutionState.Idle)
                        account.cache.getOrCreateUser(prefix)
                    } else {
                        namecoinState.emit(NamecoinResolutionState.Idle)
                        null
                    }
                } else {
                    namecoinState.emit(NamecoinResolutionState.Idle)
                    null
                }
            }.flowOn(Dispatchers.IO)

    @OptIn(FlowPreview::class)
    val results =
        combine(searchTerm, nip05ResolutionFlow, invalidations.debounce(100)) { prefix, nip05, version ->
            if (nip05 != null) {
                return@combine listOf(nip05)
            }
            if (prefix != null) {
                logTime("UserSuggestionState Search $prefix version $version") {
                    account.cache.findUsersStartingWith(prefix, account)
                }
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.IO)

    fun reset() {
        if (!currentWord.value.isEmpty()) {
            currentWord.tryEmit("")
        }
    }

    fun processCurrentWord(word: String) {
        currentWord.tryEmit(word)
    }

    fun invalidateData() {
        // force new query
        invalidations.update { it + 1 }
    }

    fun userSearchTermOrNull(currentWord: String): String? =
        if (currentWord.length > 2) {
            currentWord.removePrefix("@")
        } else {
            null
        }

    fun updateDataSource(searchTerm: String?) {
        if (searchTerm != null) {
            searchDataSourceState.searchQuery.tryEmit(searchTerm)
        } else {
            searchDataSourceState.searchQuery.tryEmit("")
        }
    }

    fun replaceCurrentWord(
        message: TextFieldValue,
        word: String,
        item: User,
    ): TextFieldValue {
        val lastWordStart = message.selection.end - word.length
        val wordToInsert = "@${item.pubkeyNpub()}"

        return TextFieldValue(
            message.text.replaceRange(lastWordStart, message.selection.end, wordToInsert),
            TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
        )
    }
}
