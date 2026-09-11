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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** The group-state changes a kind `1210` row can record. */
enum class MarmotSystemType(
    val wireName: String,
    val defaultText: String,
) {
    MEMBER_ADDED("member_added", "Member added"),
    MEMBER_REMOVED("member_removed", "Member removed"),
    MEMBER_LEFT("member_left", "Member left"),
    ADMIN_ADDED("admin_added", "Admin added"),
    ADMIN_REMOVED("admin_removed", "Admin removed"),
    GROUP_RENAMED("group_renamed", "Group renamed"),
    GROUP_AVATAR_CHANGED("group_avatar_changed", "Group avatar changed"),
    GROUP_DISBANDED("group_disbanded", "Group disbanded"),
    ;

    companion object {
        fun fromWire(name: String): MarmotSystemType? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * A kind `1210` group system row
 * (`foundation/application-messages.md`, "Group system events").
 *
 * These rows are **synthesized locally from canonical group state**, not
 * received as messages. That is what makes them trustworthy: a row derived from
 * an MLS-authenticated commit cannot be forged by one member, and every client
 * that applies the same commit derives the same row. A client MUST NOT wait for
 * a 1210 *message* to learn that group state changed — the state notification
 * is authoritative, and a 1210 that does arrive over the wire is an assertion by
 * its sender, not a derived fact.
 *
 * They are also not chat: render them separately, and never treat [text] as a
 * chat body.
 */
class MarmotSystemEvent(
    val systemType: MarmotSystemType,
    /** Committing member, when the change is attributable. */
    val actor: HexKey?,
    /** The member the change concerns, for the member/admin types. */
    val subject: HexKey? = null,
    /** New group name, for [MarmotSystemType.GROUP_RENAMED]. */
    val name: String? = null,
    /** Human-readable fallback. Clients SHOULD render from the structured fields instead. */
    val text: String = systemType.defaultText,
) {
    /**
     * The `content` JSON.
     *
     * Built by hand rather than via a serializer because this string is inside
     * the app event's id preimage: a library that reorders members or spaces
     * them differently would produce a different id for the same row, and a
     * peer would reject it.
     */
    fun toContentJson(): String {
        val sb = StringBuilder(128)
        sb.append("{\"v\":").append(SCHEMA_VERSION)
        sb.append(",\"system_type\":\"").append(systemType.wireName).append("\"")
        sb.append(",\"text\":")
        appendJsonString(sb, text)
        sb.append(",\"data\":{")
        var first = true
        actor?.let {
            sb.append("\"actor\":\"").append(it).append("\"")
            first = false
        }
        subject?.let {
            if (!first) sb.append(',')
            sb.append("\"subject\":\"").append(it).append("\"")
            first = false
        }
        name?.let {
            if (!first) sb.append(',')
            sb.append("\"name\":")
            appendJsonString(sb, it)
        }
        sb.append("}}")
        return sb.toString()
    }

    /**
     * The complete app event for this row.
     *
     * [author] is the account the row is attributed to — the committer for an
     * attributable change. The row is anchored to the epoch the change reached,
     * so [createdAt] should be that moment, not the moment it was rendered.
     */
    fun toAppEvent(
        author: HexKey,
        createdAt: Long,
    ) = MarmotAppEvent.build(
        pubKey = author,
        kind = MarmotAppEvent.KIND_SYSTEM,
        content = toContentJson(),
        createdAt = createdAt,
        tags = arrayOf(arrayOf("system", systemType.wireName)),
    )

    companion object {
        const val SCHEMA_VERSION = 1

        /**
         * Parse a 1210 row's content, or null when it is not one we understand.
         *
         * An unknown `system_type` returns null rather than throwing: the
         * registry grows, and protocol processing MUST NOT reject an otherwise
         * valid app payload just because its semantics are unfamiliar. The
         * caller delivers it and declines to render it.
         */
        fun fromAppEvent(event: MarmotAppEvent): MarmotSystemEvent? {
            if (event.kind != MarmotAppEvent.KIND_SYSTEM) return null
            // Bounded before parsed: a row's content is peer-authored, and
            // deep nesting or a huge collection costs a parser far more than
            // it costs whoever sent it.
            if (!MarmotJson.withinResourceBounds(event.content)) return null
            return try {
                val obj = MarmotJson.parseObject(event.content)
                if (obj.int("v") != SCHEMA_VERSION) return null
                val type = MarmotSystemType.fromWire(obj.string("system_type")) ?: return null
                val data = obj.element("data")?.let { MarmotJson.parseObject(it.toString()) }
                MarmotSystemEvent(
                    systemType = type,
                    actor = data?.takeIf { it.containsKey("actor") }?.string("actor"),
                    subject = data?.takeIf { it.containsKey("subject") }?.string("subject"),
                    name = data?.takeIf { it.containsKey("name") }?.string("name"),
                    text = if (obj.containsKey("text")) obj.string("text") else type.defaultText,
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun appendJsonString(
            sb: StringBuilder,
            value: String,
        ) {
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else ->
                        if (ch < ' ') {
                            sb.append("\\u")
                            sb.append(ch.code.toString(16).padStart(4, '0'))
                        } else {
                            sb.append(ch)
                        }
                }
            }
            sb.append('"')
        }
    }
}
