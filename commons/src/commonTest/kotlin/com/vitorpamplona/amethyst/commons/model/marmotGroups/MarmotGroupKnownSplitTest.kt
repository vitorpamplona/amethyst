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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A group the user was invited to sits under New Requests until they take part. Replying must
 * move it to Known right away: the list screen used to cache the split on inputs that a reply
 * doesn't change (the same rooms, the same follows), so the group stayed under New Requests
 * until the screen was re-entered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarmotGroupKnownSplitTest {
    private val owner = "a".repeat(64)
    private val peer = "b".repeat(64)
    private val groupId = "c".repeat(64)
    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun chat(
        pubKey: HexKey,
        id: String,
    ): Note {
        val event = Event(id, pubKey, 1_800_000_000L, 9, emptyArray(), "hi", "")
        return Note(event.id).also { it.loadEvent(event, User(pubKey, context), emptyList()) }
    }

    private fun TestScope.testDispatcher() = UnconfinedTestDispatcher(testScheduler)

    @Test
    fun theDeviceGroupIsNeverARequest() =
        runTest(UnconfinedTestDispatcher()) {
            val list = MarmotGroupList(owner)
            val deviceGroup = "f".repeat(64)
            list.isHidden = { it == deviceGroup }
            val seen = mutableListOf<MarmotGroupSplit>()
            backgroundScope.launch {
                list.knownSplit(MutableStateFlow(emptySet()), extraGroupIds = { listOf(deviceGroup, groupId) }, context = testDispatcher()).collect { seen.add(it) }
            }

            assertEquals(listOf(groupId), seen.last().requests.map { it.first })
            assertEquals(emptyList(), seen.last().known.map { it.first })
        }

    @Test
    fun replyingMovesTheGroupToKnownAtOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val list = MarmotGroupList(owner)
            list.getOrCreateGroup(groupId).adminPubkeys.value = listOf(peer)
            list.addMessage(groupId, chat(peer, "1".repeat(64)))

            val follows = MutableStateFlow<Set<HexKey>>(emptySet())
            val seen = mutableListOf<MarmotGroupSplit>()
            backgroundScope.launch { list.knownSplit(follows, context = testDispatcher()).collect { seen.add(it) } }

            assertEquals(listOf(groupId), seen.last().requests.map { it.first })
            assertEquals(emptyList(), seen.last().known.map { it.first })

            list.addMessage(groupId, chat(owner, "2".repeat(64)))

            assertEquals(listOf(groupId), seen.last().known.map { it.first })
            assertEquals(emptyList(), seen.last().requests.map { it.first })
        }

    @Test
    fun followingAnAdminMovesTheGroupToKnown() =
        runTest(UnconfinedTestDispatcher()) {
            val list = MarmotGroupList(owner)
            list.getOrCreateGroup(groupId).adminPubkeys.value = listOf(peer)
            list.addMessage(groupId, chat(peer, "1".repeat(64)))
            val follows = MutableStateFlow<Set<HexKey>>(emptySet())
            val seen = mutableListOf<MarmotGroupSplit>()
            backgroundScope.launch { list.knownSplit(follows, context = testDispatcher()).collect { seen.add(it) } }

            follows.value = setOf(peer)

            assertEquals(listOf(groupId), seen.last().known.map { it.first })
        }

    @Test
    fun groupsAreNewestFirst() =
        runTest(UnconfinedTestDispatcher()) {
            val list = MarmotGroupList(owner)
            val older = "d".repeat(64)
            list.addMessage(older, Note("e".repeat(64)).also { it.loadEvent(Event("e".repeat(64), owner, 1_700_000_000L, 9, emptyArray(), "old", ""), User(owner, context), emptyList()) })
            list.addMessage(groupId, chat(owner, "2".repeat(64)))
            val seen = mutableListOf<MarmotGroupSplit>()
            backgroundScope.launch { list.knownSplit(MutableStateFlow(emptySet()), context = testDispatcher()).collect { seen.add(it) } }

            assertEquals(listOf(groupId, older), seen.last().known.map { it.first })
        }
}
