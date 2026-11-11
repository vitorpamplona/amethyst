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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex

/*
 * What a front page is made of: the desks, the filters that read them, and the three
 * desks whose events get a real destination.
 *
 * Ported from NosFabrica/the-nostr-observer @ 0901b92 (MIT, see ./LICENSE.observer):
 * `generator/.../nostr/Pull.kt` and `DeskLink.kt`, matched to Brainstorm's port
 * (client/src/lib/frontPage/desks.ts) where the two differ.
 */

const val FRONT_PAGE_WINDOW_SECONDS = 24L * 60 * 60

/**
 * The token that opens the search relay to an UNRANKED query. The relay CLOSES every
 * REQ whose `search` carries neither an `observer:<hex>` token nor `include:spam`, so a
 * plain lookup (a kind 0) must say it to be answered at all.
 */
const val INCLUDE_SPAM = "include:spam"

/**
 * The trust floor, and it is NOT redundant with `limit`: at limit=400, adding gte:20
 * replaced 49 of the 400, so the top-N is not a strict top-N by the floor's score. Never
 * applied to the control run, which is the anonymous read the page is compared with.
 */
const val DEFAULT_TRUST_FLOOR = 20

class Desk(
    val key: String,
    val kinds: List<Int>,
    val label: String,
    val limit: Int,
    val keeps: (Event) -> Boolean = { true },
)

fun Event.firstTagValue(name: String): String? = tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1)

fun Event.tagsNamed(name: String): List<Array<String>> = tags.filter { it.isNotEmpty() && it[0] == name }

/**
 * The desks, and why each earns a column. ONE REQ PER DESK: merged into one subscription
 * the control run's anonymous notes would land in the ranked notes and the overlap figure
 * would go to ~100%.
 */
val DESKS: List<Desk> =
    listOf(
        Desk("notes", listOf(1), "Notes", 400),
        Desk("pictures", listOf(20), "Picture posts", 60),
        // A kind 30311 is replaceable and carries a `status`: a finished stream sits in the
        // window looking exactly like a running one (measured: 7 of 18 had ended).
        Desk("live", listOf(30311), "Live now", 30) { (it.firstTagValue("status") ?: "").lowercase() == "live" },
        Desk("polls", listOf(1068), "Polls", 20),
        // NIP-71 moved video to 21/22 from 34235/34236; measured, the old kinds still carry
        // nearly all of it. Asking only for the new ones would print no video.
        Desk("videos", listOf(21, 34235), "Videos", 40),
        Desk("shorts", listOf(22, 34236), "Short videos", 40),
        Desk("files", listOf(1063), "File metadata", 50),
        Desk("highlights", listOf(9802), "Highlights", 50),
        Desk("articles", listOf(30023), "Long-form", 100),
        Desk("classifieds", listOf(30402), "Classifieds", 30),
        Desk("wiki", listOf(30818), "Wiki entries", 30),
        // 31922 is NIP-52's all-day half and 31923 the timed half; one alone drops events.
        Desk("calendar", listOf(31922, 31923), "Calendar events", 100),
        Desk("apps", listOf(32267), "App releases", 30),
        Desk("git", listOf(30617), "Code repositories", 30),
    )

/**
 * A bare `observer:<pk> sort:rank` is a valid NIP-50 query that returns a ranked recency
 * feed: the whole product. A null [observerHex] is the control run, which must say
 * `include:spam` to be answered; `sort:rank` without a resolvable observer silently
 * becomes the anonymous ranking, so nothing gets here without a confirmed lens.
 */
fun deskFilter(
    kinds: List<Int>,
    since: Long,
    until: Long,
    limit: Int,
    observerHex: HexKey?,
    floor: Int,
) = Filter(
    kinds = kinds,
    since = since,
    // BOTH ends: `until` was once carried through and never put into a filter.
    until = until,
    limit = limit,
    search =
        if (observerHex != null) {
            "observer:$observerHex sort:rank filter:rank:gte:$floor"
        } else {
            "$INCLUDE_SPAM sort:rank"
        },
)

/**
 * `keeps` drops an ended stream, and the reader's own posts are not the news: they rank
 * highly through the reader's own web of trust by construction. They stay in the control.
 */
fun Desk.belongs(
    observerHex: HexKey,
    events: List<Event>,
): List<Event> = events.filter { keeps(it) && it.pubKey != observerHex }

/**
 * The same question asked through the reader's ranking and without it. `since` is
 * required: without it the search times out and both sides read as a quiet window. No
 * trust floor, so the probe can SEE the silent fallback to anonymous ranking.
 */
fun rankedProbe(
    observerHex: HexKey?,
    since: Long,
) = Filter(
    kinds = listOf(1),
    since = since,
    search = if (observerHex != null) "observer:$observerHex sort:rank" else "$INCLUDE_SPAM sort:rank",
    limit = 12,
)

private val HEX64 = Regex("^[0-9a-f]{64}$")

fun isEventIdHex(value: String) = HEX64.matches(value)

