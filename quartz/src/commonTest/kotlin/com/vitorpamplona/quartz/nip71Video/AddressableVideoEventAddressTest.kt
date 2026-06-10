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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kinds 34235/34236 (legacy NIP-71 videos) are parameterized replaceable
 * events: their address MUST include the `d` tag. A wrong (empty-dTag)
 * address makes LocalCache consume the event into a different
 * AddressableNote than the one `a` tags point to, so quotes/reposts of
 * these videos never resolve on screen.
 */
class AddressableVideoEventAddressTest {
    // Fixture from a real kind-34236 event published by the Divine client
    // (bfe2f224…, "Lunchtime for our Koi"); the horizontal test reuses the
    // same data synthetically.
    private val pubkey = "3b6187c08b9dd5617150ea047e788a0fdd44b4394cb5566cba76f683ddc027d2"
    private val dTag = "7af7cae314483a84dcc204824cef10aace246a69c819734412330e2a25f459a1"

    private fun assertAddressUsesDTag(
        kind: Int,
        event: AddressableVideoEvent,
    ) {
        assertEquals(dTag, event.dTag())
        assertEquals(Address(kind, pubkey, dTag), event.address())
        // The kind:pubkey:dTag wire format is fixed by NIP-01, so it is
        // asserted literally instead of via Address.assemble (which is
        // what addressTag() calls internally).
        assertEquals("$kind:$pubkey:$dTag", event.addressTag())
    }

    @Test
    fun verticalVideoAddressUsesDTag() {
        val event =
            VideoVerticalEvent(
                id = "bfe2f2244fefc7cebc7b2eae825495f99dabb4649ee3f90ab1fa33bcd1e9bb9f",
                pubKey = pubkey,
                createdAt = 1780894816,
                tags = arrayOf(arrayOf("d", dTag), arrayOf("title", "Lunchtime for our Koi")),
                content = "Lunchtime for our Koi",
                sig = "",
            )

        assertAddressUsesDTag(VideoVerticalEvent.KIND, event)
    }

    @Test
    fun horizontalVideoAddressUsesDTag() {
        val event =
            VideoHorizontalEvent(
                id = "bfe2f2244fefc7cebc7b2eae825495f99dabb4649ee3f90ab1fa33bcd1e9bb9f",
                pubKey = pubkey,
                createdAt = 1780894816,
                tags = arrayOf(arrayOf("d", dTag)),
                content = "",
                sig = "",
            )

        assertAddressUsesDTag(VideoHorizontalEvent.KIND, event)
    }

    @Test
    fun videoWithoutDTagFallsBackToEmptyAddress() {
        val event =
            VideoVerticalEvent(
                id = "bfe2f2244fefc7cebc7b2eae825495f99dabb4649ee3f90ab1fa33bcd1e9bb9f",
                pubKey = pubkey,
                createdAt = 1780894816,
                tags = arrayOf(arrayOf("title", "No d tag")),
                content = "",
                sig = "",
            )

        assertEquals("", event.dTag())
        assertEquals(Address(VideoVerticalEvent.KIND, pubkey, ""), event.address())
        assertEquals("${VideoVerticalEvent.KIND}:$pubkey:", event.addressTag())
    }
}
