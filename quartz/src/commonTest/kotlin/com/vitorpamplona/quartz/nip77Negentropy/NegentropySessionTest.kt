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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NegentropySessionTest {
    private fun makeEvent(
        id: String,
        createdAt: Long,
    ): Event =
        Event(
            id = id,
            pubKey = "a".repeat(64),
            createdAt = createdAt,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "b".repeat(64),
        )

    @Test
    fun clientServerSync_identicalSets_noIdsExchanged() {
        val events =
            listOf(
                makeEvent("a".repeat(64), 1000),
                makeEvent("b".repeat(64), 2000),
                makeEvent("c".repeat(64), 3000),
            )

        val clientSession = NegentropySession("sub1", Filter(), events)
        val openCmd = clientSession.open()

        assertEquals("NEG-OPEN", openCmd.label())
        assertEquals("sub1", openCmd.subId)
        assertTrue(openCmd.initialMessage.isNotEmpty())

        // Server side: same events
        val serverStorage = StorageVector()
        for (e in events) {
            serverStorage.insert(e.createdAt, e.id)
        }
        serverStorage.seal()
        val serverNeg = Negentropy(serverStorage)

        val serverResponse = serverNeg.reconcile(Hex.decode(openCmd.initialMessage))
        assertNotNull(serverResponse.msg)

        val result = clientSession.processMessage(Hex.encode(serverResponse.msg!!))

        // Identical sets: no haves or needs
        assertEquals(0, result.haveIds.size)
        assertEquals(0, result.needIds.size)
    }

    @Test
    fun clientServerSync_clientHasExtra_detectsHaveIds() {
        val sharedEvents =
            listOf(
                makeEvent("a".repeat(64), 1000),
                makeEvent("b".repeat(64), 2000),
            )

        val clientOnlyEvent = makeEvent("c".repeat(64), 3000)
        val clientEvents = sharedEvents + clientOnlyEvent

        val clientSession = NegentropySession("sub1", Filter(), clientEvents)
        val openCmd = clientSession.open()

        // Server side: only shared events
        val serverStorage = StorageVector()
        for (e in sharedEvents) {
            serverStorage.insert(e.createdAt, e.id)
        }
        serverStorage.seal()
        val serverNeg = Negentropy(serverStorage)

        val serverResponse = serverNeg.reconcile(Hex.decode(openCmd.initialMessage))
        assertNotNull(serverResponse.msg)

        val result = clientSession.processMessage(Hex.encode(serverResponse.msg!!))

        // Client has extra event that server needs
        assertTrue(result.haveIds.contains("c".repeat(64)))
        assertEquals(0, result.needIds.size)
    }

    @Test
    fun clientServerSync_serverHasExtra_detectsNeedIds() {
        val sharedEvents =
            listOf(
                makeEvent("a".repeat(64), 1000),
                makeEvent("b".repeat(64), 2000),
            )

        val clientSession = NegentropySession("sub1", Filter(), sharedEvents)
        val openCmd = clientSession.open()

        // Server side: shared + extra
        val serverStorage = StorageVector()
        for (e in sharedEvents) {
            serverStorage.insert(e.createdAt, e.id)
        }
        serverStorage.insert(3000, "d".repeat(64))
        serverStorage.seal()
        val serverNeg = Negentropy(serverStorage)

        val serverResponse = serverNeg.reconcile(Hex.decode(openCmd.initialMessage))
        assertNotNull(serverResponse.msg)

        val result = clientSession.processMessage(Hex.encode(serverResponse.msg!!))

        // Server has an event client needs
        assertTrue(result.needIds.contains("d".repeat(64)))
        assertEquals(0, result.haveIds.size)
    }

    @Test
    fun clientServerSync_bothHaveUnique_detectsBothDirections() {
        val sharedEvents =
            listOf(
                makeEvent("a".repeat(64), 1000),
            )

        val clientOnly = makeEvent("b".repeat(64), 2000)
        val clientEvents = sharedEvents + clientOnly

        val clientSession = NegentropySession("sub1", Filter(), clientEvents)
        val openCmd = clientSession.open()

        // Server side: shared + different extra
        val serverStorage = StorageVector()
        for (e in sharedEvents) {
            serverStorage.insert(e.createdAt, e.id)
        }
        serverStorage.insert(3000, "c".repeat(64))
        serverStorage.seal()
        val serverNeg = Negentropy(serverStorage)

        val serverResponse = serverNeg.reconcile(Hex.decode(openCmd.initialMessage))
        assertNotNull(serverResponse.msg)

        val result = clientSession.processMessage(Hex.encode(serverResponse.msg!!))

        assertTrue(result.haveIds.contains("b".repeat(64)))
        assertTrue(result.needIds.contains("c".repeat(64)))
    }

    @Test
    fun clientServerSync_emptyClient_needsAll() {
        val clientSession = NegentropySession("sub1", Filter(), emptyList())
        val openCmd = clientSession.open()

        // Server has events
        val serverStorage = StorageVector()
        serverStorage.insert(1000, "a".repeat(64))
        serverStorage.insert(2000, "b".repeat(64))
        serverStorage.seal()
        val serverNeg = Negentropy(serverStorage)

        val serverResponse = serverNeg.reconcile(Hex.decode(openCmd.initialMessage))
        assertNotNull(serverResponse.msg)

        val result = clientSession.processMessage(Hex.encode(serverResponse.msg!!))

        assertEquals(0, result.haveIds.size)
        assertEquals(2, result.needIds.size)
    }

    @Test
    fun clientServerSync_emptyServer_hasAll() {
        val clientEvents =
            listOf(
                makeEvent("a".repeat(64), 1000),
                makeEvent("b".repeat(64), 2000),
            )

        val clientSession = NegentropySession("sub1", Filter(), clientEvents)
        val openCmd = clientSession.open()

        // Server is empty
        val serverStorage = StorageVector()
        serverStorage.seal()
        val serverNeg = Negentropy(serverStorage)

        val serverResponse = serverNeg.reconcile(Hex.decode(openCmd.initialMessage))
        assertNotNull(serverResponse.msg)

        val result = clientSession.processMessage(Hex.encode(serverResponse.msg!!))

        assertEquals(2, result.haveIds.size)
        assertEquals(0, result.needIds.size)
    }

    @Test
    fun multiRoundSync_withFrameLimit() {
        // Create enough events to require multiple rounds with a small frame limit
        val clientEvents =
            (1..500).map { i ->
                makeEvent(
                    i.toString().padStart(64, '0'),
                    i.toLong() * 1000,
                )
            }

        val serverEvents =
            (251..750).map { i ->
                Pair(i.toLong() * 1000, i.toString().padStart(64, '0'))
            }

        // Small frame limit to force multiple rounds
        val clientSession = NegentropySession("sub1", Filter(), clientEvents, frameSizeLimit = 4096)
        val openCmd = clientSession.open()

        val serverStorage = StorageVector()
        for ((ts, id) in serverEvents) {
            serverStorage.insert(ts, id)
        }
        serverStorage.seal()
        val serverNeg = Negentropy(serverStorage, 4096)

        var serverMsg = serverNeg.reconcile(Hex.decode(openCmd.initialMessage))
        assertNotNull(serverMsg.msg)

        val allHaveIds = mutableListOf<String>()
        val allNeedIds = mutableListOf<String>()

        var result = clientSession.processMessage(Hex.encode(serverMsg.msg!!))
        allHaveIds.addAll(result.haveIds)
        allNeedIds.addAll(result.needIds)

        var rounds = 1
        while (!result.isComplete()) {
            rounds++
            serverMsg = serverNeg.reconcile(Hex.decode(result.nextCmd!!.message))
            assertNotNull(serverMsg.msg)
            result = clientSession.processMessage(Hex.encode(serverMsg.msg!!))
            allHaveIds.addAll(result.haveIds)
            allNeedIds.addAll(result.needIds)
        }

        // Client has 1-250 exclusively, server has 501-750 exclusively
        assertEquals(250, allHaveIds.size)
        assertEquals(250, allNeedIds.size)
        assertTrue(rounds > 1, "Should require multiple rounds with frame limit")
    }

    @Test
    fun serverSession_processesMessages() {
        val sharedEvents =
            listOf(
                makeEvent("a".repeat(64), 1000),
            )
        val serverOnlyEvent = makeEvent("b".repeat(64), 2000)
        val serverEvents = sharedEvents + serverOnlyEvent

        // Client initiates
        val clientSession = NegentropySession("sub1", Filter(), sharedEvents)
        val openCmd = clientSession.open()

        // Server processes via NegentropyServerSession
        val serverSession = NegentropyServerSession("sub1", serverEvents)
        val response = serverSession.processMessage(openCmd.initialMessage)

        assertNotNull(response)
        assertEquals("NEG-MSG", response.label())
        assertEquals("sub1", response.subId)
        assertTrue(response.message.isNotEmpty())
    }

    @Test
    fun close_returnsCorrectCmd() {
        val session = NegentropySession("sub1", Filter(), emptyList())
        val closeCmd = session.close()

        assertEquals("NEG-CLOSE", closeCmd.label())
        assertEquals("sub1", closeCmd.subId)
        assertTrue(closeCmd.isValid())
    }
}
