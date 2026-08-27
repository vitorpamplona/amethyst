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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.originless.OriginlessUrls
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.privacyOptions.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class OriginlessServersViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account
    private var httpClientBuilder: IRoleBasedHttpClientBuilder? = null

    private val _fileServers = MutableStateFlow<List<ServerName>>(emptyList())
    val fileServers = _fileServers.asStateFlow()

    /** Reachability status per server, keyed by [ServerName.baseUrl]. */
    private val _health = MutableStateFlow<Map<String, ServerHealth>>(emptyMap())
    val health = _health.asStateFlow()

    private var isModified = false

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.httpClientBuilder = accountViewModel.httpClientBuilder
    }

    fun load() {
        refresh()
        checkAllHealth()
    }

    fun refresh() {
        isModified = false
        _fileServers.update {
            account.settings.originlessServerUrls.value
                .map { originlessServer(it) }
        }
        pruneHealth()
    }

    /** Moves a server to a new position; list order is fetch-failover priority. */
    fun moveServer(
        from: Int,
        to: Int,
    ) {
        _fileServers.update { list ->
            if (from !in list.indices || to !in list.indices) return@update list
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
        isModified = true
    }

    fun checkAllHealth() {
        _fileServers.value.forEach { probeServer(it.baseUrl) }
    }

    private fun probeServer(serverUrl: String) {
        val builder = httpClientBuilder ?: return

        if (_health.value[serverUrl] == ServerHealth.Checking) return

        MediaServerHealthProbe.cached(serverUrl)?.let { cachedStatus ->
            _health.update { it + (serverUrl to cachedStatus) }
            return
        }

        _health.update { it + (serverUrl to ServerHealth.Checking) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = MediaServerHealthProbe.probeOriginless(serverUrl, builder::okHttpClientForPreview)
            _health.update { it + (serverUrl to result) }
        }
    }

    private fun pruneHealth() {
        val liveUrls = _fileServers.value.mapTo(HashSet()) { it.baseUrl }
        _health.update { statuses -> statuses.filterKeys { it in liveUrls } }
    }

    fun addServerList(serverList: List<String>) {
        var added = false
        serverList.forEach { if (addServerInternal(it)) added = true }
        if (added) persist()
    }

    fun addServer(serverUrl: String) {
        if (addServerInternal(serverUrl)) persist()
    }

    private fun addServerInternal(serverUrl: String): Boolean {
        val serverRef = originlessServer(serverUrl)
        if (_fileServers.value.any { it.baseUrl == serverRef.baseUrl }) return false

        _fileServers.update { it.plus(serverRef) }
        probeServer(serverRef.baseUrl)
        isModified = true
        return true
    }

    fun removeServer(serverUrl: String) {
        _fileServers.update { list ->
            list.filterNot { it.baseUrl == OriginlessUrls.normalizeBase(serverUrl) }
        }
        pruneHealth()
        isModified = true
        persist()
    }

    fun persistPending() = persist()

    private fun persist() {
        if (!isModified) return
        isModified = false
        account.settings.changeOriginlessServerUrls(_fileServers.value.map { it.baseUrl })
    }
}
