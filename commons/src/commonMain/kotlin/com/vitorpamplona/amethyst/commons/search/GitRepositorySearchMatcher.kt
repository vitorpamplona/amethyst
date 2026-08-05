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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * Local, in-memory matcher for the Git Repositories screen search box.
 *
 * The screen already loads the full set of ngit repository announcements
 * (`kind:30617` `GitRepositoryEvent`) the user has subscribed to via their
 * follow lists / follow set, so a client-side filter avoids issuing an
 * extra NIP-50 relay query for the common "I know its name/topic/host"
 * lookup. It also matches on fields the generic NIP-50 search would
 * ignore — clone/web URLs, maintainer npubs, the ngit `d` identifier
 * — which are exactly what someone browsing repos on gitworkshop /
 * ngit tends to remember.
 *
 * The query is split on whitespace so `"amethyst nostr"` requires each
 * term to appear in at least one indexed field of the same repository.
 * Every term match is case-insensitive.
 *
 * Indexed fields:
 *  - repo name (`name` tag)
 *  - repo identifier (`d` tag; what appears in the ngit URL path)
 *  - description
 *  - hashtags/topics (`t` tags)
 *  - clone URLs (`clone` tag values)
 *  - web URLs (`web` tag values)
 *  - relay URLs the maintainers listen on (`relays` tag values)
 *  - maintainer pubkeys (both hex and NIP-19 `npub…` form)
 *  - repo author pubkey (hex and npub)
 *  - earliest-unique-commit hash (`r … euc`) — lets you paste a commit
 *    hash from a nostr:naddr and land on the repo
 */
object GitRepositorySearchMatcher {
    /**
     * @return `true` when [event] matches every whitespace-separated term
     * in [query]. An empty query matches nothing (callers should skip the
     * filter path in that case).
     */
    fun matches(
        event: GitRepositoryEvent,
        query: String,
    ): Boolean {
        val terms = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return false

        val haystack = buildHaystack(event)
        return terms.all { term ->
            val needle = term.lowercase()
            // Support "npub1…" queries by resolving them to hex; the hex
            // form is already in the haystack via authorNpubs / dTag /
            // maintainers.
            val hexFromBech32 = tryDecodeNpubToHex(needle)
            haystack.any { field -> field.contains(needle) } ||
                (hexFromBech32 != null && haystack.any { field -> field.contains(hexFromBech32) })
        }
    }

    private fun buildHaystack(event: GitRepositoryEvent): List<String> {
        val out = ArrayList<String>(16)
        event.name()?.lowercase()?.let(out::add)
        event
            .dTag()
            .takeIf { it.isNotEmpty() }
            ?.lowercase()
            ?.let(out::add)
        event.description()?.lowercase()?.let(out::add)
        event.hashtags().forEach { out.add(it.lowercase()) }
        event.clones().forEach { out.add(it.lowercase()) }
        event.webs().forEach { out.add(it.lowercase()) }
        event.relays().forEach { out.add(it.lowercase()) }
        // Maintainers as hex + npub. Author is an implicit maintainer per
        // NIP-34, so include it in both forms too.
        val authors = HashSet<String>()
        authors.add(event.pubKey)
        authors.addAll(event.maintainers())
        authors.forEach { hex ->
            out.add(hex.lowercase())
            hexToNpub(hex)?.let { out.add(it.lowercase()) }
        }
        event.earliestUniqueCommit()?.lowercase()?.let(out::add)
        return out
    }

    private val WHITESPACE = Regex("\\s+")

    private fun tryDecodeNpubToHex(candidate: String): String? {
        if (!candidate.startsWith("npub1")) return null
        return runCatching {
            when (val parsed = Nip19Parser.uriToRoute(candidate)?.entity) {
                is NPub -> parsed.hex
                else -> null
            }
        }.getOrNull()
    }

    private fun hexToNpub(hex: String): String? = runCatching { NPub.create(hex) }.getOrNull()
}
