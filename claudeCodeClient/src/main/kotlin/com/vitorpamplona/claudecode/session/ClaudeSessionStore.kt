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
package com.vitorpamplona.claudecode.session

import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import com.vitorpamplona.claudecode.protocol.ContentBlock
import com.vitorpamplona.claudecode.protocol.boolean
import com.vitorpamplona.claudecode.protocol.long
import com.vitorpamplona.claudecode.protocol.obj
import com.vitorpamplona.claudecode.protocol.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

/** A session as listed: what to show for it and where it ran. */
data class ClaudeSessionInfo(
    val sessionId: String,
    /** The name to show: the user's title, else the generated one, else the first prompt. */
    val title: String,
    val customTitle: String?,
    val firstPrompt: String?,
    val lastPrompt: String?,
    val cwd: String?,
    val gitBranch: String?,
    val createdAt: Long?,
    val lastModified: Long,
    val fileSize: Long,
    val file: File,
)

/** A session some Claude Code process has open right now (a terminal, an IDE, another app). */
data class ClaudeLiveSession(
    val sessionId: String,
    val pid: Long,
    val cwd: String?,
    /** As the CLI reports it, e.g. `busy` or `idle`. */
    val status: String?,
    /** `interactive` for a terminal session. */
    val kind: String?,
    val name: String?,
)

/** One message of a session's history, from its file. */
data class TranscriptEntry(
    val uuid: String,
    val role: Role,
    val content: List<ContentBlock>,
    val timestamp: Long?,
    val model: String?,
) {
    enum class Role { USER, ASSISTANT }
}

/**
 * The sessions Claude Code saved on this machine: one JSONL file per session under
 * `~/.claude/projects/<folder>/`, the same history `claude --resume` offers.
 *
 * The file format is Claude Code's own and not a published contract, so everything here reads
 * leniently: unknown entries are skipped, and a line that doesn't parse is ignored. Writes are
 * limited to what the CLI itself appends (a title line) and deleting a session.
 */
