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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

class MuteListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    fun getMuteListAddress() = MuteListEvent.createAddress(signer.pubKey)

    fun getMuteListNote() = cache.getOrCreateAddressableNote(getMuteListAddress())

    fun getMuteListFlow(): StateFlow<NoteState> = getMuteListNote().flow().metadata.stateFlow

    fun getMuteList(): MuteListEvent? = getMuteListNote().event as? MuteListEvent

    suspend fun muteListWithBackup(note: Note): PeopleListEvent.UsersAndWords =
        muteList(
            note.event as? MuteListEvent ?: settings.backupMuteList,
        )

    suspend fun muteList(event: MuteListEvent?): PeopleListEvent.UsersAndWords =
        tryAndWait { continuation ->
            event?.publicAndPrivateUsersAndWords(signer) {
                continuation.resume(it)
            }
        } ?: PeopleListEvent.UsersAndWords()

    val flow =
        getMuteListFlow()
            .map { muteListWithBackup(it.note) }
            .onStart { emit(muteListWithBackup(getMuteListNote())) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                PeopleListEvent.UsersAndWords(),
            )

    fun hideUser(
        pubkeyHex: String,
        onDone: (MuteListEvent) -> Unit,
    ) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.addUser(
                earlierVersion = muteList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        } else {
            MuteListEvent.createListWithUser(
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun showUser(
        pubkeyHex: String,
        onDone: (MuteListEvent) -> Unit,
    ) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeUser(
                earlierVersion = muteList,
                pubKeyHex = pubkeyHex,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun hideWord(
        word: String,
        onDone: (MuteListEvent) -> Unit,
    ) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.addWord(
                earlierVersion = muteList,
                word = word,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        } else {
            MuteListEvent.createListWithWord(
                word = word,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun showWord(
        word: String,
        onDone: (MuteListEvent) -> Unit,
    ) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeWord(
                earlierVersion = muteList,
                word = word,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    init {
        settings.backupMuteList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved mute list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Mute List Collector Start")
            getMuteListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Mute List for ${signer.pubKey}")
                (it.note.event as? MuteListEvent)?.let {
                    settings.updateMuteList(it)
                }
            }
        }
    }
}
