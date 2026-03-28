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
package com.vitorpamplona.quartz.nip32Labeling

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelNamespaceTag
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-32: Label Event (kind 1985)
 *
 * Attaches labels to existing events, pubkeys, relays, or topics.
 * Supports distributed moderation, collection management, license assignment,
 * and content classification.
 */
@Immutable
class LabelEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey)

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds(): List<HexKey> = tags.mapNotNull(ETag::parseId)

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    /** All label namespace (`L`) tags on this event. */
    fun namespaces() = tags.mapNotNull(LabelNamespaceTag::parse)

    /** All label (`l`) tags on this event. */
    fun labels() = tags.mapNotNull(LabelTag::parse)

    /** Labels filtered by a specific namespace. */
    fun labelsByNamespace(namespace: String) = labels().filter { it.namespace == namespace }

    /** Referenced event IDs (label targets). */
    fun labeledEvents() = tags.mapNotNull(ETag::parseId)

    /** Referenced pubkeys (label targets). */
    fun labeledPubKeys() = tags.mapNotNull(PTag::parseKey)

    /** Referenced addresses (label targets). */
    fun labeledAddresses() = tags.mapNotNull(ATag::parseAddressId)

    /** Referenced hashtags/topics (label targets via `t` tag). */
    fun labeledHashtags() = tags.mapNotNull(HashtagTag::parse)

    /** Referenced relay URLs (label targets via `r` tag). */
    fun labeledRelayUrls(): List<String> =
        tags
            .filter { it.size >= 2 && it[0] == "r" && it[1].isNotEmpty() }
            .map { it[1] }

    companion object {
        const val KIND = 1985
        const val ALT = "Label event"

        /**
         * Build a label event for labeling events.
         */
        fun buildEventLabel(
            labeledEventId: HexKey,
            labeledEventRelay: String? = null,
            labeledEventAuthor: HexKey? = null,
            labels: List<LabelTag>,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate<LabelEvent>(KIND, content, createdAt) {
            alt(ALT)
            eTag(ETag(labeledEventId, labeledEventRelay?.let { RelayUrlNormalizer.normalizeOrNull(it) }, labeledEventAuthor))
            labels.map { it.namespace }.distinct().forEach { labelNamespace(it) }
            labels.forEach { label(it) }
        }

        /**
         * Build a label event for labeling pubkeys.
         */
        fun buildPubKeyLabel(
            labeledPubKey: HexKey,
            labeledPubKeyRelay: String? = null,
            labels: List<LabelTag>,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate<LabelEvent>(KIND, content, createdAt) {
            alt(ALT)
            pTag(labeledPubKey, labeledPubKeyRelay?.let { RelayUrlNormalizer.normalizeOrNull(it) })
            labels.map { it.namespace }.distinct().forEach { labelNamespace(it) }
            labels.forEach { label(it) }
        }

        /**
         * Build a label event with custom tag targets and labels.
         */
        fun build(
            labels: List<LabelTag>,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate<LabelEvent>(KIND, content, createdAt) {
            alt(ALT)
            labels.map { it.namespace }.distinct().forEach { labelNamespace(it) }
            labels.forEach { label(it) }
        }
    }
}
