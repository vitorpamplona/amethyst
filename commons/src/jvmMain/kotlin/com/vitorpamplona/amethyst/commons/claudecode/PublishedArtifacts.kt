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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File
import java.io.IOException

/**
 * Something Claude put on claude.ai during a session: a page it published with its `Artifact`
 * tool, an artifact it made from one of the account's types (a deck, a design), or a document it
 * started through the Claude Docs connector. Read from the call's input and, once it ran, its
 * result, which is where the link is.
 */
@Immutable
data class PublishedArtifact(
    val kind: Kind,
    val change: Change,
    /** The name Claude gave it, when the call says; a page's own `<title>` is read by [PublishedArtifacts.withPageTitle]. */
    val title: String?,
    /** The one-sentence subtitle Claude wrote for the gallery card. */
    val description: String?,
    /** Its claude.ai link. Null until the call returns, and when the result carries none. */
    val url: String?,
    /** The file the page was published from, as the call named it (a path on this machine). */
    val file: String?,
    /** Which version of the artifact this publish made, when the result says. */
    val version: Int?,
) {
    enum class Kind {
        /** A page Claude wrote and published (HTML, or Markdown). */
        PAGE,

        /** An artifact made from one of the account's types: slides, a design, a dashboard, a sheet. */
        TYPED,

        /** A document started through the Claude Docs connector. */
        DOCUMENT,

        /** One Claude only opened: the call says nothing of what it is. */
        UNKNOWN,
    }

    enum class Change {
        CREATED,
        UPDATED,

        /** Claude showed the user one that already existed. */
        OPENED,
    }
}

object PublishedArtifacts {
    /** Claude Code's tool that publishes pages to claude.ai. */
    const val ARTIFACT_TOOL = "Artifact"

    /** The Claude Docs connector's server, as its tools are named: `mcp__Claude_Docs__batch`, or with the CLI's `claude_ai_` prefix. */
    private const val DOCS_SERVER = "claude_docs"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * An artifact's link: `claude.ai/artifact/<id>`, `claude.ai/code/artifact/<uuid>`, either with
     * the title in front of the id, or on `preview.claude.ai`.
     */
    private val LINK = Regex("""https://(?:preview\.)?claude\.ai/(?:code/)?artifact/[A-Za-z0-9_-]+""")

    /** "Published … at <link> (Version 2, version id …)". */
    private val VERSION = Regex("""\(Version (\d+)\b""")

    /** A page names itself in its first 8 KB, where claude.ai looks for the `<title>` too. */
    private const val TITLE_SCAN_BYTES = 8 * 1024
    private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** A Docs batch that starts a doc names it under `container.create`. */
    private const val CREATE_KEY = "\"create\""

    /** File types a browser shows as a page: what "open in browser" offers for a file Claude wrote. */
    private val PAGE_EXTENSIONS = setOf("html", "htm", "xhtml", "svg")

