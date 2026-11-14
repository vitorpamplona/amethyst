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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.IOException

/** A tool call made readable: what it changes, runs or looks at, instead of its JSON input. */
@Immutable
sealed interface ToolView {
    /**
     * An edit or a write, as a unified diff of one file (`diff --git` text, which the git diff
     * view draws). [path] is relative to the session's folder when it is inside it.
     */
    data class FileChange(
        val path: String,
        val diff: String,
        val added: Int,
        val removed: Int,
        val newFile: Boolean,
    ) : ToolView {
        /** [diff], parsed for the diff view once, off the main thread when [ToolViews.of] made it. */
        val patch: ParsedPatch by lazy { UnifiedDiffParser.parse(diff) }
    }

    data class Command(
        val command: String,
        val description: String?,
    ) : ToolView

    data class FileRead(
        val path: String,
        val fromLine: Int?,
        val toLine: Int?,
    ) : ToolView

    data class Search(
        val pattern: String,
        val scope: String?,
    ) : ToolView

    data class Web(
        val target: String,
    ) : ToolView

    /** A tool the app doesn't know: its input as it came. */
    data class Raw(
        val json: String,
    ) : ToolView
}

object ToolViews {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Views already worked out, by call: a chat row that scrolls back into view would otherwise
     * read its file, diff it and parse the diff again. Least recently used first; bounded by
     * count and by the size of the diffs it holds ([cachedChars]).
     */
    private val cache = LinkedHashMap<String, ToolView>(64, 0.75f, true)
    private var cachedChars = 0L

    private fun weight(view: ToolView) = (view as? ToolView.FileChange)?.diff?.length?.toLong() ?: 0L

    /**
     * [of], remembered: the same call in the same folder, in the same state, gives the same
     * view, with its diff already parsed. Call it off the main thread.
     */
    fun cached(
        name: String,
        input: String,
        cwd: File?,
        pending: Boolean,
    ): ToolView {
        val key = cacheKey(name, input, cwd, pending)
        synchronized(cache) { cache[key] }?.let { return it }
        val view = of(name, input, cwd, pending)
        (view as? ToolView.FileChange)?.patch
        synchronized(cache) {
            cache.put(key, view)?.let { cachedChars -= weight(it) }
            cachedChars += weight(view)
            val oldest = cache.entries.iterator()
            while ((cache.size > CACHE_SIZE || cachedChars > CACHE_CHARS) && cache.size > 1 && oldest.hasNext()) {
                cachedChars -= weight(oldest.next().value)
                oldest.remove()
            }
        }
        return view
    }

    /** The view [cached] already made for this call, if any: cheap enough for the main thread. */
    fun peek(
        name: String,
        input: String,
        cwd: File?,
        pending: Boolean,
    ): ToolView? = synchronized(cache) { cache[cacheKey(name, input, cwd, pending)] }

    /**
     * A key that doesn't hold the input itself (a write's input is the whole file): its length,
     * its hash (which a string computes once and keeps) and its two ends.
     */
    private fun cacheKey(
        name: String,
        input: String,
        cwd: File?,
        pending: Boolean,
    ) = "$pending\u0000$name\u0000${cwd?.path}\u0000${input.length}\u0000${input.hashCode()}\u0000${input.take(KEY_ENDS)}\u0000${input.takeLast(KEY_ENDS)}"

    fun cached(
        name: String,
        input: JsonObject,
        cwd: File?,
        pending: Boolean,
    ): ToolView = cached(name, input.toString(), cwd, pending)

