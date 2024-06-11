/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.Nip96MediaServers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.czeal.rfc3986.URIReference

class MediaServersViewModel : ViewModel() {
    lateinit var account: Account

    private val _fileServers = MutableStateFlow<List<Nip96MediaServers.ServerName>>(emptyList())
    val fileServers = _fileServers.asStateFlow()
    var isModified = false

    fun load(account: Account) {
        this.account = account
        refresh()
    }

    fun refresh() {
        isModified = false
        _fileServers.update {
            val obtainedFileServers = account.getFileServersList()?.servers() ?: emptyList()
            obtainedFileServers.map { serverUrl ->
                Nip96MediaServers
                    .ServerName(
                        URIReference.parse(serverUrl).host.value,
                        serverUrl,
                    )
            }
        }
    }

    fun addServer(serverUrl: String) {
    }

    fun removeServer(
        name: String = "",
        serverUrl: String,
    ) {
    }

    fun removeAll() {
        _fileServers.update { emptyList() }
        isModified = true
    }
}
