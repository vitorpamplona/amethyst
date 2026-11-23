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
package com.vitorpamplona.amethyst.commons.claudecode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** What the side panel shows of a pull request comes from `gh`'s JSON. */
class PullRequestStateTest {
    @Test
    fun titleNumberAndSizeAreRead() {
        val pr =
            assertNotNull(
                GitHubPullRequests.parseState(
                    """[{"additions":469,"deletions":44,"isDraft":false,"number":4883,"state":"OPEN","title":"Adopt cordn-kmp 1.6.1","url":"https://github.com/o/r/pull/4883"}]""",
                ),
            )
        assertEquals("Adopt cordn-kmp 1.6.1", pr.title)
        assertEquals(4883, pr.number)
        assertEquals(469, pr.additions)
        assertEquals(44, pr.deletions)
    }

    @Test
    fun anOlderAnswerWithoutThemStillReads() {
        val pr = assertNotNull(GitHubPullRequests.parseState("""{"isDraft":true,"state":"OPEN","url":"https://github.com/o/r/pull/7"}"""))
        assertEquals(null, pr.title)
        assertEquals(null, pr.number)
        assertEquals(null, pr.additions)
    }
}