    /**
     * What [name] called with [input] put on claude.ai, or null for any other call (a read, a
     * listing, an asset upload, a doc's later edits). [result] is the call's output once it ran.
     */
    fun of(
        name: String,
        input: String,
        result: String?,
    ): PublishedArtifact? {
        val isDocs = isDocsTool(name, "batch")
        if (name != ARTIFACT_TOOL && !isDocs) return null
        // Most of the connector's batches fill a doc a section at a time, each with the section's
        // whole text, and the chat's rows are made again on every change: only a birth is read.
        if (isDocs && !input.contains(CREATE_KEY)) return null
        val obj =
            try {
                json.parseToJsonElement(input) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return null
        return if (isDocs) document(obj, result) else artifact(obj, result)
    }

    fun of(call: ChatItem.ToolCall): PublishedArtifact? = of(call.name, call.input, call.result)

    /** Whether [name] is the Claude Docs connector's [tool], however the CLI prefixed its server. */
    fun isDocsTool(
        name: String,
        tool: String,
    ): Boolean {
        if (!name.startsWith("mcp__") || !name.endsWith("__$tool")) return false
        val server = name.removePrefix("mcp__").removeSuffix("__$tool").lowercase()
        return server == DOCS_SERVER || server == "claude_ai_$DOCS_SERVER"
    }

    private fun artifact(
        input: JsonObject,
        result: String?,
    ): PublishedArtifact? {
        val existing = input.str("url")
        return when (input.str("action") ?: "publish") {
            "publish" -> {
                // An upload into an artifact's asset store, or assets copied from another: not a page of its own.
                if (input.bool("asset") == true) return null
                val typeUrl = input.str("type_url")
                // A type's result also names the type and the pages to read first: none of those is this one.
                val notThis = setOfNotNull(typeUrl, input.str("from_url"))
                val version =
                    result?.let {
                        VERSION
                            .find(it)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    }
                PublishedArtifact(
                    kind = if (typeUrl != null) PublishedArtifact.Kind.TYPED else PublishedArtifact.Kind.PAGE,
                    // A republish of the same file keeps its link without naming it: the version says so.
                    change = if (existing != null || (version ?: 1) > 1) PublishedArtifact.Change.UPDATED else PublishedArtifact.Change.CREATED,
                    title = input.str("title"),
                    description = input.str("description"),
                    url = existing ?: result?.let { links(it).firstOrNull { link -> link !in notThis } },
                    file = input.str("file_path"),
                    version = version,
                )
            }
            "open" ->
                existing?.let {
                    PublishedArtifact(PublishedArtifact.Kind.UNKNOWN, PublishedArtifact.Change.OPENED, null, null, it, null, null)
                }
            else -> null
        }
    }

    /**
     * A new doc: a `batch` call whose `container.create` names it. The rest of the connector's
     * calls fill or edit a doc section by section, and would be a card each.
     */
    private fun document(
        input: JsonObject,
        result: String?,
    ): PublishedArtifact? {
        val create = (input["container"] as? JsonObject)?.get("create") as? JsonObject ?: return null
        // Built on a blank Docs artifact the user gave: that one's link is the doc's.
        val existing = create.str("artifact")?.let { links(it).firstOrNull() }
        return PublishedArtifact(
            kind = PublishedArtifact.Kind.DOCUMENT,
            change = PublishedArtifact.Change.CREATED,
            title = create.str("name"),
            description = null,
            url = existing ?: result?.let { links(it).firstOrNull() },
            file = null,
            version = null,
        )
    }

    /** Every artifact link in [text], in order, each once. */
    fun links(text: String): List<String> =
        LINK
            .findAll(text)
            .map { it.value }
            .distinct()
            .toList()

    /** [artifact] with its page's own `<title>` when the call gave it no title. Reads the file: call it off the main thread. */
    fun withPageTitle(
        artifact: PublishedArtifact,
        cwd: File?,
    ): PublishedArtifact {
        if (artifact.title != null) return artifact
        val file = artifact.file?.let { resolve(it, cwd) } ?: return artifact
        return pageTitle(file)?.let { artifact.copy(title = it) } ?: artifact
    }

    /** The `<title>` in the first 8 KB of [file], on one line; null when it has none or can't be read. */
    fun pageTitle(file: File): String? {
        if (!file.isFile) return null
        val head =
            try {
                file.inputStream().use { String(it.readNBytes(TITLE_SCAN_BYTES), Charsets.UTF_8) }
            } catch (e: IOException) {
                return null
            }
        return TITLE
            .find(head)
            ?.groupValues
            ?.get(1)
            ?.let(::unescape)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** What to call [artifact] when neither the call nor the page names it: its file's name. */
    fun fallbackTitle(artifact: PublishedArtifact): String? = artifact.file?.let { File(it).nameWithoutExtension }?.takeIf { it.isNotBlank() }

    /** Whether [path] is a page a browser shows: offered as "open in browser" where Claude wrote it. */
    fun isPage(path: String): Boolean = File(path).extension.lowercase() in PAGE_EXTENSIONS

    /**
     * The page at [path] (from the session's folder when relative), to open in the browser: null
     * unless the file is there and is a page. A link is judged by the file it leads to, so a
     * `report.html` that points at a program is not one. Reads the disk: call it off the main thread.
     */
    fun localPage(
        path: String,
        cwd: File?,
    ): File? {
        if (!isPage(path)) return null
        val target =
            try {
                resolve(path, cwd).canonicalFile
            } catch (e: IOException) {
                return null
            }
        return target.takeIf { isPage(it.path) && it.isFile }
    }

    /** [path] as a file on this machine: relative ones are from the session's folder. */
    fun resolve(
        path: String,
        cwd: File?,
    ): File {
        val file = File(path)
        return if (file.isAbsolute || cwd == null) file else File(cwd, path)
    }

    /**
     * What a session put on claude.ai, newest first: each artifact once, as its latest publish left
     * it, its title the first one it was given. A subagent's are in its call's rows.
     */
    fun inSession(items: List<ChatItem>): List<PublishedArtifact> {
        val byUrl = LinkedHashMap<String, PublishedArtifact>()

        fun visit(list: List<ChatItem>) {
            list.forEach { item ->
                if (item !is ChatItem.ToolCall) return@forEach
                if (!item.isError) {
                    of(item)?.let { artifact ->
                        val url = artifact.url ?: return@let
                        val earlier = byUrl.remove(url)
                        byUrl[url] =
                            if (earlier == null) {
                                artifact
                            } else {
                                // Opening one again says nothing new about it.
                                val latest = if (artifact.change == PublishedArtifact.Change.OPENED) earlier else artifact
                                latest.copy(
                                    // A doc started in a blank Docs artifact is that artifact, by the doc's name.
                                    title = if (startsDocIn(artifact, earlier)) artifact.title ?: earlier.title else earlier.title ?: latest.title,
                                    description = latest.description ?: earlier.description,
                                    file = latest.file ?: earlier.file,
                                )
                            }
                    }
                }
                visit(item.subagent)
            }
        }
        visit(items)
        return byUrl.values.reversed()
    }

    /** Whether [artifact] is a doc started in [earlier], a blank artifact of the Docs type made just before. */
    fun startsDocIn(
        artifact: PublishedArtifact,
        earlier: PublishedArtifact,
    ): Boolean = artifact.kind == PublishedArtifact.Kind.DOCUMENT && earlier.kind == PublishedArtifact.Kind.TYPED

    private fun unescape(text: String): String =
        text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}
