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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuzzWorkspacesTest {
    private val a = RelayUrlNormalizer.normalize("wss://a.buzz.example")
    private val b = RelayUrlNormalizer.normalize("wss://b.buzz.example")

    @BeforeTest fun setup() {
        BuzzWorkspaces.clearForTesting()
        BuzzRelayDialect.clearForTesting()
    }

    @AfterTest fun teardown() {
        BuzzWorkspaces.clearForTesting()
        BuzzRelayDialect.clearForTesting()
    }

    @Test
    fun joiningRecordsAndMarksDialect() {
        assertTrue(BuzzWorkspaces.join(a))
        assertTrue(BuzzWorkspaces.isJoined(a))
        assertEquals(setOf(a), BuzzWorkspaces.flow.value)
        // Joining also marks the relay a Buzz dialect so its events render as workspace channels.
        assertTrue(BuzzRelayDialect.isBuzz(a))
        // Re-joining is a no-op (returns false).
        assertFalse(BuzzWorkspaces.join(a))
    }

    @Test
    fun leaveRemoves() {
        BuzzWorkspaces.join(a)
        BuzzWorkspaces.join(b)
        BuzzWorkspaces.leave(a)
        assertEquals(setOf(b), BuzzWorkspaces.flow.value)
        assertFalse(BuzzWorkspaces.isJoined(a))
    }

    @Test
    fun restoreReplacesAndMarksAll() {
        BuzzWorkspaces.join(a)
        BuzzWorkspaces.restore(setOf(b))
        assertEquals(setOf(b), BuzzWorkspaces.flow.value)
        assertTrue(BuzzRelayDialect.isBuzz(b))
    }
}
