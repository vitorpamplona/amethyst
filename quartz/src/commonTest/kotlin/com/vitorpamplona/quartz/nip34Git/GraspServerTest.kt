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
package com.vitorpamplona.quartz.nip34Git

import com.vitorpamplona.quartz.nip34Git.grasp.GraspServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** GRASP URL derivation, checked against the vectors of ngit's own `repo_ref.rs` tests. */
class GraspServerTest {
    private val npub = "npub15qydau2hjma6ngxkl2cyar74wzyjshvl65za5k5rl69264ar2exs5cyejr"

    @Test
    fun normalizesLikeNgit() {
        // ngit: normalize_grasp_server_url_all_checks
        val cases =
            listOf(
                "https://sub.domain.org" to "sub.domain.org",
                "wss://sub.domain.org" to "sub.domain.org",
                "sub.domain.org" to "sub.domain.org",
                "http://sub.domain.org" to "http://sub.domain.org",
                "ws://sub.domain.org" to "http://sub.domain.org",
                "http://localhost" to "http://localhost",
                "localhost" to "localhost",
                "https://sub.domain.org:8080" to "sub.domain.org:8080",
                "http://sub.domain.org:8080" to "http://sub.domain.org:8080",
                "sub.domain.org:8080" to "sub.domain.org:8080",
                "https://sub.domain.org/path/to" to "sub.domain.org/path/to",
                "https://sub.domain.org:8080/path/to" to "sub.domain.org:8080/path/to",
                "https://sub.domain.org/npub143675782648/to.git" to "sub.domain.org",
                "https://sub.domain.org/path/npub143675782648/to.git" to "sub.domain.org/path",
                "https://sub.domain.org/" to "sub.domain.org",
                "http://sub.domain.org/" to "http://sub.domain.org",
            )
        cases.forEach { (input, expected) -> assertEquals(expected, GraspServer.normalize(input), input) }
    }

    @Test
    fun rejectsInputThatIsNotAHost() {
        assertNull(GraspServer.normalize(""))
        assertNull(GraspServer.normalize("   "))
        assertNull(GraspServer.normalize("https://"))
        assertNull(GraspServer.normalize("my server"))
        assertNull(GraspServer.normalize("hello"))
        assertNull(GraspServer.normalize("-bad.example.com"))
        assertNull(GraspServer.normalize("relay.example.com:99999"))
        assertNull(GraspServer.normalize("relay.example.com:port"))
    }

    @Test
    fun acceptsIpAddressesAndPorts() {
        assertEquals("http://127.0.0.1:7777", GraspServer.normalize("ws://127.0.0.1:7777"))
        assertEquals("relay.example.com:8443", GraspServer.normalize("relay.example.com:8443"))
    }

    @Test
    fun onlyAnNpubPathSegmentIsCut() {
        // ngit cuts at the first "npub1" anywhere, which would mangle these hosts.
        assertEquals("npub1.example.com", GraspServer.normalize("wss://npub1.example.com"))
        assertEquals("mynpub1host.dev", GraspServer.normalize("https://mynpub1host.dev/"))
        assertEquals("mynpub1host.dev", GraspServer.normalize("https://mynpub1host.dev/$npub/r.git"))
    }

    @Test
    fun relayUrlFollowsTheClonesScheme() {
        // ngit: derives_relay_scheme_from_clone_url_scheme
        assertEquals("wss://relay.ngit.dev", GraspServer.relayUrl("https://relay.ngit.dev/$npub/my-repo.git"))
        assertEquals("ws://localhost:8080", GraspServer.relayUrl("http://localhost:8080/$npub/my-repo.git"))
        assertEquals("wss://relay.ngit.dev", GraspServer.relayUrl("relay.ngit.dev"))
        assertEquals("ws://abc.onion", GraspServer.relayUrl("abc.onion"))
    }

    @Test
    fun cloneUrlIsServerNpubAndEncodedIdentifier() {
        assertEquals("https://relay.ngit.dev/$npub/example-repo.git", GraspServer.cloneUrl("relay.ngit.dev", npub, "example-repo"))
        assertEquals("https://relay.ngit.dev/$npub/example-repo.git", GraspServer.cloneUrl("wss://relay.ngit.dev/", npub, "example-repo"))
        assertEquals("http://localhost:8080/$npub/r.git", GraspServer.cloneUrl("ws://localhost:8080", npub, "r"))
        assertEquals("http://abc.onion/$npub/r.git", GraspServer.cloneUrl("abc.onion", npub, "r"))
        // The NIP-34 example identifier `my 🚀 repo`, percent-encoded as ngit's urlencoding::encode does.
        assertEquals("https://gitnostr.com/$npub/my%20%F0%9F%9A%80%20repo.git", GraspServer.cloneUrl("gitnostr.com", npub, "my 🚀 repo"))
    }
}
