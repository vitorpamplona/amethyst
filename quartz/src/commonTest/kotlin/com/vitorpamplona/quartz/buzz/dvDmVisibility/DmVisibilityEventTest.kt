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
package com.vitorpamplona.quartz.buzz.dvDmVisibility

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DmVisibilityEventTest {
    // The relay signs these snapshots; here any keypair stands in for the relay key.
    private val relay = NostrSignerInternal(KeyPair())
    private val viewer = KeyPair().pubKey.toHexKey()

    // Sign generically and wrap, so the test does not depend on EventFactory registration.
    private suspend fun sealSnapshot(hidden: List<String>): DmVisibilityEvent {
        val t = DmVisibilityEvent.build(viewer, hidden)
        val signed: Event = relay.sign(t.createdAt, t.kind, t.tags, t.content)
        return DmVisibilityEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig)
    }

    @Test
    fun buildsSnapshotWithViewerAndHiddenChannels() =
        runTest {
            val hidden = listOf("channel-a", "channel-b")
            val event = sealSnapshot(hidden)

            assertEquals(30622, event.kind)
            assertEquals("", event.content)
            assertEquals(viewer, event.viewer())
            assertEquals(viewer, event.viewerFromPTag())
            assertEquals(hidden, event.hiddenChannels())
        }

    @Test
    fun emptyHiddenSetHasNoHTags() =
        runTest {
            val event = sealSnapshot(emptyList())

            assertEquals(viewer, event.viewer())
            assertTrue(event.hiddenChannels().isEmpty(), "no h tags when nothing is hidden")
            assertTrue(event.tags.none { it[0] == "h" })
        }
}
