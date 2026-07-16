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
package com.vitorpamplona.amethyst.commons.model.nipB7Blossom

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Shared, platform-agnostic state holder for the user's Blossom media server
 * list (NIP-B7 / kind 10063 [BlossomServersEvent]).
 *
 * This is the same event kind the Amethyst mobile app reads through its own
 * `BlossomServerListState`: it loads the addressable event from the injected
 * [ICacheProvider] and exposes the declared server URLs as a [StateFlow]. Both
 * the Android and Desktop front ends can consume this so a server list
 * configured on one client shows up on the other.
 */
class BlossomServerListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note itself
    val blossomListNote = cache.getOrCreateAddressableNote(getBlossomServersAddress())

    fun getBlossomServersAddress() = BlossomServersEvent.createAddress(signer.pubKey)

    fun getBlossomServersListFlow(): StateFlow<NoteState> = blossomListNote.flow().metadata.stateFlow

    fun getBlossomServersList(): BlossomServersEvent? = blossomListNote.event as? BlossomServersEvent

    fun normalizeServers(note: Note): List<String> = (note.event as? BlossomServersEvent)?.servers() ?: emptyList()

    val flow: StateFlow<List<String>> =
        getBlossomServersListFlow()
            .map { normalizeServers(it.note) }
            .onStart { emit(normalizeServers(blossomListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
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
}
