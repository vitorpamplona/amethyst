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
package com.vitorpamplona.contextvm.discovery

import com.vitorpamplona.contextvm.core.CvmKinds
import com.vitorpamplona.contextvm.core.CvmTags
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.Tag

/**
 * What a peer told us about itself.
 *
 * CEP-35 makes this a *session* concept rather than an announcement one: the
 * same tag vocabulary arrives either on a public announcement (CEP-6) or on the
 * first direct message of a session, and both are treated as equivalent.
 */
data class DiscoverySurface(
    val name: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val website: String? = null,
    val supportsEncryption: Boolean = false,
    val supportsEphemeralEncryption: Boolean = false,
    val supportsOversizedTransfer: Boolean = false,
    val supportsOpenStream: Boolean = false,
    /**
     * Everything else, routing excluded.
     *
     * CEP-35 requires unknown discovery tags to be preserved rather than
     * discarded, so custom protocols can build on the same exchange. Keeping
     * them reachable is what makes that work.
     */
    val unknownTags: List<Tag> = emptyList(),
) {
    /** Raw access for a caller that understands a tag this version does not. */
    fun rawTag(name: String): Tag? = unknownTags.firstOrNull { it.isNotEmpty() && it[0] == name }

    companion object {
        private val KNOWN =
            setOf(
                CvmTags.NAME,
                CvmTags.ABOUT,
                CvmTags.PICTURE,
                CvmTags.WEBSITE,
                CvmTags.SUPPORT_ENCRYPTION,
                CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL,
                CvmTags.SUPPORT_OVERSIZED_TRANSFER,
                CvmTags.SUPPORT_OPEN_STREAM,
            )

        fun parse(tags: Array<Tag>): DiscoverySurface {
            fun value(name: String) = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

            return DiscoverySurface(
                name = value(CvmTags.NAME),
                about = value(CvmTags.ABOUT),
                picture = value(CvmTags.PICTURE),
                website = value(CvmTags.WEBSITE),
                supportsEncryption = CvmTags.hasFlag(tags, CvmTags.SUPPORT_ENCRYPTION),
                supportsEphemeralEncryption = CvmTags.hasFlag(tags, CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL),
                supportsOversizedTransfer = CvmTags.hasFlag(tags, CvmTags.SUPPORT_OVERSIZED_TRANSFER),
                supportsOpenStream = CvmTags.hasFlag(tags, CvmTags.SUPPORT_OPEN_STREAM),
                unknownTags =
                    tags.filter {
                        it.isNotEmpty() && it[0] !in KNOWN && !CvmTags.isRouting(it[0])
                    },
            )
        }
    }
}

/**
 * Per-session capability learning (CEP-35).
 *
 * Discovery is a first-message exchange in each direction, not an
 * initialize-only step: a server-to-client message that is not an initialize
 * response may still carry the server's baseline. After that exchange, later
 * feature tags are message-local and do not mutate the baseline unless a
 * feature-specific CEP says they do — CEP-8's `payment_interaction` upsert being
 * the one that does.
 */
class SessionDiscovery {
    private var baseline: DiscoverySurface? = null

    /** The peer's learned baseline, or null before their first message. */
    val peer get() = baseline

    val hasLearned get() = baseline != null

    /**
     * Applies a peer message's tags.
     *
     * The first one establishes the baseline; later ones are returned for
     * message-local interpretation but leave the baseline alone.
     */
    fun observe(tags: Array<Tag>): DiscoverySurface {
        val surface = DiscoverySurface.parse(tags)
        if (baseline == null) baseline = surface
        return surface
    }

    /** Replaces the baseline outright. For a feature CEP that defines an update. */
    fun replaceBaseline(tags: Array<Tag>) {
        baseline = DiscoverySurface.parse(tags)
    }
}

/**
 * A CEP-6 announcement.
 *
 * `content` is the stringified result of the matching MCP call — the initialize
 * result for a server announcement, a list result for the others — so it is left
 * as text for the caller to decode with the same codec it uses on the wire.
 */
