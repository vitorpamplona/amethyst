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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A canonical url must normalize to itself. [RelayUrlNormalizer.normalizeOrNull] answers straight
 * from the interner when its input already is a canonical url, skipping the parser, which is only
 * right if running the parser on it would have returned it unchanged.
 */
class RelayUrlNormalizerIdempotenceTest {
    private val inputs =
        listOf(
            "wss://nostr.mom",
            "nostr.mom",
            "ws://nostr.mom",
            "https://nostr.mom/",
            "http://nostr.mom/",
            "Wss://relay.nostr.band",
            "WSS://Relay.Damus.IO/",
            "wss://localhost:3030",
            "localhost:3030",
            "ws://127.0.0.1:4869",
            "192.168.1.10:7777",
            "wss://a.onion",
            "a.onion/",
            "wss://abc.onion:8080/path",
            "wss://nostr.mom:443",
            "ws://nostr.mom:80/",
            "wss://nostr.mom%20",
            "wss://nostr.mom:3030",
            "wss://relay.example.com/path/to/relay",
            "wss://relay.example.com/path/",
            "wss://relay.example.com/?query=1",
            "wss://[2001:db8::1]:7777",
            "wss://[200:1234::1]/",
            " wss://relay.example.com ",
            "wss:/relay.example.com",
            "wss//relay.example.com",
            "relay.example.com:7447/ws",
            "nostr.wine/",
            "wss://xn--bcher-kva.example/",
        )

    @Test
    fun aCanonicalUrlNormalizesToItself() {
        inputs.forEach { raw ->
            val canonical = RelayUrlNormalizer.normalizeOrNull(raw) ?: return@forEach
            assertEquals(canonical, RelayUrlNormalizer.normalizeOrNull(canonical.url), "from $raw")
        }
    }

    @Test
    fun theInternerShortcutNeverLetsALenientRepairPassAsAHint() {
        // The lenient parser repairs this into a url the strict hint parser must still refuse,
        // even once the repaired url is interned and the shortcut would find it.
        val repaired = RelayUrlNormalizer.normalizeOrNull("wss//relay.example.com") ?: return

        assertNull(RelayUrlNormalizer.normalizeHintOrNull(repaired.url))
    }
}
