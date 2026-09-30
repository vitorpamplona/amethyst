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
import kotlin.test.assertFalse
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

    // A real v2 token minted by amethyst.communities.buzz.xyz: `v2.` plus an opaque handle. Nothing
    // about the invite is encoded in it — the relay resolves the code when the claim arrives.
    private val v2Token = "v2.WWsMv33mYH8o04ZGdcoZKmIImGOEMW7auc5cZ0UdH24"
    private val v2Url = "https://amethyst.communities.buzz.xyz/invite/$v2Token"

    @Test
    fun parsesAnOpaqueV2Invite() {
        val invite = BuzzInviteLink.parse(v2Url)!!
        assertEquals("amethyst.communities.buzz.xyz", invite.host)
        assertEquals(v2Token, invite.code)
        // Unknowable client-side: the claim response carries the community and the granted role.
        assertEquals("", invite.communityId)
        assertEquals("member", invite.role)
        assertNull(invite.expiresAt)
        // What the join actually needs, both derived from the host.
        assertEquals("wss://amethyst.communities.buzz.xyz", invite.relayUrl())
        assertEquals("https://amethyst.communities.buzz.xyz", invite.httpBase())
    }

    @Test
    fun aV2InviteNeverExpiresClientSide() {
        // No expiry to check, so the courtesy check must not block the claim — the relay decides.
        assertTrue(!BuzzInviteLink.parse(v2Url)!!.isExpired(Long.MAX_VALUE))
    }

    @Test
    fun toleratesTrailingFragmentAndQueryOnV2() {
        assertEquals(v2Token, BuzzInviteLink.parse("$v2Url#x")!!.code)
        assertEquals(v2Token, BuzzInviteLink.parse("$v2Url?ref=1")!!.code)
    }

    @Test
    fun rejectsNonInviteAndConcordShapes() {
        assertNull(BuzzInviteLink.parse("https://amethyst.communities.buzz.xyz/"))
        assertNull(BuzzInviteLink.parse("https://example.com/invite/"))
        // A Concord invite is /invite/<naddr>#<fragment> — no dot-separated base64 payload.
        assertNull(BuzzInviteLink.parse("https://amethyst.social/invite/naddr1abcdef#deadbeef"))
        // Dotless token → not a Buzz invite.
        assertNull(BuzzInviteLink.parse("https://host.example/invite/justsometext"))
        // The v2 exemption is the literal prefix, not "give up on decoding": an undecodable
        // payload with any other prefix is still not an invite.
        assertNull(BuzzInviteLink.parse("https://host.example/invite/v3.WWsMv33mYH8o04ZGdcoZKmI"))
        assertNull(BuzzInviteLink.parse("https://host.example/invite/notbase64json.sig"))
        // `v2` without the dot is a dotless token like any other.
        assertNull(BuzzInviteLink.parse("https://host.example/invite/v2"))
    }

    /** Exactly what `validate_v2_code` in `buzz-core/src/invite.rs` accepts — the relay never falls back to v1 for `v2.`. */
    @Test
    fun v2CodesMustBeCanonicalBase64UrlOfThirtyTwoBytes() {
        assertTrue(BuzzInviteLink.isValidV2Code(v2Token))

        val secret = v2Token.removePrefix("v2.")
        listOf(
            // Upstream's own malformed cases.
            "v2.",
            "v2.not-base64!",
            "v2.${secret.dropLast(2)}", // 31 bytes' worth
            "$v2Token=", // padded alias
            // Same 32 bytes, but the unused low bits of the last symbol are set: a non-canonical alias.
            "v2.${secret.dropLast(1)}5",
            // Standard (not url-safe) alphabet, and extra segments.
            "v2.${secret.replace('_', '/')}+",
            "$v2Token.sig",
            // 33 bytes.
            "v2.${secret}AA",
        ).forEach { code ->
            assertFalse(BuzzInviteLink.isValidV2Code(code), "accepted malformed v2 code: $code")
            assertNull(BuzzInviteLink.parse("https://host.example/invite/$code"), "parsed malformed v2 invite: $code")
        }
    }

    @Test
    fun claimResponseSlugsAreRead() {
        assertEquals(BuzzInviteClaim.ERROR_EXHAUSTED, BuzzInviteClaim.errorOf("""{"error":"invite_exhausted"}"""))
        assertEquals(BuzzInviteClaim.ERROR_EXPIRED, BuzzInviteClaim.errorOf("""{"error":"invite_expired"}"""))
        assertEquals(
            BuzzInviteClaim.STATUS_ALREADY_MEMBER,
            BuzzInviteClaim.statusOf("""{"status":"already_member","community_id":"c","host":"h","role":"member"}"""),
        )
        assertNull(BuzzInviteClaim.errorOf("<html>bad gateway</html>"))
        assertNull(BuzzInviteClaim.statusOf("""{"error":"invite_invalid"}"""))
    }
}
