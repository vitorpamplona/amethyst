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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountMarmotActions
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevices
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.quartz.utils.cache.LargeCache
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The read position of an open chat is shared once it settles (3 s); a chat left before that still
 * shares its last change, from the account's scope, so a quick look moves the other devices too.
 */
class MarmotReadPositionShareTest {
    private val chat = "ab".repeat(32)
    private val position = MarmotGroupChatroom.ReadPosition(4, 1_000, "cd".repeat(32))

    @Test
    fun aChatLeftWithinTheSettleTimeStillSharesItsReadPosition() =
        runBlocking {
            val shared = CompletableDeferred<Triple<Long, Long, String>>()
            val devices =
                mockk<MarmotDevices>().also {
                    every { it.state } returns MutableStateFlow(MarmotDevicesSnapshot(linked = true))
                    every { it.isDeviceGroup(any()) } returns false
                    coEvery { it.markRead(chat, any(), any(), any()) } answers {
                        shared.complete(Triple(secondArg<Long>(), thirdArg<Long>(), arg<String>(3)))
                        Unit
                    }
                }
            val room = mockk<MarmotGroupChatroom>().also { every { it.readPositionAt(any()) } returns position }
            val rooms = LargeCache<String, MarmotGroupChatroom>().also { it.put(chat, room) }
            val list = mockk<MarmotGroupList>().also { every { it.rooms } returns rooms }
            val lastRead = MutableStateFlow(0L)
            val account =
                mockk<Account>().also {
                    every { it.marmotDevices } returns devices
                    every { it.marmotGroupList } returns list
                    every { it.loadLastReadFlow(marmotGroupLastReadRoute(chat)) } returns lastRead
                    every { it.scope } returns CoroutineScope(SupervisorJob() + Dispatchers.IO)
                }
            val actions = AccountMarmotActions(account)

            // The chat opens, the user reads it, and leaves a second later: well inside the 3 s.
            val open = launch(Dispatchers.IO) { actions.shareMarmotReadPositions(chat) }
            delay(200)
            lastRead.value = 1_000
            delay(1_000)
            open.cancel()

            val (epoch, at, id) = withTimeout(5_000) { shared.await() }
            assertEquals(position.epoch, epoch)
            assertEquals(position.createdAt, at)
            assertEquals(position.eventId, id)
        }
}