class ClaudeSessionStore(
    val configDir: File = defaultConfigDir(),
) {
    val projectsDir: File get() = File(configDir, "projects")
    private val liveDir: File get() = File(configDir, "sessions")

    private class Cached(
        val length: Long,
        val modified: Long,
        val info: ClaudeSessionInfo?,
        val scanned: Scan,
    )

    /**
     * What a scan found so far, and how far into the file it got. A session file only grows,
     * so the next scan of a busy session starts here instead of at the beginning.
     */
    private data class Scan(
        val offset: Long = 0,
        val customTitle: String? = null,
        val aiTitle: String? = null,
        val summary: String? = null,
        val firstPrompt: String? = null,
        val lastPrompt: String? = null,
        val cwd: String? = null,
        val gitBranch: String? = null,
        val createdAt: Long? = null,
        val hasMessages: Boolean = false,
    )

    private val cache = ConcurrentHashMap<String, Cached>()

    /** Every saved session, newest first; only those that ran in [cwd] when it is given. */
    fun listSessions(cwd: String? = null): List<ClaudeSessionInfo> {
        val files =
            projectsDir
                .listFiles { f -> f.isDirectory }
                .orEmpty()
                .flatMap { dir -> dir.listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }.orEmpty().toList() }
        val seen = files.mapTo(HashSet()) { it.absolutePath }
        cache.keys.retainAll(seen)
        return files
            .mapNotNull { info(it) }
            .filter { cwd == null || it.cwd == cwd }
            .sortedByDescending { it.lastModified }
    }

    /** The session's file, wherever its folder is. */
    fun sessionFile(sessionId: String): File? {
        if (!isSafeId(sessionId)) return null
        return projectsDir
            .listFiles { f -> f.isDirectory }
            .orEmpty()
            .map { File(it, "$sessionId.jsonl") }
            .firstOrNull { it.isFile }
    }

    fun session(sessionId: String): ClaudeSessionInfo? = sessionFile(sessionId)?.let { info(it) }

    /**
     * The messages of the session's current branch, oldest first. A session rewound or edited
     * keeps the abandoned branch in its file; following parent links back from the newest
     * message shows only what the conversation is now, as `claude --resume` does.
     */
    fun transcript(sessionId: String): List<TranscriptEntry> {
        val file = sessionFile(sessionId) ?: return emptyList()
        return readTranscript(file)
    }

    /** Sets the session's title, the way the CLI's own rename does: a `custom-title` line. */
    fun rename(
        sessionId: String,
        title: String,
    ) {
        val file = sessionFile(sessionId) ?: throw IOException("No session $sessionId")
        val line =
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("custom-title"),
                    "customTitle" to JsonPrimitive(title),
                    "sessionId" to JsonPrimitive(sessionId),
                ),
            )
        val needsNewline = file.length() > 0 && file.endsWithNewline().not()
        file.appendText((if (needsNewline) "\n" else "") + line.toString() + "\n")
        cache.remove(file.absolutePath)
    }

    /** Deletes the session's file and its folder of extras (subagent runs, tool outputs). */
    fun delete(sessionId: String): Boolean {
        val file = sessionFile(sessionId) ?: return false
        val extras = File(file.parentFile, sessionId)
        if (extras.isDirectory && !extras.deleteRecursively()) throw IOException("Could not delete $extras")
        val deleted = file.delete()
        cache.remove(file.absolutePath)
        return deleted
    }

    /**
     * Sessions some Claude Code process has open now, keyed by session id, from the CLI's
     * registry of running sessions. Entries whose process is gone are left out.
     */
    fun liveSessions(): Map<String, ClaudeLiveSession> =
        liveDir
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { file ->
                val obj = parseObject(file.readTextOrNull() ?: return@mapNotNull null) ?: return@mapNotNull null
                val sessionId = obj.string("sessionId") ?: return@mapNotNull null
                val pid = obj.long("pid") ?: return@mapNotNull null
                val alive = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
                if (!alive) return@mapNotNull null
                ClaudeLiveSession(sessionId, pid, obj.string("cwd"), obj.string("status"), obj.string("kind"), obj.string("name"))
            }.associateBy { it.sessionId }

    private fun info(file: File): ClaudeSessionInfo? {
        val length = file.length()
        val modified = file.lastModified()
        val cached = cache[file.absolutePath]
        if (cached != null && cached.length == length && cached.modified == modified) return cached.info
        // Grown since the last look (a session at work): read only what was added.
        val from = cached?.scanned?.takeIf { length >= cached.length && it.offset <= length } ?: Scan()
        val scanned =
            try {
                scan(file, from)
            } catch (_: IOException) {
                return null
            }
        val info = toInfo(file, scanned.shown, length, modified)
        cache[file.absolutePath] = Cached(length, modified, info, scanned.committed)
        return info
    }

    /**
     * Reads what the list needs from [from]'s offset on, without parsing every message: most
     * lines are only checked for a marker, and parsed when they carry a title, a prompt or the
     * folder. A last line still being written is left for the next scan.
     */
    private fun scan(
        file: File,
        from: Scan,
    ): ScanResult {
        var state = from
        val line = ByteArrayOutputStream()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(from.offset)
            val buffer = ByteArray(64 * 1024)
            var offset = from.offset
            while (true) {
                val n = raf.read(buffer)
                if (n < 0) break
                var start = 0
                for (i in 0 until n) {
                    if (buffer[i] == NEWLINE) {
                        line.write(buffer, start, i - start)
                        state = scanLine(line.toString(Charsets.UTF_8), state).copy(offset = offset + i + 1)
                        line.reset()
                        start = i + 1
                    }
                }
                line.write(buffer, start, n - start)
                offset += n
            }
        }
        // A last line with no newline yet counts now, but is read again next time: it may still grow.
        val shown = if (line.size() > 0) scanLine(line.toString(Charsets.UTF_8), state) else state
        return ScanResult(state, shown)
    }

    private class ScanResult(
        /** Up to the last whole line: where the next scan starts. */
        val committed: Scan,
        /** With the unfinished last line too: what the list shows now. */
        val shown: Scan,
    )

    private fun scanLine(
        line: String,
        state: Scan,
    ): Scan =
        when {
            line.contains("\"type\":\"custom-title\"") -> parseObject(line)?.string("customTitle")?.let { state.copy(customTitle = it) } ?: state
            line.contains("\"type\":\"ai-title\"") -> parseObject(line)?.string("aiTitle")?.let { state.copy(aiTitle = it) } ?: state
            line.contains("\"type\":\"summary\"") -> parseObject(line)?.string("summary")?.let { state.copy(summary = it) } ?: state
            line.contains("\"type\":\"last-prompt\"") -> parseObject(line)?.string("lastPrompt")?.let { state.copy(lastPrompt = it) } ?: state
            line.contains("\"type\":\"user\"") || line.contains("\"type\":\"assistant\"") ->
                state.copy(
                    hasMessages = true,
                    cwd = CWD.find(line)?.let { unescape(it.groupValues[1]) } ?: state.cwd,
                    gitBranch = BRANCH.find(line)?.let { unescape(it.groupValues[1]) } ?: state.gitBranch,
                    createdAt = state.createdAt ?: TIMESTAMP.find(line)?.let { parseInstant(it.groupValues[1]) },
                    firstPrompt =
                        state.firstPrompt
                            ?: if (line.contains("\"type\":\"user\"")) parseObject(line)?.let(::promptText) else null,
                )
            else -> state
        }

    private fun toInfo(
        file: File,
        scan: Scan,
        length: Long,
        modified: Long,
    ): ClaudeSessionInfo? {
        if (!scan.hasMessages && scan.customTitle == null) return null
        val sessionId = file.name.removeSuffix(".jsonl")
        val title = scan.customTitle ?: scan.aiTitle ?: scan.summary ?: scan.firstPrompt?.let(::oneLine) ?: sessionId
        return ClaudeSessionInfo(
            sessionId = sessionId,
            title = title,
            customTitle = scan.customTitle,
            firstPrompt = scan.firstPrompt,
            lastPrompt = scan.lastPrompt,
            cwd = scan.cwd,
            gitBranch = scan.gitBranch?.takeIf { it.isNotBlank() && it != "HEAD" },
            createdAt = scan.createdAt,
            lastModified = modified,
            fileSize = length,
            file = file,
        )
    }

    private class Node(
        val uuid: String,
        val parent: String?,
        val obj: JsonObject,
    )

    private fun readTranscript(file: File): List<TranscriptEntry> {
        val nodes = LinkedHashMap<String, Node>()
        var leaf: String? = null
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (!line.contains("\"uuid\"")) return@forEach
                val obj = parseObject(line) ?: return@forEach
                val uuid = obj.string("uuid") ?: return@forEach
                if (obj.boolean("isSidechain") == true) return@forEach
                // A compaction starts a new chain whose root points at the old one logically.
                val parent = obj.string("parentUuid") ?: obj.string("logicalParentUuid")
                nodes[uuid] = Node(uuid, parent, obj)
                val type = obj.string("type")
                if (type == "user" || type == "assistant") leaf = uuid
            }
        }
        val chain = ArrayList<Node>()
        val visited = HashSet<String>()
        var cursor = leaf
        while (cursor != null && visited.add(cursor)) {
            val node = nodes[cursor] ?: break
            chain.add(node)
            cursor = node.parent
        }
        // A file whose links don't reach back (an older format): fall back to file order.
        if (chain.isEmpty()) return nodes.values.mapNotNull { toEntry(it.obj) }

        // Calls made together in one turn are chained one after another, and the CLI hangs an
        // earlier call's result off its call, beside the chain. Those results belong to the
        // conversation; a branch the user abandoned (a typed prompt, a reply) does not.
        val onChain = chain.mapTo(HashSet()) { it.uuid }
        val besides = HashMap<String, MutableList<Node>>()
        nodes.values.forEach { node ->
            val parent = node.parent ?: return@forEach
            if (node.uuid !in onChain && parent in onChain && isToolResultsOnly(node.obj)) {
                besides.getOrPut(parent) { ArrayList() }.add(node)
            }
        }
        val ordered = ArrayList<Node>(chain.size + besides.size)
        chain.asReversed().forEach { node ->
            ordered.add(node)
            besides[node.uuid]?.let(ordered::addAll)
        }
        return ordered.mapNotNull { toEntry(it.obj) }
    }

    private fun isToolResultsOnly(obj: JsonObject): Boolean {
        if (obj.string("type") != "user") return false
        val content = obj.obj("message")?.get("content") as? JsonArray ?: return false
        return content.isNotEmpty() && content.all { (it as? JsonObject)?.string("type") == "tool_result" }
    }

    private fun toEntry(obj: JsonObject): TranscriptEntry? {
        val role =
            when (obj.string("type")) {
                "user" -> TranscriptEntry.Role.USER
                "assistant" -> TranscriptEntry.Role.ASSISTANT
                else -> return null
            }
        if (obj.boolean("isMeta") == true) return null
        if (obj.boolean("isCompactSummary") == true) return null
        val message = obj.obj("message") ?: return null
        val content = ContentBlock.listOf(message["content"])
        if (content.isEmpty()) return null
        return TranscriptEntry(
            uuid = obj.string("uuid") ?: return null,
            role = role,
            content = content,
            timestamp = obj.string("timestamp")?.let(::parseInstant),
            model = message.string("model"),
        )
    }

    /** The text of a prompt the user typed, or null for tool results and the CLI's own notes. */
    private fun promptText(obj: JsonObject): String? {
        if (obj.boolean("isMeta") == true || obj.boolean("isSidechain") == true || obj.boolean("isCompactSummary") == true) return null
        val text =
            ContentBlock
                .listOf(obj.obj("message")?.get("content"))
                .firstNotNullOfOrNull { (it as? ContentBlock.Text)?.text }
                ?.trim()
                ?: return null
        // Slash-command wrappers, hook output and reminders are written as user messages too.
        if (text.isEmpty() || text.startsWith("<")) return null
        return text
    }

    private fun oneLine(text: String) =
        text
            .lineSequence()
            .first { it.isNotBlank() }
            .trim()
            .take(TITLE_MAX)

    private fun File.endsWithNewline(): Boolean =
        try {
            RandomAccessFile(this, "r").use { raf ->
                raf.seek(raf.length() - 1)
                raf.read() == '\n'.code
            }
        } catch (_: IOException) {
            true
        }

    private fun File.readTextOrNull(): String? =
        try {
            readText()
        } catch (_: IOException) {
            null
        }

    companion object {
        private const val TITLE_MAX = 120
        private const val NEWLINE = '\n'.code.toByte()
        private val CWD = Regex("\"cwd\":\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val BRANCH = Regex("\"gitBranch\":\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val TIMESTAMP = Regex("\"timestamp\":\"([^\"]*)\"")
        private val SAFE_ID = Regex("[A-Za-z0-9-]+")

        /** `CLAUDE_CONFIG_DIR` when set, else `~/.claude`, where the CLI keeps its files. */
        fun defaultConfigDir(environment: Map<String, String> = System.getenv()): File = environment["CLAUDE_CONFIG_DIR"]?.let { File(it) } ?: File(System.getProperty("user.home"), ".claude")

        /** Session ids are UUIDs; anything else must not become a path. */
        fun isSafeId(sessionId: String) = SAFE_ID.matches(sessionId)

        private fun parseObject(line: String): JsonObject? =
            try {
                ClaudeMessage.json.parseToJsonElement(line) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun unescape(value: String): String =
            if (!value.contains('\\')) {
                value
            } else {
                try {
                    (ClaudeMessage.json.parseToJsonElement("\"$value\"") as JsonPrimitive).content
                } catch (_: IllegalArgumentException) {
                    value
                }
            }

        private fun parseInstant(value: String): Long? =
            try {
                Instant.parse(value).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
    }
}