    /** The view of a call whose input is still JSON text, as the transcript keeps it. */
    fun of(
        name: String,
        input: String,
        cwd: File?,
        pending: Boolean = false,
    ): ToolView {
        val obj =
            try {
                json.parseToJsonElement(input) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return ToolView.Raw(input)
        return of(name, obj, cwd, pending)
    }

    /**
     * The view of a call. [pending] is true while the call waits for approval: the file is
     * still as it was, so an edit is placed by its old text and a write is compared with the
     * file it replaces.
     */
    fun of(
        name: String,
        input: JsonObject,
        cwd: File?,
        pending: Boolean = false,
    ): ToolView =
        when (name) {
            "Edit" -> edit(input, cwd, pending, listOf(input))
            "MultiEdit" -> edit(input, cwd, pending, (input["edits"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty())
            "Write" -> write(input, cwd, pending)
            "Bash", "PowerShell" -> input.str("command")?.let { ToolView.Command(it, input.str("description")) }
            "Read", "NotebookRead" ->
                (input.str("file_path") ?: input.str("notebook_path"))?.let { path ->
                    val offset = input.int("offset")
                    val limit = input.int("limit")
                    val from = offset ?: if (limit != null) 1 else null
                    ToolView.FileRead(relative(path, cwd), from, if (from != null && limit != null) from + limit - 1 else null)
                }
            "Grep", "Glob" -> input.str("pattern")?.let { ToolView.Search(it, (input.str("path") ?: input.str("glob"))?.let { p -> relative(p, cwd) }) }
            "WebFetch" -> input.str("url")?.let { ToolView.Web(it) }
            "WebSearch" -> input.str("query")?.let { ToolView.Web(it) }
            else -> null
        } ?: ToolView.Raw(input.toString())

    /** A tool input's values on one line (`app.theme DARK`), for tools the app doesn't draw itself. */
    fun argumentsLine(input: String): String {
        val obj =
            try {
                json.parseToJsonElement(input) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return input
        return obj.values.joinToString(" ") { (it as? JsonPrimitive)?.content ?: it.toString() }
    }

    /** [path] from the session's folder when it is inside it, as the user thinks of the file. */
    fun relative(
        path: String,
        cwd: File?,
    ): String {
        if (cwd == null) return path
        val base = cwd.absolutePath.trimEnd(File.separatorChar) + File.separatorChar
        return if (path.startsWith(base)) path.removePrefix(base) else path
    }

    private fun edit(
        input: JsonObject,
        cwd: File?,
        pending: Boolean,
        edits: List<JsonObject>,
    ): ToolView? {
        val path = input.str("file_path") ?: return null
        val shown = relative(path, cwd)
        val current = readSmall(File(path))
        val hunks = StringBuilder()
        var added = 0
        var removed = 0
        edits.forEach { edit ->
            val old = edit.str("old_string") ?: return@forEach
            val new = edit.str("new_string") ?: return@forEach
            // Where the change sits: by its old text before it ran, by its new text after.
            val anchor = if (pending) old else new
            val firstLine = current?.indexOf(anchor)?.takeIf { it >= 0 && anchor.isNotEmpty() }?.let { lineOf(current, it) } ?: 1
            val diff = LineDiff.unified(lines(old), lines(new), firstLine, firstLine)
            hunks.append(diff.hunks)
            added += diff.added
            removed += diff.removed
        }
        if (hunks.isEmpty()) return null
        return ToolView.FileChange(shown, header(shown, newFile = false) + hunks, added, removed, newFile = false)
    }

    private fun write(
        input: JsonObject,
        cwd: File?,
        pending: Boolean,
    ): ToolView? {
        val path = input.str("file_path") ?: return null
        val content = input.str("content") ?: return null
        val shown = relative(path, cwd)
        // Before it runs, a write over a file is compared with that file; after, the old text is gone.
        val before = if (pending) readSmall(File(path)) else null
        val diff = LineDiff.unified(before?.let(::lines).orEmpty(), lines(content), 1, 1)
        if (diff.hunks.isEmpty()) return null
        val newFile = before == null
        return ToolView.FileChange(shown, header(shown, newFile) + diff.hunks, diff.added, diff.removed, newFile)
    }

    private fun header(
        path: String,
        newFile: Boolean,
    ): String = "diff --git a/$path b/$path\n" + (if (newFile) "--- /dev/null\n" else "--- a/$path\n") + "+++ b/$path\n"

    private fun lineOf(
        text: String,
        index: Int,
    ): Int {
        var line = 1
        for (i in 0 until index) if (text[i] == '\n') line++
        return line
    }

    internal fun lines(text: String): List<String> = if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n')

    private fun readSmall(file: File): String? =
        try {
            if (file.isFile && file.length() <= MAX_READ) file.readText() else null
        } catch (e: IOException) {
            null
        }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private const val MAX_READ = 2_000_000L
    private const val CACHE_SIZE = 256
    private const val CACHE_CHARS = 8_000_000L
    private const val KEY_ENDS = 64
}

/** A line diff, written as unified-diff hunks with three lines of context. */
internal object LineDiff {
    class Result(
        val hunks: String,
        val added: Int,
        val removed: Int,
    )

    private const val CONTEXT = 3

    /**
     * Past this many line pairs the diff isn't searched (the table would take megabytes): the old
     * text is shown removed and the new added.
     */
    private const val MAX_CELLS = 1_000_000L

    private class Op(
        val type: Char,
        val text: String,
        val oldLine: Int,
        val newLine: Int,
    )

    fun unified(
        old: List<String>,
        new: List<String>,
        oldStart: Int,
        newStart: Int,
    ): Result {
        val ops = ops(old, new, oldStart, newStart)
        val changed = ops.indices.filter { ops[it].type != ' ' }
        if (changed.isEmpty()) return Result("", 0, 0)

        // Ranges of ops around each change, merged when their context touches.
        val ranges = ArrayList<IntRange>()
        changed.forEach { i ->
            val from = (i - CONTEXT).coerceAtLeast(0)
            val to = (i + CONTEXT).coerceAtMost(ops.size - 1)
            val last = ranges.lastOrNull()
            if (last != null && from <= last.last + 1) ranges[ranges.size - 1] = last.first..to else ranges.add(from..to)
        }

        val out = StringBuilder()
        ranges.forEach { range ->
            val slice = range.map { ops[it] }
            val oldCount = slice.count { it.type != '+' }
            val newCount = slice.count { it.type != '-' }
            // An empty side names the line before it, as git does.
            val oldFirst = slice.firstOrNull { it.type != '+' }?.oldLine ?: (slice.first().oldLine - 1)
            val newFirst = slice.firstOrNull { it.type != '-' }?.newLine ?: (slice.first().newLine - 1)
            out
                .append("@@ -")
                .append(oldFirst)
                .append(',')
                .append(oldCount)
            out
                .append(" +")
                .append(newFirst)
                .append(',')
                .append(newCount)
                .append(" @@\n")
            slice.forEach { out.append(it.type).append(it.text).append('\n') }
        }
        return Result(out.toString(), ops.count { it.type == '+' }, ops.count { it.type == '-' })
    }

    private fun ops(
        old: List<String>,
        new: List<String>,
        oldStart: Int,
        newStart: Int,
    ): List<Op> {
        // The lines both ends share stay out of the table.
        var prefix = 0
        while (prefix < old.size && prefix < new.size && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < old.size - prefix && suffix < new.size - prefix && old[old.size - 1 - suffix] == new[new.size - 1 - suffix]) suffix++
        val a = old.subList(prefix, old.size - suffix)
        val b = new.subList(prefix, new.size - suffix)

        val middle = ArrayList<Pair<Char, String>>()
        if (a.size.toLong() * b.size > MAX_CELLS) {
            a.forEach { middle.add('-' to it) }
            b.forEach { middle.add('+' to it) }
        } else {
            // Longest common subsequence, then walked from the start.
            val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
            for (i in a.size - 1 downTo 0) {
                for (j in b.size - 1 downTo 0) {
                    lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
            var i = 0
            var j = 0
            while (i < a.size && j < b.size) {
                when {
                    a[i] == b[j] -> {
                        middle.add(' ' to a[i])
                        i++
                        j++
                    }
                    lcs[i + 1][j] >= lcs[i][j + 1] -> middle.add('-' to a[i++])
                    else -> middle.add('+' to b[j++])
                }
            }
            while (i < a.size) middle.add('-' to a[i++])
            while (j < b.size) middle.add('+' to b[j++])
        }

        val result = ArrayList<Op>(old.size + new.size)
        var oldLine = oldStart
        var newLine = newStart

        fun add(
            type: Char,
            text: String,
        ) {
            result.add(Op(type, text, oldLine, newLine))
            if (type != '+') oldLine++
            if (type != '-') newLine++
        }
        old.subList(0, prefix).forEach { add(' ', it) }
        middle.forEach { (type, text) -> add(type, text) }
        old.subList(old.size - suffix, old.size).forEach { add(' ', it) }
        return result
    }
}