data class ServerAnnouncement(
    val kind: Kind,
    val pubKey: HexKey,
    val createdAt: Long,
    val content: String,
    val discovery: DiscoverySurface,
) {
    companion object {
        fun parseOrNull(event: Event): ServerAnnouncement? {
            if (event.kind !in CvmKinds.ANNOUNCEMENTS) return null
            return ServerAnnouncement(
                kind = event.kind,
                pubKey = event.pubKey,
                createdAt = event.createdAt,
                content = event.content,
                discovery = DiscoverySurface.parse(event.tags),
            )
        }

        /**
         * Keeps the newest announcement per `(kind, pubkey)`.
         *
         * These kinds are replaceable, so an older event arriving late from a
         * lagging relay must not overwrite a newer one already held.
         */
        fun latestPerKind(events: List<Event>): Map<Kind, ServerAnnouncement> =
            events
                .mapNotNull { parseOrNull(it) }
                .groupBy { it.kind }
                .mapValues { (_, list) -> list.maxBy { it.createdAt } }
    }
}

/**
 * A relay a server is reachable on (CEP-17, NIP-65 kind 10002).
 *
 * The ContextVM profile publishes unmarked tags, meaning the relay serves both
 * directions. Markers are honoured when present but are not the norm here.
 */
data class ServerRelay(
    val url: String,
    val read: Boolean = true,
    val write: Boolean = true,
) {
    companion object {
        const val READ = "read"
        const val WRITE = "write"

        fun parseAll(event: Event): List<ServerRelay> {
            if (event.kind != CvmKinds.RELAY_LIST) return emptyList()
            return event.tags
                .filter { it.size >= 2 && it[0] == CvmTags.RELAY && it[1].isNotBlank() }
                .map { tag ->
                    when (tag.getOrNull(2)) {
                        READ -> ServerRelay(tag[1], read = true, write = false)
                        WRITE -> ServerRelay(tag[1], read = false, write = true)
                        // Unmarked is the recommended ContextVM profile: the
                        // relay is usable for both publishing and subscribing.
                        else -> ServerRelay(tag[1])
                    }
                }
        }

        /**
         * Relays usable for a full request/response exchange.
         *
         * A read-only or write-only relay cannot carry both halves, and since
         * kind 25910 is ephemeral there is no fetching a response later from
         * somewhere else.
         */
        fun operational(relays: List<ServerRelay>) = relays.filter { it.read && it.write }
    }
}

/** CEP-24 server reviews: NIP-22 comments anchored to a kind-11316 announcement. */
object ServerReview {
    /** The addressable coordinate a review targets. */
    fun coordinate(serverPubKey: HexKey) = "${CvmKinds.SERVER_ANNOUNCEMENT}:$serverPubKey:"

    /**
     * Tags for a top-level review.
     *
     * NIP-22 uses uppercase tags for the root and lowercase for the immediate
     * parent; for a top-level comment both are the announcement, hence the
     * apparent duplication.
     */
    fun topLevelTags(
        serverPubKey: HexKey,
        relayHint: String? = null,
        announcementEventId: HexKey? = null,
    ): List<Tag> {
        val coordinate = coordinate(serverPubKey)
        val kind = CvmKinds.SERVER_ANNOUNCEMENT.toString()
        return buildList {
            add(tagOf("A", coordinate, relayHint))
            add(arrayOf("K", kind))
            add(tagOf("P", serverPubKey, relayHint))
            add(tagOf("a", coordinate, relayHint))
            announcementEventId?.let { add(arrayOf("e", it, relayHint ?: "", serverPubKey)) }
            add(arrayOf("k", kind))
            add(tagOf("p", serverPubKey, relayHint))
        }
    }

    /**
     * Tags for a reply to an existing review.
     *
     * The uppercase root stays on the announcement while the lowercase parent
     * moves to the comment being answered — that asymmetry is the whole point of
     * NIP-22's dual tagging and is easy to get wrong.
     */
    fun replyTags(
        serverPubKey: HexKey,
        parentCommentId: HexKey,
        parentAuthor: HexKey,
        relayHint: String? = null,
    ): List<Tag> =
        buildList {
            add(tagOf("A", coordinate(serverPubKey), relayHint))
            add(arrayOf("K", CvmKinds.SERVER_ANNOUNCEMENT.toString()))
            add(tagOf("P", serverPubKey, relayHint))
            add(arrayOf("e", parentCommentId, relayHint ?: "", parentAuthor))
            add(arrayOf("k", CvmKinds.REVIEW.toString()))
            add(tagOf("p", parentAuthor, relayHint))
        }

    private fun tagOf(
        name: String,
        value: String,
        relayHint: String?,
    ): Tag = if (relayHint != null) arrayOf(name, value, relayHint) else arrayOf(name, value)
}
