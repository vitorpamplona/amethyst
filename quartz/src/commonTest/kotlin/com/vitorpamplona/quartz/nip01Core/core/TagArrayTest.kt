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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.ClientTag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagArrayTest {
    private val tags: TagArray =
        arrayOf(
            arrayOf("d", "30022"),
            arrayOf("a", "31990:460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c:1685802317447", "wss://nostr.wine/", "android"),
            arrayOf("alt", "App recommendations by the author"),
            arrayOf("client", "Amethyst"),
        )

    @Test
    fun matchesTagValues() {
        assertTrue(tags.tagValueContains("recommendations", ignoreCase = true))
        assertTrue(tags.tagValueContains("amethyst", ignoreCase = true))
        assertFalse(tags.tagValueContains("missing", ignoreCase = true))
    }

    @Test
    fun skipsExcludedTagNames() {
        assertFalse(tags.tagValueContains("amethyst", ignoreCase = true, exceptNames = setOf(ClientTag.TAG_NAME)))
        // other tags still match when the client tag is excluded
        assertTrue(tags.tagValueContains("recommendations", ignoreCase = true, exceptNames = setOf(ClientTag.TAG_NAME)))
    }

    @Test
    fun skipsAllSearchExcludedTagNames() {
        val excluded = setOf(ClientTag.TAG_NAME, PTag.TAG_NAME, ETag.TAG_NAME, ATag.TAG_NAME, AltTag.TAG_NAME)

        assertFalse(tags.tagValueContains("amethyst", ignoreCase = true, exceptNames = excluded))
        assertFalse(tags.tagValueContains("31990:460c25e6", ignoreCase = true, exceptNames = excluded))
        assertFalse(tags.tagValueContains("recommendations", ignoreCase = true, exceptNames = excluded))
        // non-excluded tags still match
        assertTrue(tags.tagValueContains("30022", ignoreCase = true, exceptNames = excluded))
    }
}
