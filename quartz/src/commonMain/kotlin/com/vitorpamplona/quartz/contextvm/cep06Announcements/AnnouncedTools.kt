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
package com.vitorpamplona.quartz.contextvm.cep06Announcements

import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The tool list a CEP-6 kind-11317 announcement carries.
 *
 * [ServerAnnouncement] deliberately leaves `content` as text, because the
 * announcement kinds each carry a different MCP result. This decodes the one
 * shape that matters for finding a server worth talking to: the `tools/list`
 * result, `{"tools":[{"name":…,"inputSchema":…}, …]}`.
 *
 * ## Why the names are the useful part
 *
 * An announcement says nothing about *what protocol* a server speaks — there
 * is no marker tag and no registry. What a server is, from the outside, is the
 * set of tools it serves. So a client looking for one specific kind of server
 * matches on the names, and [names] is what it matches against.
 */
class AnnouncedTools(
    /** Each tool definition verbatim, for a caller that wants the schemas. */
    val tools: List<JsonObject>,
) {
    /** Every advertised tool name, in announcement order, deduplicated. */
    val names: Set<String> =
        tools.mapNotNullTo(LinkedHashSet()) {
            (it[NAME] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
        }

    /** Whether every one of [required] is advertised. Extra tools are fine. */
    fun serves(required: Collection<String>): Boolean = names.containsAll(required)

    companion object {
        private const val NAME = "name"
        private const val TOOLS = "tools"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Reads the content of a kind-11317 announcement.
         *
         * Null when the text is not JSON or carries no `tools` array — an
         * announcement we cannot read is not an error to propagate, it is one
         * server in a relay's worth of them that this client skips.
         */
        fun parseOrNull(content: String): AnnouncedTools? {
            val root =
                try {
                    json.parseToJsonElement(content) as? JsonObject
                } catch (e: IllegalArgumentException) {
                    null
                } ?: return null

            val tools = root[TOOLS] as? JsonArray ?: return null
            return AnnouncedTools(tools.filterIsInstance<JsonObject>())
        }

        /** Reads [announcement] when it is a tool list; null for any other kind. */
        fun from(announcement: ServerAnnouncement): AnnouncedTools? =
            if (announcement.kind == CvmKinds.TOOLS_LIST) {
                parseOrNull(announcement.content)
            } else {
                null
            }
    }
}
