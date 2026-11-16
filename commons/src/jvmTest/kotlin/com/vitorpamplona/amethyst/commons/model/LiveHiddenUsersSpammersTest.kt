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
package com.vitorpamplona.amethyst.commons.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The notification card feeds skip their clear-and-rebuild when spam detection only added
 * spammers none of their notes are from. Anything else that changed must still rebuild them.
 */
class LiveHiddenUsersSpammersTest {
    private fun hidden(
        users: Set<String> = setOf("muted"),
        spammers: Set<String> = setOf("s1"),
        words: Set<String> = setOf("word"),
        sensitive: Boolean? = false,
    ) = LiveHiddenUsers(
        showSensitiveContent = sensitive,
        hiddenWordsCase = emptyList(),
        hiddenUsersHashCodes = users.mapTo(HashSet()) { it.hashCode() },
        spammersHashCodes = spammers.mapTo(HashSet()) { it.hashCode() },
        hiddenUsers = users,
        spammers = spammers,
        hiddenWords = words,
    )

    @Test
    fun newSpammersAreReported() = assertEquals(setOf("s2", "s3"), hidden().spammersAddedOnly(hidden(spammers = setOf("s1", "s2", "s3"))))

    @Test
    fun nothingChangedIsAnEmptyAddition() = assertEquals(emptySet(), hidden().spammersAddedOnly(hidden()))

    @Test
    fun aSpammerShownAgainIsNotOnlyAnAddition() = assertNull(hidden(spammers = setOf("s1", "s2")).spammersAddedOnly(hidden(spammers = setOf("s2", "s3"))))

    @Test
    fun aMuteIsNotOnlyAnAddition() = assertNull(hidden().spammersAddedOnly(hidden(users = setOf("muted", "other"), spammers = setOf("s1", "s2"))))

    @Test
    fun aHiddenWordIsNotOnlyAnAddition() = assertNull(hidden().spammersAddedOnly(hidden(words = setOf("word", "new"))))

    @Test
    fun theSensitiveSettingIsNotOnlyAnAddition() = assertNull(hidden().spammersAddedOnly(hidden(sensitive = true)))
}
