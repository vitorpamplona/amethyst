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
package com.vitorpamplona.quartz.nip34Git.patch

/** How a file changed within a diff. */
enum class GitFileChange {
    ADD,
    DELETE,
    MODIFY,
    RENAME,
}

/** The role of a single line inside a hunk. */
enum class GitDiffLineType {
    CONTEXT,
    ADD,
    DELETE,
}

class GitDiffLine(
    val type: GitDiffLineType,
    val content: String,
    val oldNumber: Int?,
    val newNumber: Int?,
)

class GitDiffHunk(
    val header: String,
    val oldStart: Int,
    val newStart: Int,
    val lines: List<GitDiffLine>,
)

class GitDiffFile(
    val oldPath: String?,
    val newPath: String?,
    val change: GitFileChange,
    val isBinary: Boolean,
    val hunks: List<GitDiffHunk>,
) {
    val displayPath: String get() = newPath ?: oldPath ?: "?"
    val additions: Int get() = hunks.sumOf { hunk -> hunk.lines.count { it.type == GitDiffLineType.ADD } }
    val deletions: Int get() = hunks.sumOf { hunk -> hunk.lines.count { it.type == GitDiffLineType.DELETE } }
}

/** A parsed patch: the human-readable commit message and the changed files. */
class ParsedPatch(
    val message: String,
    val files: List<GitDiffFile>,
) {
    val hasDiff: Boolean get() = files.isNotEmpty()
    val totalAdditions: Int get() = files.sumOf { it.additions }
    val totalDeletions: Int get() = files.sumOf { it.deletions }
}

/**
 * Parses a `git format-patch` / unified-diff string (the `content` of a NIP-34
 * patch event, kind 1617) into a commit message plus a structured list of file
 * diffs. Tolerant of partial input: anything that isn't a recognizable diff is
 * returned as the [ParsedPatch.message].
 */
object UnifiedDiffParser {
    private val DIFF_GIT_PREFIX = "diff --git "
    private val HUNK_PREFIX = "@@"
    private val MBOX_FROM = Regex("^From [0-9a-fA-F]{7,40}\\b.*")
    private val HEADER_KEYS =
        setOf(
            "from",
            "date",
            "subject",
            "mime-version",
            "content-type",
            "content-transfer-encoding",
            "message-id",
            "in-reply-to",
            "references",
        )

    fun parse(patch: String): ParsedPatch {
        val lines = patch.split("\n")
        val firstDiff = lines.indexOfFirst { it.startsWith(DIFF_GIT_PREFIX) }

        if (firstDiff < 0) {
            return ParsedPatch(cleanMessage(lines), emptyList())
        }

        val message = cleanMessage(lines.subList(0, firstDiff))
        val files = parseFiles(lines, firstDiff)
        return ParsedPatch(message, files)
    }

    private fun parseFiles(
        lines: List<String>,
        from: Int,
    ): List<GitDiffFile> {
        // Split into per-file blocks at each "diff --git" line.
        val blocks = ArrayList<IntRange>()
        var blockStart = -1
        for (i in from until lines.size) {
            if (lines[i].startsWith(DIFF_GIT_PREFIX)) {
                if (blockStart >= 0) blocks.add(blockStart until i)
                blockStart = i
            }
        }
        if (blockStart >= 0) blocks.add(blockStart until lines.size)

        return blocks.map { parseFile(lines, it) }
    }

    private fun parseFile(
        lines: List<String>,
        range: IntRange,
    ): GitDiffFile {
        var oldPath: String?
        var newPath: String?
        run {
            val header = lines[range.first].removePrefix(DIFF_GIT_PREFIX)
            val sep = header.indexOf(" b/")
            if (sep >= 0) {
                oldPath =
                    header
                        .substring(0, sep)
                        .removePrefix("a/")
                        .trim()
                        .ifBlank { null }
                newPath =
                    header
                        .substring(sep + 1)
                        .removePrefix("b/")
                        .trim()
                        .ifBlank { null }
            } else {
                oldPath = null
                newPath = null
            }
        }

        var change = GitFileChange.MODIFY
        var isBinary = false

        var i = range.first + 1
        while (i <= range.last && !lines[i].startsWith(HUNK_PREFIX)) {
            val line = lines[i]
            when {
                line.startsWith("new file mode") -> change = GitFileChange.ADD
                line.startsWith("deleted file mode") -> change = GitFileChange.DELETE
                line.startsWith("rename from ") -> {
                    change = GitFileChange.RENAME
                    oldPath = line.removePrefix("rename from ").trim()
                }
                line.startsWith("rename to ") -> {
                    change = GitFileChange.RENAME
                    newPath = line.removePrefix("rename to ").trim()
                }
                line.startsWith("--- ") -> oldPath = resolveSidePath(line.removePrefix("--- "), oldPath)
                line.startsWith("+++ ") -> newPath = resolveSidePath(line.removePrefix("+++ "), newPath)
                line.startsWith("Binary files") || line.startsWith("GIT binary patch") -> isBinary = true
            }
            i++
        }

        val hunks = if (isBinary) emptyList() else parseHunks(lines, i, range.last)
        if (oldPath == null && newPath != null && change == GitFileChange.MODIFY) change = GitFileChange.ADD
        if (newPath == null && oldPath != null && change == GitFileChange.MODIFY) change = GitFileChange.DELETE
        return GitDiffFile(oldPath, newPath, change, isBinary, hunks)
    }

