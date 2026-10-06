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
package com.vitorpamplona.quartz.nip84Highlights

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.firstTaggedATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.firstTaggedAddress
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.firstTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.firstTaggedUserId
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip01Core.tags.references.ReferenceTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyIdentifierTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.nip84Highlights.tags.CommentTag
import com.vitorpamplona.quartz.nip84Highlights.tags.ContextTag
import com.vitorpamplona.quartz.nip84Highlights.tags.TextQuoteSelectorTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class HighlightEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(comment(), searchableContext(), content).joinToString("\n")

    // The read path: hands over the same fields indexableContent() joins, without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(comment())) return
        if (!visitor.visit(searchableContext())) return
        visitor.visit(content)
    }

    override fun eventHints(): List<EventIdHint> {
        val eHints = tags.mapNotNull(ETag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val eHints = tags.mapNotNull(ETag::parseId)
        val qHints = tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = citedNIP19().eventIds()

        return eHints + qHints + nip19Hints
    }

    override fun addressHints(): List<AddressHint> {
        val aHints = tags.mapNotNull(ATag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = citedNIP19().addressHints()

        return aHints + qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val aHints = tags.mapNotNull(ATag::parseAddressId)
        val qHints = tags.mapNotNull(QTag::parseAddressId)
        val nip19Hints = citedNIP19().addressIds()

        return aHints + qHints + nip19Hints
    }

    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints = tags.mapNotNull(PTag::parseAsHint)
        val nip19Hints = citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val pHints = tags.mapNotNull(PTag::parseKey)
        val nip19Hints = citedNIP19().pubKeys()

        return pHints + nip19Hints
    }

    /**
     * The highlight's `r` source. NIP-84 lets it be a URL or any other text naming the source,
     * so this is not necessarily a URL: check with [isUrlReference] before opening it. Prefers the
     * tag marked `source` and skips `mention` ones, which quote highlights use for URLs cited in
     * their comment.
     */
    fun inReference(): String? {
        var firstUnmarked: String? = null
        tags.forEach { tag ->
            if (tag.size > 1 && tag[0] == ReferenceTag.TAG_NAME && tag[1].isNotBlank()) {
                val marker = tag.getOrNull(2)
                if (marker == SOURCE_MARKER) return tag[1]
                if (marker != MENTION_MARKER && firstUnmarked == null) firstUnmarked = tag[1]
            }
        }
        return firstUnmarked
    }

    /** The `r` source when it is a web address; see [inReference] for any text. */
    fun inUrl(): String? = inReference()?.takeIf(::isUrlReference)

    /** The NIP-73 external ids (`i` tags) the highlight was taken from: a book, paper, podcast... */
    fun inExternalIds(): List<ExternalId> = tags.mapNotNull(ReplyIdentifierTag::parseExternalId)

    /** Raw `i` tag values, including the ones no [ExternalId] parser recognizes. */
    fun inExternalIdValues(): List<String> = tags.mapNotNull(ReplyIdentifierTag::parse)

    /**
     * The pubkey of the author of the highlighted content.
     *
     * NIP-84 marks that person with an `"author"` role on their `p` tag
     * (`["p", <pubkey>, <relay>, "author"]`) precisely so it can be told apart from the
     * `"mention"` p tags a highlight may also carry. Prefer the marked tag; fall back to the
     * first `p` tag for older/simpler highlights (including Amethyst's own) that tag only the
     * author and omit the role marker.
     *
     * Without this the first `p` tag wins regardless of role, so a highlight that mentions
     * other users before the author is attributed to a mention instead of the real author.
     */
    fun author() =
        tags.firstNotNullOfOrNull { tag ->
            if (tag.size > 3 && tag[0] == PTag.TAG_NAME && tag[3] == AUTHOR_MARKER && tag[1].isNotEmpty()) {
                tag[1]
            } else {
                null
            }
        } ?: firstTaggedUserId()

    fun quote() = content

    fun comment() = tags.firstNotNullOfOrNull(CommentTag::parse)

    fun context() = tags.firstNotNullOfOrNull(ContextTag::parse)

    fun textQuoteSelector() = tags.firstNotNullOfOrNull(TextQuoteSelectorTag::parse)

    /**
     * The paragraph-level context surrounding the highlight, preferring an explicit
     * NIP-84 `context` tag and falling back to reconstructing it from a W3C
     * `textquoteselector`'s prefix/suffix (as web highlighter clients emit) so the
     * in-context rendering still works when no `context` tag is present.
     *
     * The prefix/suffix are scraped from a web page, so they carry the page's
     * block-boundary whitespace — runs of newlines and spaces between DOM nodes — which
     * would otherwise render as a stack of blank lines around the highlight. Each run is
     * collapsed to a single space so the surrounding context reads as one continuous
     * passage; the highlight's own [content] is left verbatim so its offsets inside the
     * reconstructed context stay exact for the in-context marker.
     */
    fun contextOrReconstructed(): String? {
        context()?.let { return it }

        val selector = textQuoteSelector() ?: return null
        if (selector.prefix == null && selector.suffix == null) return null

        val prefix = selector.prefix?.replace(WHITESPACE_RUN, " ")?.trimStart() ?: ""
        val suffix = selector.suffix?.replace(WHITESPACE_RUN, " ")?.trimEnd() ?: ""

        return prefix + content + suffix
    }

    /**
     * The surrounding text worth indexing: the `context` tag, or -- when a web highlighter wrote
     * only a `textquoteselector` -- its prefix and suffix. Unlike [contextOrReconstructed] the
     * highlight itself is left out, since [content] is indexed on its own.
     *
     * Computed once per instance: search reads it per event per keystroke, and building it
     * re-parses the selector and runs a regex over both halves.
     */
    fun searchableContext(): String? = searchableContextValue

    // Lazy rather than a nullable cache field, because null is itself a valid, cacheable answer.
    private val searchableContextValue by lazy(LazyThreadSafetyMode.PUBLICATION) { computeSearchableContext() }

    private fun computeSearchableContext(): String? {
        context()?.let { return it }

        val selector = textQuoteSelector() ?: return null
        return listOfNotNull(selector.prefix, selector.suffix)
            .map { it.replace(WHITESPACE_RUN, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }
    }

    fun inPost() = firstTaggedATag()

    fun inPostAddress() = firstTaggedAddress()

    fun inPostVersion() = firstTaggedEvent()

    companion object {
        const val KIND = 9802

        /** NIP-84 role marker on the `p` tag that identifies the highlighted content's author. */
        private const val AUTHOR_MARKER = "author"

        /** NIP-84 marker for the `r` tag naming the highlighted source in a quote highlight. */
        const val SOURCE_MARKER = "source"

        /** NIP-84 marker for `r`/`p` tags cited by a quote highlight's comment. */
        const val MENTION_MARKER = "mention"

        private val WHITESPACE = Regex("\\s")

        /**
         * Whether an `r` value reads as a web address. NIP-84 allows any text there ("Chapter 3
         * of Dune"), which must neither be opened as a link nor get `https://` glued on.
         */
        fun isUrlReference(reference: String): Boolean {
            val trimmed = reference.trim()
            if (trimmed.isEmpty() || WHITESPACE.containsMatchIn(trimmed)) return false
            if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
                return HttpUrlFormatter.isValidUrl(trimmed)
            }
            // Scheme-less host/path, as some clients write it: needs a dotted host.
            val host = trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
            return host.contains('.') && !host.startsWith('.') && !host.endsWith('.') && HttpUrlFormatter.isValidUrl(trimmed)
        }

        /** Any run of whitespace (spaces, tabs, newlines) — collapsed to a single space. */
        private val WHITESPACE_RUN = Regex("\\s+")

        suspend fun create(
            msg: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): HighlightEvent = signer.sign(createdAt, KIND, emptyArray(), msg)

        /**
         * Builds a fully-tagged NIP-84 highlight from the pieces a browser share (or the
         * highlight composer) produces. The highlighted passage becomes the event `content`;
         * the remaining inputs are emitted as their NIP-84 tags when present:
         *
         * - [address] → an `a` reference to a nostr addressable source (e.g. a NIP-23 article),
         * - [event] → an `e` reference to a specific nostr event version highlighted,
         * - [author] → a `p` attribution to the highlighted content's author,
         * - [url] → an `r` source reference. A web address is normalized by [ReferenceTag] (clean
         *   it of trackers with [com.vitorpamplona.quartz.nip84Highlights.parse.UrlTrackerCleaner]
         *   first); any other text is kept verbatim, as NIP-84 allows,
         * - [externalIds] → `i` tags for NIP-73 sources (ISBNs, DOIs, podcast episodes...),
         * - [prefix]/[suffix] → a `textquoteselector` anchor (the `exact` field stays a
         *   placeholder since the passage already lives in `content`),
         * - [context] → the surrounding paragraph as a `context` tag,
         * - [comment] → the user's own note as a `comment` tag (turns it into a quote highlight).
         *
         * This covers every NIP-84 source: a web page ([url]), a nostr article ([address]),
         * a nostr note ([event]), each with optional author attribution and the user's note.
         */
        suspend fun create(
            quote: String,
            url: String? = null,
            prefix: String? = null,
            suffix: String? = null,
            comment: String? = null,
            context: String? = null,
            address: String? = null,
            event: String? = null,
            author: String? = null,
            externalIds: List<ExternalId> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): HighlightEvent = signer.sign(createdAt, KIND, assembleTags(url, prefix, suffix, comment, context, address, event, author, externalIds), quote)

        /**
         * The unsigned [EventTemplate] counterpart of [create], for the app's
         * sign-and-broadcast pipeline (`account.signAndComputeBroadcast(...)`). Same tag
         * assembly; the caller supplies the signer.
         */
        fun build(
            quote: String,
            url: String? = null,
            prefix: String? = null,
            suffix: String? = null,
            comment: String? = null,
            context: String? = null,
            address: String? = null,
            event: String? = null,
            author: String? = null,
            externalIds: List<ExternalId> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<HighlightEvent>.() -> Unit = {},
        ): EventTemplate<HighlightEvent> =
            eventTemplate(KIND, quote, createdAt) {
                addAll(assembleTags(url, prefix, suffix, comment, context, address, event, author, externalIds))
                initializer()
            }

        private fun assembleTags(
            url: String?,
            prefix: String?,
            suffix: String?,
            comment: String?,
            context: String?,
            address: String? = null,
            event: String? = null,
            author: String? = null,
            externalIds: List<ExternalId> = emptyList(),
        ): Array<Array<String>> {
            val tags = mutableListOf<Array<String>>()

            if (!address.isNullOrBlank()) {
                tags.add(ATag.assemble(address, null))
            }
            if (!event.isNullOrBlank()) {
                tags.add(ETag.assemble(event, null, null))
            }
            if (!author.isNullOrBlank()) {
                // Mark the role so [author] attributes to this p tag even when the highlight also
                // carries `mention` p tags — the producer-side counterpart of that reader logic.
                tags.add(arrayOf(PTag.TAG_NAME, author, "", AUTHOR_MARKER))
            }
            externalIds.forEach { tags.add(ReplyIdentifierTag.assemble(it)) }
            if (!url.isNullOrBlank()) {
                // `r` may be any text: only a web address gets URL-normalized. A quote highlight
                // marks its source so the comment's own cited URLs (`mention`) can't be mistaken for it.
                val reference = if (isUrlReference(url)) HttpUrlFormatter.normalize(url) else url.trim()
                tags.add(
                    if (comment.isNullOrBlank()) {
                        arrayOf(ReferenceTag.TAG_NAME, reference)
                    } else {
                        arrayOf(ReferenceTag.TAG_NAME, reference, SOURCE_MARKER)
                    },
                )
            }
            if (!prefix.isNullOrEmpty() || !suffix.isNullOrEmpty()) {
                tags.add(TextQuoteSelectorTag.assemble(null, prefix, suffix))
            }
            if (!context.isNullOrBlank()) {
                tags.add(ContextTag.assemble(context))
            }
            if (!comment.isNullOrBlank()) {
                tags.add(CommentTag.assemble(comment))
            }

            return tags.toTypedArray()
        }
    }
}
