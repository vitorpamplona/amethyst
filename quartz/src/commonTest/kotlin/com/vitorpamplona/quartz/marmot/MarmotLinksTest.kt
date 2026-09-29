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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushGossip
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenListEvent
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class MarmotLinksTest {
    private val me = "0".repeat(64)
    private val keyPackage = "e1".repeat(32)
    private val group = "ab".repeat(32)

    @Test
    fun welcomeLinksTheConsumedKeyPackageAndTheGroup() {
        val tags =
            arrayOf(
                arrayOf("e", keyPackage),
                arrayOf("relays", "wss://relay.example/"),
                arrayOf("h", group),
            )
        assertEquals(
            listOf(
                Link(Relation.KEY_PACKAGE, LinkTarget.Event(keyPackage), "e"),
                Link(Relation.GROUP, LinkTarget.Tag("h", group), "h"),
            ),
            WelcomeEvent(me, me, 0, tags, "", "").links(),
        )
    }

    @Test
    fun groupMessageLinksOnlyItsGroup() {
        assertEquals(
            listOf(Link(Relation.GROUP, LinkTarget.Tag("h", group), "h")),
            GroupEvent(me, me, 0, arrayOf(arrayOf("h", group)), "c2VjcmV0", me).links(),
        )
    }

    @Test
    fun keyPackageLinksItsRef() {
        val tags = arrayOf(arrayOf("d", "slot"), arrayOf("i", "0a1b"), arrayOf("mls_ciphersuite", "0x0001"))
        assertEquals(
            listOf(Link(Relation.TAG, LinkTarget.Tag("i", "0a1b"), "i")),
            KeyPackageEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun pushTokenPayloadsAreNotParsed() {
        // The member and server keys are inside the JSON content: out of scope for links().
        val content = PushGossip.encodeTokens(emptyList())
        assertEquals(emptyList<Link>(), TokenListEvent(me, me, 0, arrayOf(arrayOf("v", "1")), content, "").links())
    }
}