    private fun parseHunks(
        lines: List<String>,
        from: Int,
        last: Int,
    ): List<GitDiffHunk> {
        val hunks = ArrayList<GitDiffHunk>()
        var i = from
        while (i <= last) {
            if (!lines[i].startsWith(HUNK_PREFIX)) {
                i++
                continue
            }
            val header = lines[i]
            val (oldStart, oldLen, newStart, newLen) = parseHunkHeader(header)
            val hunkLines = ArrayList<GitDiffLine>()
            var oldNo = oldStart
            var newNo = newStart
            // Bound the hunk by its declared line counts so trailing content (e.g. the
            // format-patch "-- " signature) is never mistaken for a deletion.
            var remainingOld = oldLen
            var remainingNew = newLen
            i++
            while (i <= last && (remainingOld > 0 || remainingNew > 0)) {
                val line = lines[i]
                if (line.startsWith(HUNK_PREFIX) || line.startsWith(DIFF_GIT_PREFIX)) break
                when {
                    line.startsWith("+") -> {
                        hunkLines.add(GitDiffLine(GitDiffLineType.ADD, line.substring(1), null, newNo))
                        newNo++
                        remainingNew--
                    }
                    line.startsWith("-") -> {
                        hunkLines.add(GitDiffLine(GitDiffLineType.DELETE, line.substring(1), oldNo, null))
                        oldNo++
                        remainingOld--
                    }
                    line.startsWith("\\") -> {} // "\ No newline at end of file"
                    else -> {
                        val text = if (line.startsWith(" ")) line.substring(1) else line
                        hunkLines.add(GitDiffLine(GitDiffLineType.CONTEXT, text, oldNo, newNo))
                        oldNo++
                        newNo++
                        remainingOld--
                        remainingNew--
                    }
                }
                i++
            }
            hunks.add(GitDiffHunk(header, oldStart, newStart, hunkLines))
        }
        return hunks
    }

    private data class HunkHeader(
        val oldStart: Int,
        val oldLen: Int,
        val newStart: Int,
        val newLen: Int,
    )

    /** Parses `@@ -oldStart,oldLen +newStart,newLen @@ section`. Omitted lengths default to 1. */
    private fun parseHunkHeader(header: String): HunkHeader {
        // header looks like: @@ -12,7 +12,9 @@ optional context
        val body = header.removePrefix("@@").substringBefore("@@").trim()
        var oldStart = 0
        var oldLen = 1
        var newStart = 0
        var newLen = 1
        for (token in body.split(' ')) {
            when {
                token.startsWith("-") -> {
                    val v = token.removePrefix("-")
                    oldStart = v.substringBefore(',').toIntOrNull() ?: 0
                    oldLen = if (',' in v) v.substringAfter(',').toIntOrNull() ?: 1 else 1
                }
                token.startsWith("+") -> {
                    val v = token.removePrefix("+")
                    newStart = v.substringBefore(',').toIntOrNull() ?: 0
                    newLen = if (',' in v) v.substringAfter(',').toIntOrNull() ?: 1 else 1
                }
            }
        }
        return HunkHeader(oldStart, oldLen, newStart, newLen)
    }

    /** Resolves a `--- ` / `+++ ` target: `/dev/null` clears the side; a real path sets it; blank keeps [current]. */
    private fun resolveSidePath(
        raw: String,
        current: String?,
    ): String? {
        val path = raw.trim()
        if (path == "/dev/null") return null
        val cleaned = path.removePrefix("a/").removePrefix("b/")
        return cleaned.ifBlank { current }
    }

    /** Extracts the commit-message body from a format-patch preamble, dropping mbox/email headers and the diffstat. */
    private fun cleanMessage(lines: List<String>): String {
        var i = 0
        if (i < lines.size && MBOX_FROM.matches(lines[i])) i++

        val looksLikeHeaders = i < lines.size && lines[i].substringBefore(':').lowercase() in HEADER_KEYS
        if (looksLikeHeaders) {
            while (i < lines.size && lines[i].isNotBlank()) i++
            if (i < lines.size && lines[i].isBlank()) i++
        }

        val body = StringBuilder()
        while (i < lines.size) {
            if (lines[i].trimEnd() == "---") break
            body.append(lines[i]).append('\n')
            i++
        }
        return body.toString().trim()
    }
}
