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
package com.vitorpamplona.amethyst.ui.actions.nestsServers

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Edit-buffer for the user's audio-room (NIP-53 / nests) MoQ server
 * list. Mirror of [com.vitorpamplona.amethyst.ui.actions.mediaServers.BlossomServersViewModel]
 * scoped down to a plain `List<String>` of base URLs since nests
 * servers don't have a "selected default" concept (clients pick the
 * first entry when starting a new space).
 *
 * The flow:
 *   1. [init] binds the [AccountViewModel].
 *   2. [load] (or [refresh]) snapshots the saved list into the local
 *      [_servers] buffer.
 *   3. [addServer] / [removeServer] / [removeAllServers] mutate the
 *      buffer, marking [isModified] = true.
 *   4. [save] publishes a fresh kind-10112 [com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent]
 *      via [Account.sendNestsServersList].
 */
@Stable
class NestsServersViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private val _servers = MutableStateFlow<List<NestsServer>>(emptyList())
    val servers = _servers.asStateFlow()
    private var isModified = false

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun load() = refresh()

    fun refresh() {
        isModified = false
        val current = account.nestsServers.flow.value
        _servers.update {
            current.map { url -> NestsServer(displayHostName(url), url) }
        }
    }

    fun addServer(url: String) {
        val normalised =
            try {
                Rfc3986.normalize(url.trim())
            } catch (_: Throwable) {
                url.trim()
            }
        if (normalised.isBlank()) return
        val entry = NestsServer(displayHostName(normalised), normalised)
        if (_servers.value.any { it.baseUrl == entry.baseUrl }) return
        _servers.update { it + entry }
        isModified = true
    }

    fun addServerList(urls: List<String>) {
        urls.forEach { addServer(it) }
    }

    fun removeServer(baseUrl: String) {
        val before = _servers.value
        val after = before.filterNot { it.baseUrl == baseUrl }
        if (after.size != before.size) {
            _servers.update { after }
            isModified = true
        }
    }

    fun removeAllServers() {
        if (_servers.value.isNotEmpty()) {
            _servers.update { emptyList() }
            isModified = true
        }
    }

    fun save() {
        if (!isModified) return
        accountViewModel.launchSigner {
            account.sendNestsServersList(_servers.value.map { it.baseUrl })
            refresh()
        }
    }

    private fun displayHostName(url: String): String =
        try {
            Rfc3986.host(url).removePrefix("moq.").removePrefix("nests.")
        } catch (_: Throwable) {
            url
        }
}

/**
 * Display-friendly entry for [NestsServersViewModel].
 *   - [name] — short host label (e.g. `nostrnests.com`)
 *   - [baseUrl] — exact bytes that go on the wire (`https://moq.nostrnests.com`)
 */
data class NestsServer(
    val name: String,
    val baseUrl: String,
)
