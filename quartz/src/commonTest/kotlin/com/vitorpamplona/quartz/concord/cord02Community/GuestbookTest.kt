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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuestbookTest {
    private val member = KeyPair().pubKey.toHexKey()
    private val creator = KeyPair().pubKey.toHexKey()
    private val target = KeyPair().pubKey.toHexKey()

    @Test
    fun joinWithInviteAttributionRoundTrips() {
        val rumor = Guestbook.join(member, createdAt = 1_700_000_000L, subMs = 128, inviteCreator = creator, inviteLabel = "Reddit")
        assertEquals(ConcordKinds.JOIN_LEAVE, rumor.kind)
        assertEquals("join", rumor.content)

        val entry = Guestbook.parse(rumor)
        assertEquals(GuestbookAction.JOIN, entry?.action)
        assertEquals(member, entry?.member)
        assertEquals(creator, entry?.inviteCreator)
        assertEquals("Reddit", entry?.inviteLabel)
    }

    @Test
    fun leaveParses() {
        val entry = Guestbook.parse(Guestbook.leave(member, createdAt = 1L))
        assertEquals(GuestbookAction.LEAVE, entry?.action)
        assertNull(entry?.inviteCreator)
    }

    @Test
    fun kickTargetsAMember() {
        val rumor = Guestbook.kick(actorPubKey = creator, target = target, createdAt = 1L)
        assertEquals(ConcordKinds.KICK, rumor.kind)
        assertEquals(target, Guestbook.kickTarget(rumor))
    }

    @Test
    fun parseIgnoresNonGuestbookRumors() {
        assertNull(Guestbook.parse(Guestbook.kick(creator, target, 1L))) // kick is not a join/leave
    }
}
