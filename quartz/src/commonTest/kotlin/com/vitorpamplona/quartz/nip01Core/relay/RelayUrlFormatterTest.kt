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

class RelayUrlFormatterTest {
    @Test
    fun format() {
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("wss://nostr.mom")?.url)
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("nostr.mom")?.url)
        assertEquals("ws://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("ws://nostr.mom")?.url)
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("wss://nostr.mom/")?.url)
        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("https://nostr.mom/")?.url)
        assertEquals("ws://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("http://nostr.mom/")?.url)

        assertEquals("wss://localhost:3030/", RelayUrlNormalizer.normalizeOrNull("wss://localhost:3030")?.url)
        assertEquals("ws://localhost:3030/", RelayUrlNormalizer.normalizeOrNull("localhost:3030")?.url)

        assertEquals("wss://a.onion/", RelayUrlNormalizer.normalizeOrNull("wss://a.onion")?.url)
        assertEquals("ws://a.onion/", RelayUrlNormalizer.normalizeOrNull("a.onion")?.url)
        assertEquals("wss://a.onion/", RelayUrlNormalizer.normalizeOrNull("wss://a.onion/")?.url)
        assertEquals("ws://a.onion/", RelayUrlNormalizer.normalizeOrNull("a.onion/")?.url)

        assertEquals("wss://nostr.mom/", RelayUrlNormalizer.normalizeOrNull("wss://nostr.mom")?.url)

        assertEquals("wss://relay.nostr.band/", RelayUrlNormalizer.normalizeOrNull("Wss://relay.nostr.band")?.url)
    }

    @Test
    fun weirdRelay() {
        assertNull(RelayUrlNormalizer.normalizeOrNull("wss://relay%20list%20to%20discover%20the%20user's%20content"))
    }
}
