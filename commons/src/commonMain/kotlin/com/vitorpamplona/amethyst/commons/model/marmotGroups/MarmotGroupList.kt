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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Tracks all Marmot MLS group chatrooms for an account.
 * Follows the same pattern as [com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList].
 */
class MarmotGroupList {
    var rooms = LargeCache<HexKey, MarmotGroupChatroom>()
        private set

    private val _groupListChanges = MutableSharedFlow<HexKey>(0, 20, BufferOverflow.DROP_OLDEST)
    val groupListChanges = _groupListChanges

    fun getOrCreateGroup(nostrGroupId: HexKey): MarmotGroupChatroom = rooms.getOrCreate(nostrGroupId) { MarmotGroupChatroom(nostrGroupId) }

    fun addMessage(
        nostrGroupId: HexKey,
        msg: Note,
    ) {
        val chatroom = getOrCreateGroup(nostrGroupId)
        if (chatroom.addMessageSync(msg)) {
            _groupListChanges.tryEmit(nostrGroupId)
        }
    }

    fun removeMessage(
        nostrGroupId: HexKey,
        msg: Note,
    ) {
        val chatroom = getOrCreateGroup(nostrGroupId)
        if (chatroom.removeMessageSync(msg)) {
            _groupListChanges.tryEmit(nostrGroupId)
        }
    }

    fun allGroupIds(): List<HexKey> {
        val result = mutableListOf<HexKey>()
        rooms.forEach { key, _ -> result.add(key) }
        return result
    }
}
