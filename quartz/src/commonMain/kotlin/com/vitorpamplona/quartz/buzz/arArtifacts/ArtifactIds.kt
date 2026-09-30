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
package com.vitorpamplona.quartz.buzz.arArtifacts

/**
 * The two identifier grammars NIP-AR's envelope uses, mirroring `canonical_uuid` and
 * `event_id` in Buzz's `buzz-core/src/artifact.rs`.
 */
object ArtifactIds {
    private val CANONICAL_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"

    /**
     * True for a lowercase, hyphenated, non-nil UUID. Upstream parses with `Uuid::parse_str`
     * and then requires `id.to_string() == value`, which only the lowercase hyphenated form
     * satisfies (simple, braced, URN and uppercase forms all fail the round trip).
     */
    fun isCanonicalUuid(value: String): Boolean = value.length == 36 && CANONICAL_UUID.matches(value) && value != NIL_UUID

    /** True for a 64-character lowercase hex event id. */
    fun isEventId(value: String): Boolean {
        if (value.length != 64) return false
        for (c in value) {
            if (c !in '0'..'9' && c !in 'a'..'f') return false
        }
        return true
    }
}
