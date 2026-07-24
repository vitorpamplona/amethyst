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
package com.vitorpamplona.amethyst.desktop.model

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.nip51Lists.muteList.MuteListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.peopleList.PeopleListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Desktop mute/block state holder.
 *
 * Assembles the user's NIP-51 mute list (kind 10000, [MuteListEvent]) and the
 * legacy block people list (kind 30000 `d=mute`, [PeopleListEvent]) into a single
 * [LiveHiddenUsers] value that [com.vitorpamplona.amethyst.desktop.model.DesktopIAccount]
 * feeds to [com.vitorpamplona.amethyst.commons.model.Note.isHiddenFor].
 *
 * Both lists carry a mix of public tags and an NIP-44-encrypted private section;
 * the shared [MuteListDecryptionCache]/[PeopleListDecryptionCache] handle the
 * async decrypt (and are no-ops for read-only accounts, which simply see the
 * public portion). The result is exposed as a hot [StateFlow] so feeds can
 * re-filter live: when the list events change (or decryption resolves), a new
 * [LiveHiddenUsers] emits and observers call `invalidateData()`.
 *
 * Mirrors Android's `HiddenUsersState`, but self-contained for the desktop
 * cache/account shape (no `AccountSettings` dependency).
 */
class DesktopHiddenUsersState(
    private val signer: NostrSigner,
    private val cache: ICacheProvider,
    private val scope: CoroutineScope,
    /** From the "always show sensitive content" setting; `null` = respect content warnings. */
    private val showSensitiveContent: StateFlow<Boolean?> = MutableStateFlow(null),
) {
    private val muteCache = MuteListDecryptionCache(signer)
    private val blockCache = PeopleListDecryptionCache(signer)

    // Strong refs so the GC keeps these addressable notes (and their decrypt caches) alive.
    private val muteListNote = cache.getOrCreateAddressableNote(MuteListEvent.createAddress(signer.pubKey))
    private val blockListNote = cache.getOrCreateAddressableNote(PeopleListEvent.createBlockAddress(signer.pubKey))

    /** Session-only user hides (e.g. "hide this spammer" without persisting a mute). */
    val transientHiddenUsers = MutableStateFlow<Set<String>>(emptySet())

    private val muteEventFlow: StateFlow<NoteState> = muteListNote.flow().metadata.stateFlow
    private val blockEventFlow: StateFlow<NoteState> = blockListNote.flow().metadata.stateFlow

    private suspend fun assemble(
        muteEvent: MuteListEvent?,
        blockEvent: PeopleListEvent?,
        transient: Set<String>,
        showSensitive: Boolean?,
    ): LiveHiddenUsers {
        val hiddenUsers = mutableSetOf<String>()
        val hiddenWords = mutableSetOf<String>()
        val mutedThreads = mutableSetOf<String>()

        if (muteEvent != null) {
            hiddenUsers.addAll(muteCache.mutedUserIdSet(muteEvent))
            hiddenWords.addAll(muteCache.mutedWordSet(muteEvent).map { it.word })
            mutedThreads.addAll(muteCache.mutedThreadIdSet(muteEvent))
        }
        if (blockEvent != null) {
            hiddenUsers.addAll(blockCache.userIdSet(blockEvent))
        }

        return LiveHiddenUsers(
            showSensitiveContent = showSensitive,
            hiddenWordsCase = hiddenWords.map { DualCase(it.lowercase(), it.uppercase()) },
            hiddenUsersHashCodes = hiddenUsers.mapTo(HashSet()) { it.hashCode() },
            spammersHashCodes = transient.mapTo(HashSet()) { it.hashCode() },
            hiddenUsers = hiddenUsers,
            spammers = transient,
            hiddenWords = hiddenWords,
            mutedThreads = mutedThreads,
        )
    }

    /** Hot flow of the current moderation choices. Emits on every list/setting change. */
    val flow: StateFlow<LiveHiddenUsers> =
        combine(
            muteEventFlow,
            blockEventFlow,
            transientHiddenUsers,
            showSensitiveContent,
        ) { muteState, blockState, transient, showSensitive ->
            assemble(
                muteState.note.event as? MuteListEvent,
                blockState.note.event as? PeopleListEvent,
                transient,
                showSensitive,
            )
        }.onStart { emit(LiveHiddenUsers.EMPTY) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, LiveHiddenUsers.EMPTY)

    /** The user's current mute list event, or null if none has loaded yet. */
    fun currentMuteList(): MuteListEvent? = muteListNote.event as? MuteListEvent

    fun hideUserTransiently(pubkeyHex: String) {
        transientHiddenUsers.value = transientHiddenUsers.value + pubkeyHex
    }

    fun showUserTransiently(pubkeyHex: String) {
        transientHiddenUsers.value = transientHiddenUsers.value - pubkeyHex
    }
}
