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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.stores.FilePublishObligationStore
import com.vitorpamplona.quartz.marmot.protocolCore.LocalOutboundGate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Outbound gates have to reach the disk, not just the map.
 *
 * `LocalOutboundGate.DISBANDING` is required to survive "publication failure,
 * restart, and a losing branch". The gate API is defaulted on the store
 * interface so existing implementations keep compiling — which means a store
 * that does not override it drops every gate silently, and the group comes back
 * after a restart offering itself as ordinarily live with an admin's
 * irreversible request forgotten. Every `amy` verb is its own process, so
 * "survives a restart" is the ordinary case here rather than a crash scenario.
 */
class FileStoresGateTest {
    @get:Rule val tmp = TemporaryFolder()

    private val groupA = "a".repeat(64)
    private val groupB = "b".repeat(64)

    @Test
    fun `a raised gate is readable by the next process`() =
        runBlocking {
            val dir = tmp.newFolder("obligations")
            FilePublishObligationStore(dir).saveGate(groupA, LocalOutboundGate.DISBANDING.name)

            // A different instance over the same directory is what the next
            // `amy` invocation actually does.
            val reopened = FilePublishObligationStore(dir).loadGates()
            assertEquals(mapOf(groupA to LocalOutboundGate.DISBANDING.name), reopened)
        }

    @Test
    fun `clearing a gate removes it for good`() =
        runBlocking {
            val dir = tmp.newFolder("obligations")
            val store = FilePublishObligationStore(dir)
            store.saveGate(groupA, LocalOutboundGate.DISBANDING.name)
            store.deleteGate(groupA)

            assertTrue(FilePublishObligationStore(dir).loadGates().isEmpty())
        }

    @Test
    fun `gates are per group and do not disturb obligations`() =
        runBlocking {
            // One file per group and per obligation, in one directory: a gate
            // write must not be mistaken for an obligation on reload, or the
            // publish gate would try to decode an enum name as a TLS record.
            val dir = tmp.newFolder("obligations")
            val store = FilePublishObligationStore(dir)
            store.save("f".repeat(64), byteArrayOf(1, 2, 3))
            store.saveGate(groupA, LocalOutboundGate.DISBANDING.name)
            store.saveGate(groupB, LocalOutboundGate.LEAVING.name)

            val reopened = FilePublishObligationStore(dir)
            assertEquals(
                mapOf(groupA to LocalOutboundGate.DISBANDING.name, groupB to LocalOutboundGate.LEAVING.name),
                reopened.loadGates(),
            )
            assertEquals(1, reopened.loadAll().size)
        }
}