/** Event id to `nevent1…`, id only: a permalink naming only the event cannot smuggle a relay. */
fun toNevent(hex: String): String {
    val id = hex.lowercase()
    require(isEventIdHex(id)) { "Not an event id" }
    return NEvent.create(id, null, null, emptyList())
}

/** `nevent1…` to event-id hex, or null if it does not decode to an nevent. */
fun fromNevent(value: String): HexKey? {
    val entity = Nip19Parser.uriToRoute(value.trim())?.entity as? NEvent ?: return null
    return entity.hex.lowercase().takeIf { isEventIdHex(it) }
}

class NaddrTarget(
    val kind: Int,
    val pubkey: String,
    val identifier: String,
)

fun fromNaddr(value: String): NaddrTarget? {
    val entity = Nip19Parser.uriToRoute(value.trim())?.entity as? NAddress ?: return null
    return NaddrTarget(entity.kind, entity.author.lowercase(), entity.dTag)
}

fun npubOf(hex: HexKey): String = Hex.decode(hex).toNpub()

/** `npub1…` to pubkey hex, or null if it does not decode to an npub. */
fun fromNpub(value: String): HexKey? {
    val entity = Nip19Parser.uriToRoute(value.trim())?.entity as? NPub ?: return null
    return entity.hex.lowercase().takeIf { isEventIdHex(it) }
}

/*
 * A byline's link to its author. Beyond the Observer, which prints names and links no one:
 * the digest gives the writer event ids and never pubkeys, so the writer names an author by
 * one of their posts ([PROFILE_WRITER]) and resolve turns it into the author's own npub page
 * ([PROFILE_LINK]). Only someone who wrote a post this edition read may be linked.
 */
const val PROFILE_HOST_PATH = "https://jumble.social/users/"
val PROFILE_WRITER = Regex("^https://jumble\\.social/users/([0-9a-fA-F]{64})(?:[/?#].*)?$")
val PROFILE_LINK = Regex("^https://jumble\\.social/users/(npub1[0-9a-zA-Z]+)(?:[/?#].*)?$", RegexOption.IGNORE_CASE)

fun toProfileLink(pubkey: HexKey) = PROFILE_HOST_PATH + npubOf(pubkey)

/** What a person is called when they have published no name. Never hex. */
fun shortNpub(hex: HexKey): String {
    val npub = npubOf(hex)
    return npub.take(10) + "…" + npub.takeLast(4)
}

/**
 * A desk whose events get a real destination. The digest hands the writer
 * [writerUrl] (event id hex on the host) and resolve re-encodes it to the host's naddr
 * page. Only events with a `d` tag qualify.
 */
class DeskLink(
    val kinds: Set<Int>,
    /** The digest's line name (`watch:`), and resolve's name for an encoded link. */
    val line: String,
    val change: ResolveChange.Kind,
    host: String,
    writerPath: String,
    naddrPath: String,
) {
    private val writerPrefix = "https://$host$writerPath"
    private val naddrPrefix = "https://$host$naddrPath"
    val writer = Regex("^" + Regex.escape(writerPrefix) + "([0-9a-fA-F]{64})(?:[/?#].*)?$", RegexOption.IGNORE_CASE)
    val naddr = Regex("^" + Regex.escape(naddrPrefix) + "(naddr1[0-9a-zA-Z]+)(?:[/?#].*)?$", RegexOption.IGNORE_CASE)

    fun writerUrl(eventId: String): String {
        val id = eventId.lowercase()
        require(isEventIdHex(id)) { "Not an event id" }
        return writerPrefix + id
    }

    /**
     * The host's naddr page for [event], or null when it has none: not this desk's kind, no `d`
     * tag, or an address that won't encode (a `d` tag over 255 bytes). Never throws: the corpus
     * is written by anyone, and nothing in resolve or check may fail on it.
     */
    fun canonicalUrlOrNull(event: Event): String? {
        val d = event.firstTagValue("d") ?: return null
        if (event.kind !in kinds || d.encodeToByteArray().size > 255) return null
        val naddr =
            try {
                NAddress.createOrNull(event.kind, event.pubKey, d, emptyList())
            } catch (e: Exception) {
                null
            } ?: return null
        return naddrPrefix + naddr
    }
}

/**
 * Live now to zap.stream, Classifieds to Shopstr, Calendar to njump. Calendar goes to
 * njump because jumble.social has no calendar view and an nevent would freeze one
 * revision of a replaceable event.
 */
val DESK_LINKS: Map<String, DeskLink> =
    linkedMapOf(
        "live" to DeskLink(setOf(30311), "watch", ResolveChange.Kind.STREAM, "zap.stream", "/stream/", "/"),
        "classifieds" to DeskLink(setOf(30402), "listing", ResolveChange.Kind.LISTING, "shopstr.store", "/listing/", "/listing/"),
        "calendar" to DeskLink(setOf(31922, 31923), "calendar", ResolveChange.Kind.CALENDAR, "njump.me", "/", "/"),
    )
