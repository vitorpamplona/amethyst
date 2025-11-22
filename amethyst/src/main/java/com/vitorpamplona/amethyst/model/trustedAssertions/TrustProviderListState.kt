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
package com.vitorpamplona.amethyst.model.trustedAssertions

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.experimental.trustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.experimental.trustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class TrustProviderListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: TrustProviderListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val trustProviderListNote = cache.getOrCreateAddressableNote(getTrustProviderListAddress())

    fun getTrustProviderListAddress() = TrustProviderListEvent.createAddress(signer.pubKey)

    fun getTrustProviderListFlow(): StateFlow<NoteState> = trustProviderListNote.flow().metadata.stateFlow

    fun getTrustProviderList(): TrustProviderListEvent? = trustProviderListNote.event as? TrustProviderListEvent

    suspend fun trustProviderListWithBackup(note: Note): Set<ServiceProviderTag> {
        val event = note.event as? TrustProviderListEvent ?: settings.backupTrustProviderList
        return event?.let { decryptionCache.serviceProviderSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveTrustProviderList: StateFlow<Set<ServiceProviderTag>> =
        getTrustProviderListFlow()
            .transformLatest { noteState ->
                emit(trustProviderListWithBackup(noteState.note))
            }.onStart {
                emit(trustProviderListWithBackup(trustProviderListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    init {
        settings.backupTrustProviderList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved ephemeral chat list")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "TrustProviderList Collector Start")
            getTrustProviderListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers", "TrustProviderList List for ${signer.pubKey}")
                (noteState.note.event as? TrustProviderListEvent)?.let {
                    settings.updateTrustProviderListTo(it)
                }
            }
        }
    }
}
