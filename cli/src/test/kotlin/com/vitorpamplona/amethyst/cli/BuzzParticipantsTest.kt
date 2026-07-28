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

import com.vitorpamplona.amethyst.cli.commands.BuzzCommands
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `buzz dm list` reads `participants` off the raw `dm_created` content. It used to do that by
 * stringifying the element and splitting on commas, which turned every non-string shape into
 * plausible-looking-but-fake pubkeys. These cases pin the decoded behaviour.
 */
class BuzzParticipantsTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    @Test
    fun readsAFlatArrayOfStrings() {
        assertEquals(listOf(a, b), BuzzCommands.participantsOf("""{"participants":["$a","$b"]}"""))
    }

    @Test
    fun emptyWhenAbsentOrEmptyOrUnparseable() {
        assertEquals(emptyList(), BuzzCommands.participantsOf("""{"type":"dm_created"}"""))
        assertEquals(emptyList(), BuzzCommands.participantsOf("""{"participants":[]}"""))
        assertEquals(emptyList(), BuzzCommands.participantsOf("not json at all"))
        assertEquals(emptyList(), BuzzCommands.participantsOf(""))
    }

    @Test
    fun rejectsNonArrayShapesInsteadOfStringifyingThem() {
        // The old split-on-comma parse yielded ["nope"] and ["x":1}] respectively.
        assertEquals(emptyList(), BuzzCommands.participantsOf("""{"participants":"nope"}"""))
        assertEquals(emptyList(), BuzzCommands.participantsOf("""{"participants":{"x":1}}"""))
    }

    @Test
    fun skipsNonStringElementsButKeepsTheRest() {
        assertEquals(listOf(b), BuzzCommands.participantsOf("""{"participants":[{"nested":"$a"},"$b"]}"""))
        assertEquals(listOf(a), BuzzCommands.participantsOf("""{"participants":["$a",null,"","   "]}"""))
    }

    @Test
    fun doesNotSplitAStringThatContainsACommaIntoTwoEntries() {
        // The regression the old `arr.toString().split(",")` parse produced.
        assertEquals(listOf("$a,$b"), BuzzCommands.participantsOf("""{"participants":["$a,$b"]}"""))
    }
}
