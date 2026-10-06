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
package com.vitorpamplona.quartz.experimental.trustedLists

import com.vitorpamplona.quartz.experimental.trustedLists.addressables.AddressableTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.events.EventTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.ExternalIdTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustedListHintProviderTest {
    private val pk1 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val pk2 = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eid = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eid2 = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    private val relay = "wss://relay.damus.io/"
    private val zero = "00".repeat(32)
    private val sig = "00".repeat(64)

    private val observer = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val provenance = arrayOf(arrayOf("observer", observer), arrayOf("source-tag", eid2, pk2, "slug"))

    @Test
    fun everyKindLinksItsObserverAndSource() {
        val user = UserTrustedListEvent(zero, pk1, 1, arrayOf(arrayOf("d", "l"), arrayOf("p", pk1)) + provenance, "", sig)
        assertEquals(listOf(pk1, observer, pk2), user.linkedPubKeys())
        assertEquals(listOf(eid2), user.linkedEventIds())
        assertTrue(user.eventHints().isEmpty())

        val events = EventTrustedListEvent(zero, pk1, 1, arrayOf(arrayOf("d", "l"), arrayOf("e", eid)) + provenance, "", sig)
        assertEquals(listOf(eid, eid2), events.linkedEventIds())
        assertEquals(listOf(observer, pk2), events.linkedPubKeys())

        val addresses = AddressableTrustedListEvent(zero, pk1, 1, arrayOf(arrayOf("d", "l"), arrayOf("a", "30023:$pk1:x")) + provenance, "", sig)
        assertEquals(listOf(observer, pk2), addresses.linkedPubKeys())
        assertEquals(listOf(eid2), addresses.linkedEventIds())

        val external = ExternalIdTrustedListEvent(zero, pk1, 1, arrayOf(arrayOf("d", "l"), arrayOf("i", "isbn:123")) + provenance, "", sig)
        assertEquals(listOf(observer, pk2), external.linkedPubKeys())
        assertEquals(listOf(eid2), external.linkedEventIds())
    }

    @Test
    fun aSourceTagWithoutAValidAuthorLinksOnlyItsEvent() {
        val user = UserTrustedListEvent(zero, pk1, 1, arrayOf(arrayOf("d", "l"), arrayOf("source-tag", eid2, "", "slug")), "", sig)
        assertTrue(user.linkedPubKeys().isEmpty())
        assertEquals(listOf(eid2), user.linkedEventIds())
    }
}
