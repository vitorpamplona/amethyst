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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.commands.ListUpdate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `listed` / `list_note` fields `relaygroup create|join|leave` print about the kind-10009 list. */
class RelayGroupListUpdateTest {
    @Test
    fun aListedGroupPrintsOnlyListed() {
        assertEquals(mapOf("listed" to true), ListUpdate(true).fields())
    }

    @Test
    fun anUnlistedGroupSaysWhy() {
        // A Buzz relay as the only outbox refuses 10009 from members.
        val why = "no outbox relay accepted the kind-10009 group list (ws://127.0.0.1:7500/: restricted: …)"
        assertEquals(mapOf("listed" to false, "list_note" to why), ListUpdate(false, why).fields())
    }
}
