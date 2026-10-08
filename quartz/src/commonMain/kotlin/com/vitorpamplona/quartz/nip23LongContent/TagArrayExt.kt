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
package com.vitorpamplona.quartz.nip23LongContent

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitHintsTo
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitPubKeysTo

// Shared by the two NIP-23 kinds, long-form content (30023) and its draft (30024): the draft has
// "the same structure", so both read their metadata, search text and references through these.

fun TagArray.longFormTitle() = firstNotNullOfOrNull(TitleTag::parse)

fun TagArray.longFormSummary() = firstNotNullOfOrNull(SummaryTag::parse)

fun TagArray.longFormImage() = firstNotNullOfOrNull(ImageTag::parse)

/** The `published_at` timestamp, or null when absent or later than [createdAt] (a post in the future). */
fun TagArray.longFormPublishedAt(createdAt: Long): Long? {
    val publishedAt = firstNotNullOfOrNull(PublishedAtTag::parse) ?: return null
    return if (publishedAt <= createdAt) publishedAt else null
}

/**
 * The write-path search text: title, summary and body, then the topics (`t`) — authors pick
 * topics that the article text does not necessarily mention, as CommentEvent does.
 */
fun TagArray.longFormIndexableContent(content: String) = (listOfNotNull(longFormTitle(), longFormSummary(), content) + hashtags()).joinToString("\n")

/** The read path: the same fields [longFormIndexableContent] joins, handed over without the join. */
fun TagArray.forEachLongFormIndexableField(
    content: String,
    visitor: IndexableFieldVisitor,
) {
    if (!visitor.visit(longFormTitle())) return
    if (!visitor.visit(longFormSummary())) return
    if (!visitor.visit(content)) return
    // Inline over the tags rather than hashtags(): this runs per event per search keystroke.
    for (tag in this) {
        val topic = HashtagTag.parse(tag) ?: continue
        if (!visitor.visit(topic)) return
    }
}

// NIP-23: references "must be made according to NIP-27" (nostr: URIs in the text), optionally
// adding tags for them. The hint providers read the `q` and `p` tags, the NIP-57 zap splits and
// the URIs in [cited].

fun TagArray.longFormEventHints(cited: List<Entity>): List<EventIdHint> = mapNotNull(QTag::parseEventAsHint) + cited.eventHints()

fun TagArray.longFormLinkedEventIds(cited: List<Entity>): List<HexKey> {
    val result = ArrayList<HexKey>()
    mapNotNullTo(result) { QEventTag.parse(it)?.eventId }
    result.addAll(cited.eventIds())
    return result
}

fun TagArray.longFormAddressHints(cited: List<Entity>): List<AddressHint> = mapNotNull(QTag::parseAddressAsHint) + cited.addressHints()

/** Only well-formed `kind:pubkey:d` quotes (parsed through [QAddressableTag]), never a raw `q` value. */
fun TagArray.longFormLinkedAddressIds(cited: List<Entity>): List<String> {
    val result = ArrayList<String>()
    mapNotNullTo(result) { QAddressableTag.parse(it)?.address?.toValue() }
    result.addAll(cited.addressIds())
    return result
}

// Runs on every relay copy of every article: one list, filled in the order the old
// `p + zap + nip19` concatenation produced, instead of three lists and two copies.
fun TagArray.longFormPubKeyHints(cited: List<Entity>): List<PubKeyHint> {
    val result = mapNotNullTo(ArrayList(), PTag::parseAsHint)
    zapSplitHintsTo(result)
    result.addAll(cited.pubKeyHints())
    return result
}

fun TagArray.longFormLinkedPubKeys(cited: List<Entity>): List<HexKey> {
    val result = mapNotNullTo(ArrayList(), PTag::parseKey)
    zapSplitPubKeysTo(result)
    result.addAll(cited.pubKeys())
    return result
}
