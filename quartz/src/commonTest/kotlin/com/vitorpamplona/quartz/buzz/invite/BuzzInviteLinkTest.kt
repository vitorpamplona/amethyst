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
package com.vitorpamplona.quartz.buzz.invite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuzzInviteLinkTest {
    // A real invite token minted by amethyst.communities.buzz.xyz (payload only is asserted;
    // the signature part is opaque to clients — the relay verifies its own MAC on claim).
    private val realToken =
        "eyJjIjoiYzAzYWJhYTktNjVlNC00M2IxLWI5YjMtZjUwMmEyODEyZDBiIiwiciI6Im1lbWJlciIsImUiOjE3ODQ5ODk2NDksIm4iOiJFMVRMRFgxUHhWY1lFcTBIRVdpM1Z3In0" +
            ".e-wUTcfoF6dYBmmeSMnKIVQ8M3zvml2dEj96tOMbVjY"
    private val realUrl = "https://amethyst.communities.buzz.xyz/invite/$realToken"

    @Test
    fun parsesARealBuzzInvite() {
        val invite = BuzzInviteLink.parse(realUrl)!!
        assertEquals("amethyst.communities.buzz.xyz", invite.host)
        assertEquals(realToken, invite.code)
        assertEquals("c03abaa9-65e4-43b1-b9b3-f502a2812d0b", invite.communityId)
        assertEquals("member", invite.role)
        assertEquals(1784989649L, invite.expiresAt)
        assertEquals("wss://amethyst.communities.buzz.xyz", invite.relayUrl())
        assertEquals("https://amethyst.communities.buzz.xyz", invite.httpBase())
    }

    @Test
    fun honorsTheExpiry() {
        val invite = BuzzInviteLink.parse(realUrl)!!
        assertTrue(invite.isExpired(1784989649L))
        assertTrue(invite.isExpired(1784989650L))
        assertTrue(!invite.isExpired(1784989648L))
    }

    @Test
    fun toleratesTrailingFragmentAndQuery() {
        assertEquals("c03abaa9-65e4-43b1-b9b3-f502a2812d0b", BuzzInviteLink.parse("$realUrl#x")!!.communityId)
        assertEquals("c03abaa9-65e4-43b1-b9b3-f502a2812d0b", BuzzInviteLink.parse("$realUrl?ref=1")!!.communityId)
    }

    @Test
    fun rejectsNonInviteAndConcordShapes() {
        assertNull(BuzzInviteLink.parse("https://amethyst.communities.buzz.xyz/"))
        assertNull(BuzzInviteLink.parse("https://example.com/invite/"))
        // A Concord invite is /invite/<naddr>#<fragment> — no dot-separated base64 payload.
        assertNull(BuzzInviteLink.parse("https://amethyst.social/invite/naddr1abcdef#deadbeef"))
        // Dotless token → not a Buzz invite.
        assertNull(BuzzInviteLink.parse("https://host.example/invite/justsometext"))
    }
}
