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
package com.vitorpamplona.quartz.buzz.workspace

/**
 * Buzz's custom-emoji shortcode rule (`normalize_custom_emoji_shortcode` in
 * `crates/buzz-sdk/src/builders.rs`). The relay runs it over every `emoji` tag of a NIP-30 emoji
 * set (`kind:30030`) or emoji list (`kind:10030`) at ingest (`validate_custom_emoji_tags` in
 * `buzz-relay/src/handlers/ingest.rs`) and rejects the whole event on the first failure.
 *
 * It is NIP-30's `[A-Za-z0-9_-]` alphabet plus a length cap NIP-30 does not have
 * ([MAX_SHORTCODE_BYTES]); surrounding whitespace and `:` delimiters are stripped first.
 */
object BuzzCustomEmoji {
    /** `MAX_CUSTOM_EMOJI_SHORTCODE_LEN`: the longest shortcode a Buzz relay accepts, in bytes. */
    const val MAX_SHORTCODE_BYTES = 64

    /**
     * The shortcode as Buzz normalizes it — trimmed, `:` delimiters removed, lowercased — or null
     * when a Buzz relay would reject it (empty, over [MAX_SHORTCODE_BYTES] bytes, or a character
     * outside `[A-Za-z0-9_-]`).
     */
    fun normalizeShortcode(shortcode: String): String? {
        val trimmed = shortcode.trim().trim(':')
        if (trimmed.isEmpty()) return null
        // The alphabet is ASCII-only, so the byte length equals the char length once it passes.
        if (trimmed.length > MAX_SHORTCODE_BYTES) return null
        if (!trimmed.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }) return null
        return trimmed.lowercase()
    }

    /** True when a Buzz relay would accept [shortcode] in a 30030/10030 `emoji` tag. */
    fun isValidShortcode(shortcode: String): Boolean = normalizeShortcode(shortcode) != null
}
