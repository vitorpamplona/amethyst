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

import androidx.compose.foundation.text.input.TextFieldState
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

@Stable
class UserSuggestionState(
    val account: Account,
    val nip05Client: INip05Client,
) {
    val invalidations = MutableStateFlow(0)
    val currentWord = MutableStateFlow("")
    val searchDataSourceState = SearchQueryState(MutableStateFlow(""), account)

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
                    // NIP-05 resolution: user@domain or bare .bit domain
                    val nip05 =
                        if (prefix.contains('@')) {
                            Nip05Id.parse(prefix)
                        } else if (prefix.endsWith(".bit", ignoreCase = true)) {
                            Nip05Id("_", prefix.lowercase())
                        } else {
                            null
                        }
                    if (nip05 != null) {
                        runCatching {
                            nip05Client.get(nip05)?.let { info ->
                                val user = account.cache.checkGetOrCreateUser(info.pubkey)
                                if (user != null) {
                                    info.relays.forEach {
                                        it.normalizeRelayUrlOrNull()?.let { relay ->
                                            account.cache.relayHints.addKey(user.pubkey(), relay)
                                        }
                                    }
                                }
                                user
                            }
                        }.getOrNull()
                    } else if (prefix.startsWithAny(userUriPrefixes)) {
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
                        account.cache.getOrCreateUser(prefix)
                    } else {
                        null
                    }
                } else {
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

    fun replaceCurrentWord(
        state: TextFieldState,
        word: String,
        item: User,
    ) {
        val wordToInsert = "@${item.pubkeyNpub()}"
        state.edit {
            val lastWordStart = selection.end - word.length
            replace(lastWordStart, selection.end, wordToInsert)
            val cursor = lastWordStart + wordToInsert.length
            placeCursorBeforeCharAt(cursor)
        }
    }
}
