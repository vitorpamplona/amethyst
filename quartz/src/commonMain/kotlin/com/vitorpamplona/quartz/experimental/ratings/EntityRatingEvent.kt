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
package com.vitorpamplona.quartz.experimental.ratings

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.ratings.tags.HasCommentTag
import com.vitorpamplona.quartz.experimental.ratings.tags.MarkTag
import com.vitorpamplona.quartz.experimental.ratings.tags.RatingTag
import com.vitorpamplona.quartz.experimental.ratings.tags.StarsTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A rating of any entity (kind 34259).
 *
 * **Not defined by a merged NIP.** The upstream spec is `XYZ.md` in `abh3po/nostr-polls`
 * (Pollerama), which defines a deliberately generic "rate anything" addressable kind with
 * exactly three tags:
 *
 * - `d` — the id of the rated entity. When that id is not unique on its own the spec asks for
 *         it to be prefixed with the mark (`hashtag:books`); see [RatingMark.stripPrefix].
 * - `m` — the *mark*: what sort of thing is being rated. Absent means a nostr event.
 * - `rating` — the score, normalized to 0..1.
 *
 * Being addressable, `(pubkey, d)` is the identity: one rating per author per entity, and a
 * newer one replaces the older.
 *
 * Clients rating an addressable publication (kind 30040) publish four more tags that are **not**
 * in the spec but are what makes the event renderable, so they are parsed here too:
 * `a`/`A` (the rated coordinate), `e` (the rated event id), `k` (the rated kind), `p` (the rated
 * author) and `s`/`c` (see [StarsTag] / [HasCommentTag]).
 *
 * @see stars for how the two competing rating scales in the wild are resolved.
 */
@Immutable
class EntityRatingEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint) + tags.mapNotNull(RootAddressTag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId) + tags.mapNotNull(RootAddressTag::parseAddressId)

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey)

    /** What sort of thing is rated. Never null — an absent `m` means a nostr event, per the spec. */
    fun mark(): String = RatingMark.orDefault(tags.firstNotNullOfOrNull(MarkTag::parse))

    /** True when [mark] is one this client has a dedicated presentation for. */
    fun isKnownMark(): Boolean = mark() in KNOWN_MARKS

    /** The rated entity's id with the `<mark>:` prefix removed. Empty when the event has no `d`. */
    fun targetIdentifier(): String = RatingMark.stripPrefix(dTag(), tags.firstNotNullOfOrNull(MarkTag::parse))

    /** The `rating` value when it is on the spec's 0..1 scale; null when absent, unparseable or out of range. */
    fun ratingFraction(): Double? = tags.firstNotNullOfOrNull(RatingTag::parse)?.takeIf { it in 0.0..1.0 }

    /** The raw `s` value when present and inside 1..[MAX_STARS]. */
    fun starsTag(): Int? = tags.firstNotNullOfOrNull(StarsTag::parse)?.takeIf { it in 1..MAX_STARS }

    /**
     * The rating as a 0..[MAX_STARS] star count, or null when the event carries no usable score.
     *
     * Two incompatible conventions exist in the wild and the `rating` tag alone cannot always
     * separate them: `["rating", "1"]` means a full score to a client publishing the spec's 0..1
     * fraction, and one star out of five to a client publishing a raw count. So:
     *
     * 1. An `s` tag inside 1..[MAX_STARS] wins outright — it is the author's own star count.
     * 2. Otherwise a `rating` in 0..1 is read as a fraction and scaled up. The closed interval is
     *    deliberate: the spec's prose says "less than 1", but a full score really is published as
     *    `1.000`, and rejecting it would silently drop every five-star review.
     * 3. Otherwise a `rating` in 1..[MAX_STARS] is read as a raw star count.
     * 4. Otherwise null — a rating we cannot interpret renders as a review without stars, never
     *    as zero stars, which would misreport the author.
     *
     * Step 2 before step 3 is what resolves the ambiguous `"1"`, and it resolves it toward the
     * spec. That is the right default (the spec's scale is the only documented one) but it does
     * mean a bare `["rating", "1"]` from a raw-scale client reads as five stars rather than one.
     * Publishers that care should send `s`.
     */
    fun stars(): Double? {
        starsTag()?.let { return it.toDouble() }

        val raw = tags.firstNotNullOfOrNull(RatingTag::parse) ?: return null
        return when {
            raw < 0.0 -> null
            raw <= 1.0 -> raw * MAX_STARS
            raw <= MAX_STARS.toDouble() -> raw
            else -> null
        }
    }

    /** The rated addressable coordinate: `a`/`A` when present, else the de-prefixed `d`. */
    fun targetAddress(): Address? =
        tags.firstNotNullOfOrNull(ATag::parseAddress)
            ?: tags.firstNotNullOfOrNull(RootAddressTag::parseAddress)
            ?: Address.parse(targetIdentifier())

    /** The rated event's id, from the `e` tag. Absent for ratings that only name a coordinate. */
    fun targetEventId(): HexKey? = tags.firstNotNullOfOrNull(ETag::parseId)

    /** The rated event's kind, from the `k` tag. */
    fun targetKind(): Int? = tags.firstNotNullOfOrNull(ReplyKindTag::parse)?.toIntOrNull()

    /** The rated event's author, from the `p` tag. */
    fun targetAuthor(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)

    /**
     * Whether this rating carries a written review. The content is authoritative; the `c` tag is
     * only consulted when the content is empty, since it is the one case where a client may have
     * stripped the body but kept the flag.
     */
    fun hasComment(): Boolean = content.isNotBlank() || tags.firstNotNullOfOrNull(HasCommentTag::parse) == true

    /** A rating with nothing to point at cannot be rendered or aggregated. */
    fun hasTarget(): Boolean = targetAddress() != null || targetEventId() != null || targetIdentifier().isNotEmpty()

    companion object {
        const val KIND = 34259

        /** Every scale in this file is out of five; the spec's 0..1 fraction is `stars / 5`. */
        const val MAX_STARS = 5

        val KNOWN_MARKS =
            setOf(
                RatingMark.EVENT,
                RatingMark.PROFILE,
                RatingMark.RELAY,
                RatingMark.HASHTAG,
                RatingMark.BOOKS,
                RatingMark.MOVIES,
            )

        /**
         * Builds a rating of an addressable entity, in the shape the publishing clients use: the
         * `d` is the mark-prefixed coordinate, `rating` is `stars / 5`, and the extension tags
         * carry the target redundantly so a reader never has to parse the `d` back apart.
         */
        fun build(
            target: Address,
            mark: String,
            stars: Int,
            review: String = "",
            targetEventId: HexKey? = null,
            targetKind: Int? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EntityRatingEvent>.() -> Unit = {},
        ): EventTemplate<EntityRatingEvent> {
            val clamped = stars.coerceIn(1, MAX_STARS)
            val coordinate = target.toValue()

            return eventTemplate(KIND, review, createdAt) {
                dTag(RatingMark.applyPrefix(coordinate, mark))
                add(MarkTag.assemble(mark))
                add(RatingTag.assemble(clamped.toDouble() / MAX_STARS))
                add(StarsTag.assemble(clamped))
                add(ATag.assemble(coordinate, null))
                add(RootAddressTag.assemble(coordinate, null))
                targetEventId?.let { add(ETag.assemble(it, null, target.pubKeyHex)) }
                add(ReplyKindTag.assemble(targetKind ?: target.kind))
                add(PTag.assemble(target.pubKeyHex, null))
                if (review.isNotBlank()) add(HasCommentTag.assemble(true))

                initializer()
            }
        }
    }
}
