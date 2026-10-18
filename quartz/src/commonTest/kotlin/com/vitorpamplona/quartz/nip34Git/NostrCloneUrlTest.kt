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

import com.vitorpamplona.quartz.nip34Git.repository.NostrCloneUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `nostr://` clone URL forms of NIP-34, checked against the spec's own examples. */
class NostrCloneUrlTest {
    private val npub = "npub15qydau2hjma6ngxkl2cyar74wzyjshvl65za5k5rl69264ar2exs5cyejr"

    @Test
    fun relayHintDropsTheWssScheme() {
        assertEquals("nostr://$npub/relay.ngit.dev/ngit", NostrCloneUrl.build(npub, "ngit", "wss://relay.ngit.dev"))
        assertEquals("nostr://$npub/relay.ngit.dev/ngit", NostrCloneUrl.build(npub, "ngit", "wss://relay.ngit.dev/"))
    }

    @Test
    fun withoutAHintOnlyTheIdentifierIsEncoded() {
        assertEquals("nostr://$npub/my%20%F0%9F%9A%80%20repo", NostrCloneUrl.build(npub, "my \uD83D\uDE80 repo"))
    }

    @Test
    fun aPlainWebSocketHintKeepsItsSchemeEncoded() {
        assertEquals(
            "nostr://$npub/ws%3A%2F%2Flocalhost%3A7334/my-local-only-repo",
            NostrCloneUrl.build(npub, "my-local-only-repo", "ws://localhost:7334"),
        )
    }
}
