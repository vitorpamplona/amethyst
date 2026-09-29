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
package com.vitorpamplona.quartz.nip66RelayMonitor.discovery

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.AcceptedKindTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RttType
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-66 Kind 30166: Relay Discovery Event
 *
 * Published by relay monitors to document the state of a specific relay.
 * The `d` tag holds the normalized relay URL, making each relay URL
 * addressable per monitor pubkey.
 *
 * Carries metrics and metadata observed during monitoring:
 * - Round-trip times (rtt-open, rtt-read, rtt-write)
 * - Network type (n), relay type (T)
 * - Supported NIPs (N), accepted kinds (k)
 * - Requirements (R: auth, payment, pow, writes)
 * - Topics (t) and geohash (g)
 */
@Immutable
class RelayDiscoveryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    /**
     * NIP-66: the relay itself is this event's `d` (a URL, not linked); its topics, geohashes and
     * accepted kinds are. A negated `k` is a kind the relay rejects, so it is not a `k` it has.
     */
    override fun links(): List<Link<*>> =
        links {
            hashtags(tags)
            each(tags, GeoHashTag::parse) { tag(Relation.TAG, GeoHashTag.TAG_NAME, it) }
            each(tags, AcceptedKindTag::parse) { if (!it.negated) tag(Relation.TAG, AcceptedKindTag.TAG_NAME, it.kind.toString()) }
        }

    fun relay(): NormalizedRelayUrl? = RelayUrlNormalizer.normalizeOrNull(dTag())

    fun rtt() = tags.rtts()

    fun rttOpen() = tags.rtt(RttType.OPEN)

    fun rttRead() = tags.rtt(RttType.READ)

    fun rttWrite() = tags.rtt(RttType.WRITE)

    fun networkTypes() = tags.networkTypes()

    fun relayTypes() = tags.relayTypes()

    fun supportedNips() = tags.supportedNips()

    fun requirements() = tags.requirements()

    fun acceptedKinds() = tags.acceptedKinds()

    fun topics() = tags.topics()

    fun geohashes() = tags.geohashes()

    companion object {
        const val KIND = 30166

        fun build(
            relayUrl: NormalizedRelayUrl,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayDiscoveryEvent>.() -> Unit = {},
        ) = eventTemplate<RelayDiscoveryEvent>(KIND, content, createdAt) {
            dTag(relayUrl.url)
            initializer()
        }

        fun build(
            relayUrl: String,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayDiscoveryEvent>.() -> Unit = {},
        ) = build(
            relayUrl = RelayUrlNormalizer.normalize(relayUrl),
            content = content,
            createdAt = createdAt,
            initializer = initializer,
        )
    }
}
