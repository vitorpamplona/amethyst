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
package com.vitorpamplona.quartz.nip34Git.grasp

import com.vitorpamplona.quartz.nip34Git.repository.NostrCloneUrl

/**
 * URL conventions of a GRASP (Git Relays Authorized via Signed-Nostr Proofs)
 * server: one host that is both a nostr relay and a git smart-HTTP server.
 *
 * A repository is hosted on a GRASP server by listing the server in its
 * kind:30617 announcement twice: its clone URL
 * `https://<server>/<npub>/<identifier>.git` in `clone`, and its relay URL
 * `wss://<server>` in `relays`. The server only accepts pushes for
 * announcements that name it both ways. These helpers mirror ngit's
 * `normalize_grasp_server_url`, `format_grasp_server_url_as_clone_url` and
 * `format_grasp_server_url_as_relay_url` so a repository announced here points
 * at the same URLs `ngit init` would derive.
 */
object GraspServer {
    /** ngit's default GRASP servers, used when the user has no kind:10317 list. */
    val DEFAULT_SERVERS = listOf("relay.ngit.dev", "gitnostr.com")

    /**
     * Reduces any spelling of a GRASP server (`wss://host`, `https://host/`,
     * a clone URL, a bare host) to ngit's canonical form: `host[:port][/path]`
     * for TLS servers, `http://host[:port][/path]` for plain ones (`ws`/`http`).
     * Everything from an `npub1` path segment on is dropped, so a clone URL
     * normalizes to its server. Returns null when no host can be found.
     */
    fun normalize(url: String): String? {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        val scheme = if (schemeEnd > 0) trimmed.substring(0, schemeEnd).lowercase() else "https"
        val rest = if (schemeEnd > 0) trimmed.substring(schemeEnd + 3) else trimmed

        val withoutQuery = rest.substringBefore('?').substringBefore('#')
        val authority = withoutQuery.substringBefore('/').substringAfterLast('@').lowercase()
        val path = withoutQuery.substring(withoutQuery.indexOf('/').let { if (it < 0) withoutQuery.length else it })

        if (authority.isEmpty() || authority.startsWith(':')) return null

        val prefix = if (scheme == "ws" || scheme == "http") "http://" else ""
        var normalized = "$prefix$authority$path"

        val npubAt = normalized.indexOf("npub1")
        if (npubAt >= 0) normalized = normalized.substring(0, npubAt)

        return normalized.trimEnd('/').ifEmpty { null }
    }

    /** The relay URL a GRASP server answers nostr on: `wss://host`, or `ws://` for plain and `.onion` hosts. */
    fun relayUrl(server: String): String? {
        val normalized = normalize(server) ?: return null
        return when {
            normalized.startsWith("http://") -> "ws://" + normalized.removePrefix("http://")
            isOnion(normalized) -> "ws://$normalized"
            else -> "wss://$normalized"
        }
    }

    /**
     * The clone URL of [identifier] owned by [npub] on this server:
     * `https://host/<npub>/<percent-encoded identifier>.git` (`http://` for
     * plain and `.onion` hosts).
     */
    fun cloneUrl(
        server: String,
        npub: String,
        identifier: String,
    ): String? {
        val normalized = normalize(server) ?: return null
        val prefix =
            when {
                normalized.startsWith("http://") -> ""
                isOnion(normalized) -> "http://"
                else -> "https://"
            }
        return "$prefix$normalized/$npub/${NostrCloneUrl.percentEncode(identifier)}.git"
    }

    private fun isOnion(normalized: String): Boolean =
        normalized
            .removePrefix("http://")
            .substringBefore('/')
            .substringBeforeLast(':')
            .endsWith(".onion")
}
