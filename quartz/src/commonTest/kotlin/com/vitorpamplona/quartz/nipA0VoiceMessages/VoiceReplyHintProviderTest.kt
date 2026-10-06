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
package com.vitorpamplona.quartz.nipA0VoiceMessages

import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceReplyHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val rootAuthor = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val parentAuthor = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val rootId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val parentId = "c3d3a1a5d6d4f7c9f0e3b1a2c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f7"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!
    private val relay2 = RelayUrlNormalizer.normalizeOrNull("wss://nos.lol/")!!

    private fun reply(vararg tags: Array<String>) = VoiceReplyEvent("00".repeat(32), author, 1700000000, arrayOf(*tags), "https://example.com/a.m4a", "00".repeat(64))

    @Test
    fun rootAndParentScopesAreReferences() {
        val event =
            reply(
                arrayOf("E", rootId, relay.url, rootAuthor),
                arrayOf("K", "1222"),
                arrayOf("P", rootAuthor, relay.url),
                arrayOf("e", parentId, relay2.url, parentAuthor),
                arrayOf("k", "1244"),
                arrayOf("p", parentAuthor),
            )

        assertEquals(listOf(rootAuthor, parentAuthor), event.linkedPubKeys())
        assertEquals(listOf(PubKeyHint(rootAuthor, relay)), event.pubKeyHints())

        assertEquals(listOf(rootId, parentId), event.linkedEventIds())
        assertEquals(listOf(EventIdHint(rootId, relay), EventIdHint(parentId, relay2)), event.eventHints())

        assertEquals(emptyList(), event.linkedAddressIds())
    }

    @Test
    fun aThreadRootedAtAnAddressLinksIt() {
        val address = "30023:$rootAuthor:article"
        val event =
            reply(
                arrayOf("A", address, relay.url),
                arrayOf("K", "30023"),
                arrayOf("P", rootAuthor),
                arrayOf("e", parentId, relay2.url, parentAuthor),
                arrayOf("k", "1244"),
                arrayOf("p", parentAuthor, relay2.url),
            )

        assertEquals(listOf(address), event.linkedAddressIds())
        assertEquals(listOf(rootAuthor, parentAuthor), event.linkedPubKeys())
        assertEquals(listOf(PubKeyHint(parentAuthor, relay2)), event.pubKeyHints())
        assertEquals(listOf(parentId), event.linkedEventIds())
    }
}
