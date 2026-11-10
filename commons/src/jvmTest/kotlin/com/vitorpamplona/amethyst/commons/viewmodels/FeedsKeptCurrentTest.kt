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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.viewmodels.FeedsKeptCurrent.Companion.onlyHidesMoreThan
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A mute or a flagged spammer can only remove notes, so the kept-current feeds check what they
 * show instead of walking the cache; anything that could bring notes back rebuilds them.
 */
class FeedsKeptCurrentTest {
    private fun hidden(
        users: Set<String> = emptySet(),
        spammers: Set<String> = emptySet(),
        threads: Set<String> = emptySet(),
        showSensitive: Boolean? = null,
    ) = LiveHiddenUsers(
        showSensitiveContent = showSensitive,
        hiddenWordsCase = emptyList(),
        hiddenUsersHashCodes = emptySet(),
        spammersHashCodes = emptySet(),
        hiddenUsers = users,
        spammers = spammers,
        mutedThreads = threads,
    )

    @Test
    fun aMuteOrASpamFlagOnlyHidesMore() {
        val before = hidden(users = setOf("a"))
        assertTrue(hidden(users = setOf("a", "b")).onlyHidesMoreThan(before))
        assertTrue(hidden(users = setOf("a"), spammers = setOf("s")).onlyHidesMoreThan(before))
        assertTrue(hidden(users = setOf("a"), threads = setOf("t")).onlyHidesMoreThan(before))
        assertTrue(before.onlyHidesMoreThan(before))
    }

    @Test
    fun anUnmuteOrASettingChangeNeedsARebuild() {
        val before = hidden(users = setOf("a", "b"), spammers = setOf("s"))
        assertFalse(hidden(users = setOf("a"), spammers = setOf("s")).onlyHidesMoreThan(before))
        assertFalse(hidden(users = setOf("a", "b")).onlyHidesMoreThan(before))
        assertFalse(hidden(users = setOf("a", "b"), spammers = setOf("s"), showSensitive = true).onlyHidesMoreThan(before))
    }
}
