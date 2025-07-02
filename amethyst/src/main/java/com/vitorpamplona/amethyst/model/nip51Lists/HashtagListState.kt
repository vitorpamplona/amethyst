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
import com.vitorpamplona.quartz.nip51Lists.interests.HashtagListEvent
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

class HashtagListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    fun getHashtagListAddress() = HashtagListEvent.createAddress(signer.pubKey)

    fun getHashtagListNote(): AddressableNote = cache.getOrCreateAddressableNote(getHashtagListAddress())

    fun getHashtagListFlow(): StateFlow<NoteState> = getHashtagListNote().flow().metadata.stateFlow

    fun getHashtagList(): HashtagListEvent? = getHashtagListNote().event as? HashtagListEvent

    suspend fun hashtagListWithBackup(note: Note): Set<String> {
        return hashtagList(
            note.event as? HashtagListEvent ?: settings.backupHashtagList,
        )
    }

    suspend fun hashtagList(event: HashtagListEvent?): Set<String> {
        return tryAndWait { continuation ->
            event?.publicAndPrivateHashtag(signer) {
                continuation.resume(it)
            }
        } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<String>> by lazy {
        getHashtagListFlow()
            .transformLatest { noteState ->
                emit(hashtagListWithBackup(noteState.note))
            }.onStart {
                emit(hashtagListWithBackup(getHashtagListNote()))
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )
    }

    fun follow(
        hashtags: List<String>,
        onDone: (HashtagListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val hashtagList = getHashtagList()

        if (hashtagList == null) {
            HashtagListEvent.createHashtags(hashtags, true, signer, onReady = onDone)
        } else {
            HashtagListEvent.addHashtags(hashtagList, hashtags, true, signer, onReady = onDone)
        }
    }

    fun follow(
        hashtag: String,
        onDone: (HashtagListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val hashtagList = getHashtagList()

        if (hashtagList == null) {
            HashtagListEvent.createHashtag(hashtag, true, signer, onReady = onDone)
        } else {
            HashtagListEvent.addHashtag(hashtagList, hashtag, true, signer, onReady = onDone)
        }
    }

    fun unfollow(
        hashtag: String,
        onDone: (HashtagListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val hashtagList = getHashtagList()

        if (hashtagList != null) {
            HashtagListEvent.removeHashtag(hashtagList, hashtag, signer, onReady = onDone)
        }
    }

    init {
        settings.backupHashtagList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved Hashtag list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
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
