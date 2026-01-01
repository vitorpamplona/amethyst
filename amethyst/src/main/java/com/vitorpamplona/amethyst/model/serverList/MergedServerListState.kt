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
package com.vitorpamplona.amethyst.model.serverList

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User


import com.vitorpamplona.amethyst.model.nip96FileStorage.FileStorageServerListState
import com.vitorpamplona.amethyst.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class MergedServerListState(
    val fileServers: FileStorageServerListState,
    val blossomServers: BlossomServerListState,
    val scope: CoroutineScope,
) {
    fun host(url: String): String =
        try {
            Rfc3986.host(url)
        } catch (e: Exception) {
            url
        }

    fun mergeServerList(
        nip96: List<String>?,
        blossom: List<String>?,
    ): List<ServerName> {
        val nip96servers = nip96?.map { ServerName(host(it), it, ServerType.NIP96) } ?: emptyList()
        val blossomServers = blossom?.map { ServerName(host(it), it, ServerType.Blossom) } ?: emptyList()

        val result = (nip96servers + blossomServers).ifEmpty { DEFAULT_MEDIA_SERVERS }

        return result + ServerName("NIP95", "", ServerType.NIP95)
    }

    val liveServerList: StateFlow<List<ServerName>> =
        combine(fileServers.flow, blossomServers.flow) { nip96s, blossoms ->
            mergeServerList(nip96s, blossoms)
        }.onStart {
            emit(mergeServerList(fileServers.flow.value, blossomServers.flow.value))
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                DEFAULT_MEDIA_SERVERS,
            )
}
