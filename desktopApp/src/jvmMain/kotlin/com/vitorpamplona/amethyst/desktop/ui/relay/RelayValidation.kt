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
package com.vitorpamplona.amethyst.desktop.ui.relay

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * Normalizes a relay URL input — auto-prefixes wss:// if no scheme given.
 * Returns the normalized URL string ready for validation.
 */
internal fun normalizeRelayInput(url: String): String {
    val trimmed = url.trim()
    return when {
        trimmed.startsWith("wss://") || trimmed.startsWith("ws://") -> trimmed
        trimmed.contains(".onion") -> "ws://$trimmed"
        trimmed.contains(".") -> "wss://$trimmed"
        else -> trimmed
    }
}

/**
 * Validates a relay URL. Returns error message or null on success.
 * Auto-prefixes wss:// if no scheme given (e.g., "nos.lol" → "wss://nos.lol").
 */
internal fun validateRelayUrl(url: String): String? {
    val input = normalizeRelayInput(url)
    if (input.isBlank()) return "Enter a relay URL"
    if (!input.startsWith("wss://") && !input.startsWith("ws://")) {
        return "Invalid relay URL"
    }
    if (input.startsWith("ws://") && !input.contains(".onion")) {
        return "Use wss:// — unencrypted ws:// exposes traffic to observers"
    }
    val host =
        input
            .removePrefix("wss://")
            .removePrefix("ws://")
            .split("/")
            .first()
    if (!host.contains(".")) {
        return "Invalid domain — must contain at least one dot (e.g., relay.example.com)"
    }
    if (RelayUrlNormalizer.normalizeOrNull(input) == null) {
        return "Invalid relay URL"
    }
    return null
}

internal fun tryAddSimpleRelay(
    url: String,
    existing: MutableList<NormalizedRelayUrl>,
): String? {
    val input = normalizeRelayInput(url)
    val error = validateRelayUrl(input)
    if (error != null) return error
    val normalized = RelayUrlNormalizer.normalizeOrNull(input)!!
    if (existing.any { it.url == normalized.url }) {
        return "Relay already added"
    }
    existing.add(normalized)
    return null
}
