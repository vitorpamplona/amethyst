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
package com.vitorpamplona.quartz.podcasts

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PodcastBoostagramTest {
    @Test
    fun `uses satoshis-stream field names and omits unset fields`() {
        val json =
            PodcastBoostagram(
                podcast = "My Show",
                episode = "Ep 1",
                action = PodcastBoostagram.ACTION_BOOST,
                appName = "Amethyst",
                valueMsatTotal = 21_000_000L,
            ).toJson()

        assertTrue(json.contains("\"podcast\":\"My Show\""))
        assertTrue(json.contains("\"app_name\":\"Amethyst\""))
        assertTrue(json.contains("\"value_msat_total\":21000000"))
        assertTrue(json.contains("\"action\":\"boost\""))
        // Unset optionals (message, sender_name) must not appear.
        assertFalse(json.contains("message"))
        assertFalse(json.contains("sender_name"))
    }

    @Test
    fun `round-trips through json`() {
        val original =
            PodcastBoostagram(
                podcast = "Show",
                action = PodcastBoostagram.ACTION_STREAM,
                valueMsatTotal = 1000L,
                senderName = "alice",
            )
        val parsed = JsonMapper.fromJson<PodcastBoostagram>(original.toJson())

        assertEquals("Show", parsed.podcast)
        assertEquals(PodcastBoostagram.ACTION_STREAM, parsed.action)
        assertEquals(1000L, parsed.valueMsatTotal)
        assertEquals("alice", parsed.senderName)
    }
}
