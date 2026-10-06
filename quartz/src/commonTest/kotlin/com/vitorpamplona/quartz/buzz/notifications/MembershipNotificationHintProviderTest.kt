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
package com.vitorpamplona.quartz.buzz.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MembershipNotificationHintProviderTest {
    private val relaySigner = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val member = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val actor = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val channel = "6f1a8a3c-9f1e-4d2b-8c1a-2b3c4d5e6f70"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun targetAndActorAreBothLinked() {
        val body = """{"type":"member_added","channel_id":"$channel","actor":"$actor"}"""
        val added = MemberAddedNotificationEvent(id, relaySigner, 1, arrayOf(arrayOf("p", member, relay), arrayOf("h", channel)), body, sig)
        assertEquals(listOf(member, actor), added.linkedPubKeys())
        assertEquals(listOf(member), added.pubKeyHints().map { it.pubkey })

        val removed = MemberRemovedNotificationEvent(id, relaySigner, 1, arrayOf(arrayOf("p", member), arrayOf("h", channel)), "", sig)
        assertEquals(listOf(member), removed.linkedPubKeys())
    }

    @Test
    fun theBodyIsParsedOncePerInstance() {
        val body = """{"type":"member_removed","channel_id":"$channel","actor":"$actor"}"""
        val removed = MemberRemovedNotificationEvent(id, relaySigner, 1, arrayOf(arrayOf("p", member), arrayOf("h", channel)), body, sig)
        assertSame(removed.notification(), removed.notification())
        assertEquals(listOf(member, actor), removed.linkedPubKeys())
        assertEquals(listOf(member, actor), removed.linkedPubKeys())

        // An absent body is remembered too (as "no body"), and degrades to "no actor".
        val empty = MemberAddedNotificationEvent(id, relaySigner, 1, arrayOf(arrayOf("p", member)), "", sig)
        assertNull(empty.notification())
        assertNull(empty.notification())
        assertEquals(listOf(member), empty.linkedPubKeys())

        val malformed = MemberAddedNotificationEvent(id, relaySigner, 1, arrayOf(arrayOf("p", member)), "not json", sig)
        assertNull(malformed.actor())
        assertSame(malformed.notification(), malformed.notification())
        assertEquals(listOf(member), malformed.linkedPubKeys())
    }
}
