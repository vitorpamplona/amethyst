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
package com.vitorpamplona.amethyst.model.nip51Lists

import android.util.Log
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.locations.GeohashListEvent
import com.vitorpamplona.quartz.utils.tryAndWait
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
import kotlin.coroutines.resume

class GeohashListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    fun getGeohashListAddress() = GeohashListEvent.createAddress(signer.pubKey)

    fun getGeohashListNote(): AddressableNote = cache.getOrCreateAddressableNote(getGeohashListAddress())

    fun getGeohashListFlow(): StateFlow<NoteState> = getGeohashListNote().flow().metadata.stateFlow

    fun getGeohashList(): GeohashListEvent? = getGeohashListNote().event as? GeohashListEvent

    suspend fun geohashListWithBackup(note: Note): Set<String> =
        geohashList(
            note.event as? GeohashListEvent ?: settings.backupGeohashList,
        )

    suspend fun geohashList(event: GeohashListEvent?): Set<String> =
        tryAndWait { continuation ->
            event?.publicAndPrivateGeohash(signer) {
                continuation.resume(it)
            }
        } ?: emptySet()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<String>> by lazy {
        getGeohashListFlow()
            .transformLatest { noteState ->
                emit(geohashListWithBackup(noteState.note))
            }.onStart {
                emit(geohashListWithBackup(getGeohashListNote()))
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )
    }

    fun follow(
        geohashs: List<String>,
        onDone: (GeohashListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val geohashList = getGeohashList()

        if (geohashList == null) {
            GeohashListEvent.createGeohashs(geohashs, true, signer, onReady = onDone)
        } else {
            GeohashListEvent.addGeohashs(geohashList, geohashs, true, signer, onReady = onDone)
        }
    }

    fun follow(
        geohash: String,
        onDone: (GeohashListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val geohashList = getGeohashList()

        if (geohashList == null) {
            GeohashListEvent.createGeohash(geohash, true, signer, onReady = onDone)
        } else {
            GeohashListEvent.addGeohash(geohashList, geohash, true, signer, onReady = onDone)
        }
    }

    fun unfollow(
        geohash: String,
        onDone: (GeohashListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val geohashList = getGeohashList()

        if (geohashList != null) {
            GeohashListEvent.removeGeohash(geohashList, geohash, signer, onReady = onDone)
        }
    }

    init {
        settings.backupGeohashList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved Geohash list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Geohash List Collector Start")
            getGeohashListFlow().collect {
                Log.d("AccountRegisterObservers", "Geohash List for ${signer.pubKey}")
                (it.note.event as? GeohashListEvent)?.let {
                    settings.updateGeohashListTo(it)
                }
            }
        }
    }
}
