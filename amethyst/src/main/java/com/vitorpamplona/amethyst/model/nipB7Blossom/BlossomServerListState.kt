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
package com.vitorpamplona.amethyst.model.nipB7Blossom

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BlossomServerListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val blossomListNote = cache.getOrCreateAddressableNote(getBlossomServersAddress())

    fun getBlossomServersAddress() = BlossomServersEvent.createAddress(signer.pubKey)

    fun getBlossomServersListFlow(): StateFlow<NoteState> = blossomListNote.flow().metadata.stateFlow

    fun getBlossomServersList(): BlossomServersEvent? = blossomListNote.event as? BlossomServersEvent

    fun normalizeServers(note: Note): List<String> {
        val event = note.event as? BlossomServersEvent
        return event?.servers() ?: emptyList()
    }

    fun host(url: String): String =
        try {
            Rfc3986.host(url).removePrefix("cdn.").removePrefix("blossom.")
        } catch (e: Exception) {
            url.removePrefix("cdn.").removePrefix("blossom.")
        }

    val flow =
        getBlossomServersListFlow()
            .map {
                normalizeServers(it.note)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    /**
     * Kind-10063 Blossom hosts only. Does not mutate the persisted default file
     * server, so an Originless default is not snapped back when a 10063 event loads.
     */
    val hostNameFlow: StateFlow<List<ServerName>> =
        flow
            .map { servers ->
                servers.map { ServerName(host(it), it, ServerType.Blossom) }.ifEmpty { DEFAULT_MEDIA_SERVERS }
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                DEFAULT_MEDIA_SERVERS,
            )

    suspend fun saveBlossomServersList(servers: List<String>): BlossomServersEvent {
        val serverList = getBlossomServersList()

        return if (serverList != null && serverList.tags.isNotEmpty()) {
            BlossomServersEvent.updateRelayList(
                earlierVersion = serverList,
                servers = servers,
                signer = signer,
            )
        } else {
            BlossomServersEvent.createFromScratch(
                relays = servers,
                signer = signer,
            )
        }
    }

    suspend fun createBlossomUploadAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        servers: List<String> = emptyList(),
    ): BlossomAuthorizationEvent = BlossomAuthorizationEvent.createUploadAuth(hash, size, alt, signer, servers)

    suspend fun createBlossomMediaAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        servers: List<String> = emptyList(),
    ): BlossomAuthorizationEvent = BlossomAuthorizationEvent.createMediaAuth(hash, size, alt, signer, servers)

    suspend fun createBlossomDeleteAuth(
        hash: HexKey,
        alt: String,
        servers: List<String> = emptyList(),
    ): BlossomAuthorizationEvent = BlossomAuthorizationEvent.createDeleteAuth(hash, alt, signer, servers)

    suspend fun createBlossomListAuth(
        alt: String,
        servers: List<String> = emptyList(),
    ): BlossomAuthorizationEvent = BlossomAuthorizationEvent.createListAuth(signer, alt, servers)
}
