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
package com.vitorpamplona.amethyst.commons.nip34Git.checkout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitCheckoutCommandsTest {
    @Test
    fun branchFollowsNgit() {
        assertEquals("pr/fix-login(0123abcd)", GitCheckoutCommands.ngitBranch("0123abcdef", "fix-login"))
        assertEquals("pr/proposal(0123abcd)", GitCheckoutCommands.ngitBranch("0123abcdef", " "))
    }

    @Test
    fun fetchQuotesWhatTheShellWouldRead() {
        val commands = GitCheckoutCommands.gitFetchAndCheckout("https://relay.ngit.dev/npub1x/repo.git", "abc123", "pr/fix(0123abcd)")
        assertEquals(
            listOf(
                "git fetch https://relay.ngit.dev/npub1x/repo.git abc123",
                "git checkout -b 'pr/fix(0123abcd)' FETCH_HEAD",
            ),
            commands,
        )
        assertNull(GitCheckoutCommands.gitFetchAndCheckout(null, "abc", "b"))
        assertEquals("'it'\\''s'", GitCheckoutCommands.shellQuote("it's"))
    }

    @Test
    fun ideLinksEncodeTheUrl() {
        assertEquals(
            "vscode://vscode.git/clone?url=https%3A%2F%2Fgit.example.com%2Fa%20b.git",
            GitCheckoutCommands.vsCodeClone("https://git.example.com/a b.git"),
        )
        assertEquals(
            "jetbrains://idea/checkout/git?checkout.repo=https%3A%2F%2Fgit.example.com%2Fr.git&idea.required.plugins.id=Git4Idea",
            GitCheckoutCommands.jetBrainsClone("https://git.example.com/r.git"),
        )
    }
}
