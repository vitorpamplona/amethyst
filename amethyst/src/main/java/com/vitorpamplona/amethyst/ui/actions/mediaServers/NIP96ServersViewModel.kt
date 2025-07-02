/**
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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.czeal.rfc3986.URIReference

class NIP96ServersViewModel : ViewModel() {
    lateinit var account: Account

    private val _fileServers = MutableStateFlow<List<ServerName>>(emptyList())
    val fileServers = _fileServers.asStateFlow()
    private var isModified = false

    fun load(account: Account) {
        this.account = account
        refresh()
    }

    fun refresh() {
        isModified = false
        _fileServers.update {
            val obtainedFileServers = obtainFileServers() ?: emptyList()
            obtainedFileServers.mapNotNull { serverUrl ->
                try {
                    ServerName(
                        URIReference.parse(serverUrl).host.value,
                        serverUrl,
                        ServerType.NIP96,
                    )
                } catch (e: Exception) {
                    Log.d("MediaServersViewModel", "Invalid URL in NIP-96 server list")
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
                URIReference.parse(serverUrl.trim()).normalize().toString()
            } catch (e: Exception) {
                serverUrl
            }
        val serverNameReference =
            try {
                URIReference.parse(normalizedUrl).host.value
            } catch (e: Exception) {
                normalizedUrl
            }
        val serverRef = ServerName(serverNameReference, normalizedUrl, ServerType.NIP96)
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
            val serverName = if (name.isNotBlank()) name else URIReference.parse(serverUrl).host.value
            _fileServers.update {
                it.minus(
                    ServerName(serverName, serverUrl, ServerType.NIP96),
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
            viewModelScope.launch(Dispatchers.IO) {
                val serverList = _fileServers.value.map { it.baseUrl }
                account.sendFileServersList(serverList)
                refresh()
            }
        }
    }

    private fun obtainFileServers(): List<String>? = account.fileStorageServers.fileServers.value
}
