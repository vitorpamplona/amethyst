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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User


import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class HiddenUsersState(
    val muteList: StateFlow<List<MuteTag>>,
    val blockList: StateFlow<List<MuteTag>>,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    var transientHiddenUsers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf())

    @Immutable
    class LiveHiddenUsers(
        val hiddenUsers: Set<String>,
        val spammers: Set<String>,
        val hiddenWords: Set<String>,
        val showSensitiveContent: Boolean?,
    ) {
        // speeds up isHidden calculations
        val hiddenUsersHashCodes = hiddenUsers.mapTo(HashSet()) { it.hashCode() }
        val spammersHashCodes = spammers.mapTo(HashSet()) { it.hashCode() }
        val hiddenWordsCase = hiddenWords.map { DualCase(it.lowercase(), it.uppercase()) }

        fun isUserHidden(userHex: HexKey) = hiddenUsers.contains(userHex) || spammers.contains(userHex)
    }

    suspend fun assembleLiveHiddenUsers(
        blockList: List<MuteTag>,
        muteList: List<MuteTag>,
        transientHiddenUsers: Set<String>,
        showSensitiveContent: Boolean?,
    ): LiveHiddenUsers =
        LiveHiddenUsers(
            hiddenUsers = blockList.mapNotNullTo(mutableSetOf()) { if (it is UserTag) it.pubKey else null } + muteList.mapNotNull { if (it is UserTag) it.pubKey else null },
            hiddenWords = blockList.mapNotNullTo(mutableSetOf()) { if (it is WordTag) it.word else null } + muteList.mapNotNull { if (it is WordTag) it.word else null },
            spammers = transientHiddenUsers,
            showSensitiveContent = showSensitiveContent,
        )

    val flow: StateFlow<LiveHiddenUsers> =
        combineTransform(
            blockList,
            muteList,
            transientHiddenUsers,
            settings.syncedSettings.security.showSensitiveContent,
        ) { blockList, muteList, transientHiddenUsers, showSensitiveContent ->
            checkNotInMainThread()
            emit(assembleLiveHiddenUsers(blockList, muteList, transientHiddenUsers, showSensitiveContent))
        }.onStart {
            emit(
                assembleLiveHiddenUsers(
                    blockList.value,
                    muteList.value,
                    transientHiddenUsers.value,
                    settings.syncedSettings.security.showSensitiveContent.value,
                ),
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                LiveHiddenUsers(emptySet(), emptySet(), emptySet(), null),
            )

    fun resetTransientUsers() {
        transientHiddenUsers.update {
            emptySet()
        }
    }

    fun showUser(pubkeyHex: HexKey) {
        transientHiddenUsers.update { it - pubkeyHex }
    }

    fun hideUser(pubkeyHex: HexKey) {
        transientHiddenUsers.update { it + pubkeyHex }
    }

    fun isHidden(pubkeyHex: HexKey) = pubkeyHex in transientHiddenUsers.value
}
