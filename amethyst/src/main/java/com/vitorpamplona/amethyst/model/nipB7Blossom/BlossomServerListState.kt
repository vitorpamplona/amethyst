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
package com.vitorpamplona.amethyst.model.nipB7Blossom

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class BlossomServerListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    fun getBlossomServersAddress() = BlossomServersEvent.createAddress(signer.pubKey)

    fun getBlossomServersNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(getBlossomServersAddress())

    fun getBlossomServersListFlow(): StateFlow<NoteState> = getBlossomServersNote().flow().metadata.stateFlow

    fun getBlossomServersList(): BlossomServersEvent? = getBlossomServersNote().event as? BlossomServersEvent

    fun normalizeServers(note: Note): List<String> {
        val event = note.event as? FileServersEvent
        return event?.servers() ?: emptyList()
    }

    val fileServers =
        getBlossomServersListFlow()
            .map { normalizeServers(it.note) }
            .onStart { emit(normalizeServers(getBlossomServersNote())) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    fun saveBlossomServersList(
        servers: List<String>,
        onDone: (BlossomServersEvent) -> Unit,
    ) {
        val serverList = getBlossomServersList()

        if (serverList != null && serverList.tags.isNotEmpty()) {
            BlossomServersEvent.updateRelayList(
                earlierVersion = serverList,
                relays = servers,
                signer = signer,
                onReady = onDone,
            )
        } else {
            BlossomServersEvent.createFromScratch(
                relays = servers,
                signer = signer,
                onReady = onDone,
            )
        }
    }
}
