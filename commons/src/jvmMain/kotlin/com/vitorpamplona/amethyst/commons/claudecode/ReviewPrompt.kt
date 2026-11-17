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

import java.io.File

/** One reviewer comment on a proposal, as Claude gets it. */
data class ReviewItem(
    val id: String,
    /** The heading of the comment, already worded: "alice on `src/A.kt`, line 12:". */
    val heading: String,
    val body: String,
    /** Where an inline comment sits, to quote the code it is about. */
    val location: ReviewLocation? = null,
    val createdAt: Long = 0,
)

data class ReviewLocation(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    /** The line numbers count the old side of the diff (a removed line). */
    val removedSide: Boolean,
    /** The commit the reviewer read. */
    val commit: String?,
)

/**
 * Turns reviewers' comments into the message Claude gets: each comment under its author, and an
 * inline one with the lines it is about, read from the worktree (at the commit the reviewer saw,
 * or the base for a removed line), so Claude knows exactly what to change.
 */
object ReviewPrompt {
    /** Lines shown around the commented ones. */
    private const val CONTEXT = 2

    suspend fun render(
        dir: File,
        header: String,
        items: List<ReviewItem>,
    ): String {
        val base = items.firstOrNull { it.location?.removedSide == true }?.let { GitWorkspace.mergeBase(dir) }
        return buildString {
            append(header)
            items.sortedBy { it.createdAt }.forEach { item ->
                append("\n\n")
                append(item.heading)
                item.location?.let { loc -> quote(dir, loc, base)?.let { append("\n").append(it) } }
                if (item.body.isNotBlank()) append("\n").append(item.body.trim())
            }
        }
    }

    /** The commented lines with a little context, numbered, the commented ones marked with `>`. */
    private suspend fun quote(
        dir: File,
        loc: ReviewLocation,
        base: String?,
    ): String? {
        val revs = if (loc.removedSide) listOfNotNull(base) else listOfNotNull(loc.commit, "HEAD")
        val lines =
            revs.firstNotNullOfOrNull { rev ->
                GitWorkspace.linesAt(dir, rev, loc.path, loc.startLine - CONTEXT, loc.endLine + CONTEXT)
            } ?: return null
        val width =
            lines
                .last()
                .first
                .toString()
                .length
        val fence = "```"
        return buildString {
            append(fence).append(languageOf(loc.path)).append('\n')
            lines.forEach { (n, text) ->
                val mark = if (n in loc.startLine..loc.endLine) ">" else " "
                append(n.toString().padStart(width))
                    .append(mark)
                    .append(' ')
                    .append(text)
                    .append('\n')
            }
            append(fence)
        }
    }

    private fun languageOf(path: String): String =
        when (path.substringAfterLast('.', "").lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "md" -> "markdown"
            "xml" -> "xml"
            "json" -> "json"
            "sh" -> "bash"
            "py" -> "python"
            "ts", "tsx" -> "typescript"
            "js" -> "javascript"
            "rs" -> "rust"
            "go" -> "go"
            "swift" -> "swift"
            else -> ""
        }
}
