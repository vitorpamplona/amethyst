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
package com.vitorpamplona.amethyst.commons.wiki

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.uri.findQueryParameterValue
import com.vitorpamplona.amethyst.commons.model.navigation.uri.percentDecodeQueryValue
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipsOnNostr
import com.vitorpamplona.amethyst.commons.wot.sortedByFollowsThenTrust
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent

/**
 * A NIP-54 topic: every author's article under one normalized `d` tag, and the redirects that point
 * away from it. A wikilink opens the topic, not one author's article, so the reader picks which
 * version to read; the version by the author whose article held the link comes first.
 */
object WikiTopics {
    /** The kinds a topic lists: the articles and the redirects named by the same slug. */
    val KINDS = listOf(WikiArticleEvent.KIND, WikiRedirectEvent.KIND)

    private const val PREFIX = "wiki?d="

    /** The app's own link to a topic. [from] is the author whose article linked to it, if any. */
    fun uri(
        slug: String,
        from: HexKey? = null,
    ): String = "nostr:" + PREFIX + slug + (from?.let { "&p=$it" } ?: "")

    fun isTopicUri(uri: String) = uri.startsWith(PREFIX) || uri.startsWith("nostr:$PREFIX")

    /** The topic [uri] opens; null when its slug normalizes to nothing. */
    fun route(uri: String): Route? {
        val slug = uri.findQueryParameterValue("d")?.let { WikiRedirectEvent.normalizeSlug(percentDecodeQueryValue(it)) }?.ifEmpty { null } ?: return null
        val from = uri.findQueryParameterValue("p")?.takeIf { isPubKeyHex(it) }
        return Route.WikiTopic(slug, from)
    }

    /** Whether [note] is an article or a redirect of [slug]: the `d` tag, normalized as NIP-54 asks. */
    fun belongsTo(
        note: Note,
        slug: String,
    ): Boolean {
        if (note !is AddressableNote) return false
        val kind = note.address.kind
        return (kind == WikiArticleEvent.KIND || kind == WikiRedirectEvent.KIND) && WikiRedirectEvent.normalizeSlug(note.address.dTag) == slug
    }

    /** The cached articles and redirects of [slug] that [account] may see. */
    fun cached(
        slug: String,
        account: Account,
    ): Set<Note> {
        val out = HashSet<Note>()
        for (kind in KINDS) out.addAll(LocalCache.addressables.filterIntoSet(kind) { _, note -> belongsTo(note, slug) && NipsOnNostr.isListable(note, account) })
        return out
    }

    /**
     * Versions in reading order: the linking author's first, then authors the account follows, then
     * by trust rank, then the newest. Redirects keep the same order among themselves.
     */
    fun order(
        notes: Collection<Note>,
        account: Account,
        from: HexKey?,
    ): List<Note> {
        val newestFirst = Comparator<Note> { a, b -> (b.createdAt() ?: 0L).compareTo(a.createdAt() ?: 0L) }
        val ranked = notes.toList().sortedByFollowsThenTrust(account, pubkeyOf = { it.author?.pubkeyHex ?: "" }, then = newestFirst)
        return if (from == null) ranked else ranked.sortedBy { if (it.author?.pubkeyHex == from) 0 else 1 }
    }

    /** The outboxes of the cached versions' authors, at most [PER_AUTHOR] each: where more versions are likely. */
    fun authorOutboxes(slug: String): Set<NormalizedRelayUrl> {
        val relays = HashSet<NormalizedRelayUrl>()
        for (kind in KINDS) {
            LocalCache.addressables.filterIntoSet(kind) { _, note ->
                if (belongsTo(note, slug)) note.author?.outboxRelays()?.let { relays.addAll(it.take(PER_AUTHOR)) }
                false
            }
        }
        return relays
    }

    private const val PER_AUTHOR = 3

    private fun isPubKeyHex(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
