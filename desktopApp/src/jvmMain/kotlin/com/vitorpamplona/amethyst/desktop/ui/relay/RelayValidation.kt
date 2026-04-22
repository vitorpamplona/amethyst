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
 * Validates and adds a relay URL to a mutable list.
 * Returns an error message string if validation fails, null on success.
 */
internal fun validateRelayUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return "Enter a relay URL"
    if (!trimmed.startsWith("wss://") && !trimmed.startsWith("ws://")) {
        return "URL must start with wss:// or ws://"
    }
    if (trimmed.startsWith("ws://") && !trimmed.contains(".onion")) {
        return "Use wss:// — unencrypted ws:// exposes traffic to observers"
    }
    // Must have a domain with at least one dot (foo is not a valid relay)
    val host =
        trimmed
            .removePrefix("wss://")
            .removePrefix("ws://")
            .split("/")
            .first()
    if (!host.contains(".")) {
        return "Invalid domain — must contain at least one dot (e.g., relay.example.com)"
    }
    if (RelayUrlNormalizer.normalizeOrNull(trimmed) == null) {
        return "Invalid relay URL"
    }
    return null
}

internal fun tryAddSimpleRelay(
    url: String,
    existing: MutableList<NormalizedRelayUrl>,
): String? {
    val error = validateRelayUrl(url)
    if (error != null) return error
    val normalized = RelayUrlNormalizer.normalizeOrNull(url.trim())!!
    if (existing.any { it.url == normalized.url }) {
        return "Relay already added"
    }
    existing.add(normalized)
    return null
}
