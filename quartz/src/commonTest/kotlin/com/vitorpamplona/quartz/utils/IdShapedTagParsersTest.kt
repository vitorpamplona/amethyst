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
package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.buzz.amTurnMetrics.tags.AgentTag
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ConsentTag
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReplacedByTag
import com.vitorpamplona.quartz.buzz.moderation.tags.ReportTag
import com.vitorpamplona.quartz.nip64Chess.end.tags.WinnerTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.EditTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tags whose value IS a pubkey or event id must not hand back anything else as one. */
class IdShapedTagParsersTest {
    private val key = "a".repeat(64)

    @Test
    fun onlyHexIdsParse() {
        for ((name, parse) in listOf<Pair<String, (Array<String>) -> String?>>(
            ReplacedByTag.TAG_NAME to ReplacedByTag::parse,
            AgentTag.TAG_NAME to AgentTag::parse,
            ReportTag.TAG_NAME to ReportTag::parse,
            WinnerTag.TAG_NAME to WinnerTag::parse,
            EditTag.TAG_NAME to EditTag::parse,
        )) {
            assertEquals(key, parse(arrayOf(name, key)), name)
            assertNull(parse(arrayOf(name, "not-a-key")), name)
            assertNull(parse(arrayOf(name, "g".repeat(64))), name)
        }
        assertNull(ConsentTag.parse(arrayOf(ConsentTag.TAG_NAME, "path", "not-a-key")))
        assertEquals(key, ConsentTag.parse(arrayOf(ConsentTag.TAG_NAME, "path", key))?.actorPubKey)
    }
}
