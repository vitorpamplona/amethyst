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
package com.vitorpamplona.quartz.nip28PublicChat.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The human-written reason of a NIP-28 kind 43 (hide message) or 44 (mute user).
 *
 * NIP-28 writes the content as a JSON object, `{"reason": "..."}`; Quartz (and other clients)
 * write the reason as plain text. A JSON object yields its `reason` string, or null without
 * one; anything else is the reason as written. Indexing the raw JSON would put the `reason`
 * key, braces and quotes into the search index, so every spec-shaped event matched "reason".
 */
internal fun moderationReason(content: String): String? {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return content

    val json =
        try {
            Json.parseToJsonElement(trimmed) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return content // braces, but not JSON: prose that happens to start with `{`

    return (json["reason"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null }
}
