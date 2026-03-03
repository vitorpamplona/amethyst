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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class BlossomServersViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private val _fileServers = MutableStateFlow<List<ServerName>>(emptyList())
    val fileServers = _fileServers.asStateFlow()
    private var isModified = false

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun load() {
        refresh()
    }

    fun refresh() {
        isModified = false
        _fileServers.update {
            val obtainedFileServers = obtainFileServers() ?: emptyList()
            obtainedFileServers.mapNotNull { serverUrl ->
                try {
                    ServerName(
                        Rfc3986.host(serverUrl),
                        serverUrl,
                        ServerType.Blossom,
                    )
                } catch (e: Exception) {
                    Log.d("MediaServersViewModel", "Invalid URL in Blossom server list")
                    null
                }
            }
        }
    }

    fun addServerList(serverList: List<String>) {
        serverList.forEach { serverUrl ->
            addServer(serverUrl)
        }
    }

    fun addServer(serverUrl: String) {
        val normalizedUrl =
            try {
                Rfc3986.normalize(serverUrl.trim())
            } catch (e: Exception) {
                serverUrl
            }
        val serverNameReference =
            try {
                Rfc3986.host(normalizedUrl)
            } catch (e: Exception) {
                normalizedUrl
            }
        val serverRef =
            ServerName(
                serverNameReference,
                normalizedUrl,
                ServerType.Blossom,
            )
        if (_fileServers.value.contains(serverRef)) {
            return
        } else {
            _fileServers.update {
                it.plus(serverRef)
            }
        }
        isModified = true
    }

    fun removeServer(
        name: String = "",
        serverUrl: String,
    ) {
        viewModelScope.launch {
            val serverName = name.ifBlank { Rfc3986.host(serverUrl) }
            _fileServers.update {
                it.minus(
                    ServerName(serverName, serverUrl, ServerType.Blossom),
                )
            }
            isModified = true
        }
    }

    fun removeAllServers() {
        _fileServers.update { emptyList() }
        isModified = true
    }

    fun saveFileServers() {
        if (isModified) {
            accountViewModel.launchSigner {
                val serverList = _fileServers.value.map { it.baseUrl }
                account.sendBlossomServersList(serverList)
                refresh()
            }
        }
    }

    private fun obtainFileServers(): List<String> = account.blossomServers.flow.value
}
