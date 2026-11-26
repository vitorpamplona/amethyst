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
package com.vitorpamplona.amethyst.commons.content

import com.vitorpamplona.amethyst.commons.content.asciidoc.AsciiDocToDocument
import com.vitorpamplona.amethyst.commons.content.djot.DjotToDocument
import com.vitorpamplona.amethyst.commons.content.markdown.MarkdownToDocument
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

/**
 * Parsed documents, shared by every viewer, so a body is parsed once and not every time it is
 * shown. The feed prefetcher calls [markdown] off the UI thread for what a card will draw, so the
 * screen usually finds it parsed; a miss parses in place.
 *
 * Keyed as [com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser] keys a note: the text,
 * the tags (custom emoji and `imeta` change the result), and what the Nostr pass is given. Every hit
 * re-checks the inputs, since a 32-bit hash collides. Keyed by text rather than event id because the
 * same event arrives translated, cropped by "Show more" or split at its code blocks, each a
 * different document.
 */
object CachedDocuments {
    private class Entry(
        val format: Int,
        val text: String,
        val tagsHash: Int,
        val callbackUri: String?,
        val authorPubKey: String?,
        val document: Document,
    ) {
        fun matches(
            format: Int,
            text: String,
            tagsHash: Int,
            callbackUri: String?,
            authorPubKey: String?,
        ) = this.format == format && this.tagsHash == tagsHash && this.callbackUri == callbackUri && this.authorPubKey == authorPubKey && this.text == text
    }

    // Articles are the documents worth keeping: a long one is the costly parse.
    private val cache = ConcurrentLruCache<Int, Entry>(100)

    private const val MARKDOWN = 1
    private const val DJOT = 2
    private const val ASCIIDOC = 3

    private fun key(
        format: Int,
        text: String,
        tagsHash: Int,
        callbackUri: String?,
        authorPubKey: String?,
    ): Int {
        var result = 31 * format + text.hashCode()
        result = 31 * result + tagsHash
        result = 31 * result + (callbackUri?.hashCode() ?: 0)
        result = 31 * result + (authorPubKey?.hashCode() ?: 0)
        return result
    }

    /** The parsed [markdown], if it is cached, without parsing it. */
    fun cachedMarkdown(
        markdown: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
        authorPubKey: String? = null,
    ): Document? {
        val tagsHash = tags.contentHash()
        return cache.get(key(MARKDOWN, markdown, tagsHash, callbackUri, authorPubKey))?.takeIf { it.matches(MARKDOWN, markdown, tagsHash, callbackUri, authorPubKey) }?.document
    }

    /** [markdown] as a [Document], parsed once for the same inputs. */
    fun markdown(
        markdown: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
        authorPubKey: String? = null,
    ): Document {
        val tagsHash = tags.contentHash()
        val key = key(MARKDOWN, markdown, tagsHash, callbackUri, authorPubKey)
        cache.get(key)?.takeIf { it.matches(MARKDOWN, markdown, tagsHash, callbackUri, authorPubKey) }?.let { return it.document }
        val document = MarkdownToDocument(NostrInlinePass(tags, callbackUri, authorPubKey)).parse(markdown)
        cache.put(key, Entry(MARKDOWN, markdown, tagsHash, callbackUri, authorPubKey, document))
        return document
    }

    /**
     * [djot] as a [Document], parsed once for the same inputs. [resolveWikilink] must be a function of
     * [authorPubKey] alone (NIP-54 wikilinks resolve to articles by the same author), since the
     * cache does not key on it.
     */
    fun djot(
        djot: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
        authorPubKey: String?,
        resolveWikilink: (String) -> String?,
    ): Document {
        val tagsHash = tags.contentHash()
        val key = key(DJOT, djot, tagsHash, callbackUri, authorPubKey)
        cache.get(key)?.takeIf { it.matches(DJOT, djot, tagsHash, callbackUri, authorPubKey) }?.let { return it.document }
        val document = DjotToDocument(NostrInlinePass(tags, callbackUri, authorPubKey), resolveWikilink).parse(djot)
        cache.put(key, Entry(DJOT, djot, tagsHash, callbackUri, authorPubKey, document))
        return document
    }

    /**
     * [asciidoc] as a [Document], parsed once for the same inputs. As with [djot], [resolveWikilink]
     * must be a function of [authorPubKey] alone.
     */
    fun asciidoc(
        asciidoc: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
        authorPubKey: String?,
        resolveWikilink: (String) -> String?,
    ): Document {
        val tagsHash = tags.contentHash()
        val key = key(ASCIIDOC, asciidoc, tagsHash, callbackUri, authorPubKey)
        cache.get(key)?.takeIf { it.matches(ASCIIDOC, asciidoc, tagsHash, callbackUri, authorPubKey) }?.let { return it.document }
        val document = AsciiDocToDocument(NostrInlinePass(tags, callbackUri, authorPubKey), resolveWikilink).parse(asciidoc)
        cache.put(key, Entry(ASCIIDOC, asciidoc, tagsHash, callbackUri, authorPubKey, document))
        return document
    }

    fun trimToSize(maxItems: Int) = cache.trimToSize(maxItems)
}
