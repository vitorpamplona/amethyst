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

import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.agents.deleteTree
import com.vitorpamplona.claudecode.protocol.ClaudeContent
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
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
        // Files are read side by side: a first look reads all of them, close to a gigabyte for
        // someone with long sessions, and each is its own cache entry.
        return files
            .parallelStream()
            .map { info(it) }
            .toList()
            .filterNotNull()
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

    /**
     * What the session's subagents did, by the id of the Agent call that started each: their own
     * messages, oldest first. The CLI keeps each in `<session>/subagents/agent-<id>.jsonl`, with
     * a `.meta.json` beside it that names the call.
     */
    fun subagentTranscripts(sessionId: String): Map<String, List<TranscriptEntry>> {
        val file = sessionFile(sessionId) ?: return emptyMap()
        val dir = File(file.parentFile, "$sessionId/subagents")
        return dir
            .listFiles { f -> f.name.endsWith(META_SUFFIX) }
            .orEmpty()
            .mapNotNull { meta ->
                val toolUseId = meta.readTextOrNull()?.let(::parseObject)?.string("toolUseId") ?: return@mapNotNull null
                val log = File(dir, meta.name.removeSuffix(META_SUFFIX) + ".jsonl")
                if (log.isFile) toolUseId to readTranscript(log, sidechain = true) else null
            }.toMap()
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
        // Links in it (a tool output, a subagent folder) go, never what they point to.
        if (extras.isDirectory && !deleteTree(extras)) throw IOException("Could not delete $extras")
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
    ): Scan {
        // Title lines name their type first, the CLI's and ours alike; searching every line for
        // four markers cost a pass over the whole history each. A short line is still searched,
        // in case a writer orders its keys otherwise.
        if (line.startsWith(TYPE_FIRST) || line.length <= SHORT_LINE) {
            when {
                line.contains("\"type\":\"custom-title\"") -> return parseObject(line)?.string("customTitle")?.let { state.copy(customTitle = it) } ?: state
                line.contains("\"type\":\"ai-title\"") -> return parseObject(line)?.string("aiTitle")?.let { state.copy(aiTitle = it) } ?: state
                line.contains("\"type\":\"summary\"") -> return parseObject(line)?.string("summary")?.let { state.copy(summary = it) } ?: state
                line.contains("\"type\":\"last-prompt\"") -> return parseObject(line)?.string("lastPrompt")?.let { state.copy(lastPrompt = it) } ?: state
            }
        }
        val user = line.contains("\"type\":\"user\"")
        if (!user && !line.contains("\"type\":\"assistant\"")) return state
        return state.copy(
            hasMessages = true,
            // Where the session started: its project folder and `--resume` are keyed by it. Later
            // messages carry the shell's folder, which an agent's `cd` moves (into one of a
            // workstream's repositories), and taking that put the session in the wrong place.
            cwd = state.cwd ?: stringField(line, CWD_KEY),
            gitBranch = stringField(line, BRANCH_KEY) ?: state.gitBranch,
            createdAt = state.createdAt ?: stringField(line, TIMESTAMP_KEY)?.let(::parseInstant),
            firstPrompt = state.firstPrompt ?: if (user) parseObject(line)?.let(::promptText) else null,
        )
    }

    /**
     * The string value after the first [key] (`"name":"`), unescaped. A plain search: a regex
     * over lines that run to megabytes was a third of the time the session list took to load.
     */
    private fun stringField(
        line: String,
        key: String,
    ): String? {
        val start = line.indexOf(key)
        if (start < 0) return null
        val from = start + key.length
        var i = from
        while (i < line.length) {
            when (line[i]) {
                '\\' -> i += 2
                '"' -> return unescape(line.substring(from, i))
                else -> i++
            }
        }
        return null
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

    /** [sidechain]: the file is a subagent's own, every line of it on the side chain. */
    private fun readTranscript(
        file: File,
        sidechain: Boolean = false,
    ): List<TranscriptEntry> {
        val nodes = LinkedHashMap<String, Node>()
        var leaf: String? = null
        try {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (!line.contains("\"uuid\"")) return@forEach
                    val obj = parseObject(line) ?: return@forEach
                    val uuid = obj.string("uuid") ?: return@forEach
                    if (!sidechain && obj.boolean("isSidechain") == true) return@forEach
                    // A compaction starts a new chain whose root points at the old one logically.
                    val parent = obj.string("parentUuid") ?: obj.string("logicalParentUuid")
                    nodes[uuid] = Node(uuid, parent, obj)
                    val type = obj.string("type")
                    if (type == "user" || type == "assistant") leaf = uuid
                }
            }
        } catch (_: IOException) {
            // Gone or unreadable between the lookup and the open: the same as never written.
            return emptyList()
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
        val content = ClaudeContent.listOf(message["content"])
        if (content.isEmpty()) return null
        return TranscriptEntry(
            uuid = obj.string("uuid") ?: return null,
            role = role,
            content = content,
            timestamp = obj.string("timestamp")?.let(::parseInstant),
            model = message.string("model"),
            parentUuid = obj.string("parentUuid"),
        )
    }

    /** The text of a prompt the user typed, or null for tool results and the CLI's own notes. */
    private fun promptText(obj: JsonObject): String? {
        if (obj.boolean("isMeta") == true || obj.boolean("isSidechain") == true || obj.boolean("isCompactSummary") == true) return null
        val text =
            ClaudeContent
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
        private const val CWD_KEY = "\"cwd\":\""
        private const val BRANCH_KEY = "\"gitBranch\":\""
        private const val TIMESTAMP_KEY = "\"timestamp\":\""
        private const val TYPE_FIRST = "{\"type\":\""

        /** Longer than any title line the CLI writes, far shorter than a message with tool output. */
        private const val SHORT_LINE = 4096
        private val SAFE_ID = Regex("[A-Za-z0-9-]+")

        /** `CLAUDE_CONFIG_DIR` when set, else `~/.claude`, where the CLI keeps its files. */
        private const val META_SUFFIX = ".meta.json"

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
