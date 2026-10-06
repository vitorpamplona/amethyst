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
package com.vitorpamplona.quartz.nip22Comments

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NIP-22 scope tags put the relay in slot 2 and the author in slot 3. Writers that drop an
 * empty relay shift the author into the relay slot; that pubkey must stay a reference, never
 * become a `wss://<pubkey>/` hint.
 */
class CommentRelaySlotHintTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val rootAuthor = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val parentAuthor = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val rootId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val parentId = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val address = "30023:$rootAuthor:article"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!

    private fun comment(vararg tags: Array<String>) = CommentEvent("00".repeat(32), pk, 1L, arrayOf(*tags), "nice", "00".repeat(64))

    @Test
    fun anAuthorInTheRelaySlotIsLinkedButNotHinted() {
        val event =
            comment(
                arrayOf("E", rootId, rootAuthor),
                arrayOf("K", "1111"),
                arrayOf("P", rootAuthor, "github"),
                arrayOf("e", parentId, parentAuthor),
                arrayOf("k", "1111"),
                arrayOf("p", parentAuthor, "inspired-by"),
            )

        assertEquals(listOf(rootId, parentId), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
        assertEquals(listOf(rootAuthor, parentAuthor), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
    }

    @Test
    fun anAddressRootWithAPubkeyOrLabelInTheRelaySlotIsLinkedButNotHinted() {
        val event =
            comment(
                arrayOf("A", address, rootAuthor),
                arrayOf("K", "30023"),
                arrayOf("a", address, "root"),
                arrayOf("k", "30023"),
            )

        assertEquals(listOf(address, address), event.linkedAddressIds())
        assertTrue(event.addressHints().isEmpty())
    }

    @Test
    fun realRelaysAreStillHinted() {
        val event =
            comment(
                arrayOf("E", rootId, relay.url, rootAuthor),
                arrayOf("e", parentId, relay.url, parentAuthor),
                arrayOf("A", address, relay.url),
            )

        assertEquals(listOf(rootId to relay, parentId to relay), event.eventHints().map { it.eventId to it.relay })
        assertEquals(listOf(address to relay), event.addressHints().map { it.addressId to it.relay })
    }
}
