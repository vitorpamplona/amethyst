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
package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.amethyst.ui.isChatroomRoute
import com.vitorpamplona.amethyst.ui.navigation.findParameterValue
import com.vitorpamplona.amethyst.ui.navigation.findQueryParameterValue
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * The deep links a tapped notification hands to `MainActivity.uriToRoute`, checked at the
 * string level — these are what decide whether a tap lands on a real screen or on the
 * "looking for event" redirect.
 */
class NotificationDeepLinkTest {
    private val npub = "npub1" + "q".repeat(58)
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    @Test
    fun chatroomUriCarriesBothParticipantsAndTheAccount() {
        val uri = NotificationRoutes.chatroomUri(ChatroomKey(setOf(alice)), npub)

        assertTrue(isChatroomRoute(uri))
        assertEquals(alice, uri.findQueryParameterValue("id"))
        assertEquals(npub, uri.findQueryParameterValue("account"))
    }

    @Test
    fun chatroomUriKeepsEveryMemberOfAGroupDm() {
        val uri = NotificationRoutes.chatroomUri(ChatroomKey(setOf(alice, bob)), npub)

        assertEquals(setOf(alice, bob), uri.findQueryParameterValue("id")?.split(',')?.toSet())
        assertEquals(npub, uri.findQueryParameterValue("account"))
    }

    /**
     * The regression: `marmot:<hex>?account=…` is an *opaque* URI, so `java.net.URI` keeps the
     * query inside the scheme-specific part and reports no query at all. Reading the account
     * that way returned null on every Marmot notification, and the tap opened the group under
     * whichever account happened to be current instead of switching to the addressee first.
     */
    @Test
    fun opaqueMarmotUriStillYieldsItsAccount() {
        val uri = NotificationRoutes.marmotUri("d".repeat(64), npub)

        assertNull(URI(uri).findParameterValue("account"))
        assertEquals(npub, uri.findQueryParameterValue("account"))
    }

    @Test
    fun hierarchicalNotificationUrisAreReadTheSameWay() {
        val uri = NotificationRoutes.notificationsUri(npub, "c".repeat(64))

        assertEquals(npub, uri.findQueryParameterValue("account"))
        assertEquals("c".repeat(64), uri.findQueryParameterValue("scrollTo"))
    }

    @Test
    fun aUriWithoutAQueryHasNoParameters() {
        assertNull("nevent1qqsabcdef".findQueryParameterValue("account"))
        assertNull("".findQueryParameterValue("account"))
    }

    @Test
    fun anEmptyParameterValueReadsAsAbsent() {
        assertNull("chatroom?id=&account=$npub".findQueryParameterValue("id"))
    }
}
