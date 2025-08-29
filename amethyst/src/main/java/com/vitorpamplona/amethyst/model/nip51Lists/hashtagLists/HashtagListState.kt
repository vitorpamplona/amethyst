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
package com.vitorpamplona.amethyst.model.nip51Lists.hashtagLists

import android.util.Log
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
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

class HashtagListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: HashtagListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val hashtagListNote = cache.getOrCreateAddressableNote(getHashtagListAddress())

    fun getHashtagListAddress() = HashtagListEvent.createAddress(signer.pubKey)

    fun getHashtagListFlow(): StateFlow<NoteState> = hashtagListNote.flow().metadata.stateFlow

    fun getHashtagList(): HashtagListEvent? = hashtagListNote.event as? HashtagListEvent

    suspend fun hashtagListWithBackup(note: Note): Set<String> {
        val event = note.event as? HashtagListEvent ?: settings.backupHashtagList
        return event?.let { decryptionCache.hashtags(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<String>> =
        getHashtagListFlow()
            .transformLatest { noteState ->
                emit(hashtagListWithBackup(noteState.note))
            }.onStart {
                emit(hashtagListWithBackup(hashtagListNote))
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Companion.Eagerly,
                emptySet(),
            )

    suspend fun follow(hashtags: List<String>): HashtagListEvent {
        val hashtagList = getHashtagList()

        return if (hashtagList == null) {
            HashtagListEvent.Companion.create(hashtags, true, signer)
        } else {
            HashtagListEvent.Companion.add(hashtagList, hashtags, true, signer)
        }
    }

    suspend fun follow(hashtag: String): HashtagListEvent {
        val hashtagList = getHashtagList()

        return if (hashtagList == null) {
            HashtagListEvent.Companion.create(hashtag, true, signer)
        } else {
            HashtagListEvent.Companion.add(hashtagList, hashtag, true, signer)
        }
    }

    suspend fun unfollow(hashtag: String): HashtagListEvent? {
        val hashtagList = getHashtagList()

        return if (hashtagList != null) {
            HashtagListEvent.Companion.remove(hashtagList, hashtag, signer)
        } else {
            null
        }
    }

    init {
        settings.backupHashtagList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved Hashtag list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Hashtag List Collector Start")
            getHashtagListFlow().collect {
                Log.d("AccountRegisterObservers", "Hashtag List for ${signer.pubKey}")
                (it.note.event as? HashtagListEvent)?.let {
                    settings.updateHashtagListTo(it)
                }
            }
        }
    }
}
