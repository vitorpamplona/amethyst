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

/**
 * What a reader runs to get a pull request or patch into their own checkout, the way ngit names
 * it: `ngit pr checkout <nevent>` puts a PR on branch `pr/<branch-name>(<8 hex of its id>)`;
 * plain git fetches its tip from where the event says it is served onto that same branch name.
 */
object GitCheckoutCommands {
    /** ngit's local branch for a PR: `pr/<branch-name>(<first 8 hex of the event id>)`. */
    fun ngitBranch(
        eventId: String,
        branchName: String?,
    ): String {
        val name = branchName?.trim()?.takeIf { it.isNotEmpty() } ?: "proposal"
        return "pr/$name(${eventId.take(8)})"
    }

    /** `ngit pr checkout <nevent>`: ngit's own way in, which also follows later revisions. */
    fun ngitCheckout(nevent: String) = "ngit pr checkout $nevent"

    /** `ngit pr apply <nevent>`: applies a patch series onto the current branch. */
    fun ngitApply(nevent: String) = "ngit pr apply $nevent"

    /**
     * Plain git, no ngit: fetch [tip] from [cloneUrl] and branch it. Returns null without a clone
     * URL or tip, since there is then nothing to fetch.
     */
    fun gitFetchAndCheckout(
        cloneUrl: String?,
        tip: String?,
        branch: String,
    ): List<String>? {
        if (cloneUrl.isNullOrBlank() || tip.isNullOrBlank()) return null
        return listOf(
            "git fetch ${shellQuote(cloneUrl)} $tip",
            "git checkout -b ${shellQuote(branch)} FETCH_HEAD",
        )
    }

    /** VS Code's built-in git extension clones [cloneUrl] and opens it. */
    fun vsCodeClone(cloneUrl: String) = "vscode://vscode.git/clone?url=" + percentEncode(cloneUrl)

    /** JetBrains Toolbox clones [cloneUrl] into IntelliJ IDEA (or the IDE the user picks). */
    fun jetBrainsClone(cloneUrl: String) = "jetbrains://idea/checkout/git?checkout.repo=" + percentEncode(cloneUrl) + "&idea.required.plugins.id=Git4Idea"

    /** Single-quotes [value] for a POSIX shell when it holds anything a shell would read. */
    fun shellQuote(value: String): String = if (value.all { it.isLetterOrDigit() || it in SAFE }) value else "'" + value.replace("'", "'\\''") + "'"

    private const val SAFE = "-_./:@%+=,"

    /** RFC 3986 percent-encoding of everything but the unreserved characters. */
    fun percentEncode(value: String): String {
        val out = StringBuilder()
        value.encodeToByteArray().forEach { byte ->
            val c = byte.toInt() and 0xff
            val ch = c.toChar()
            if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-_.~")) {
                out.append(ch)
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0f])
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
