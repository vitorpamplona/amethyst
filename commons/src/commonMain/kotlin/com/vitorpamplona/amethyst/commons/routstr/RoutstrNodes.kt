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
package com.vitorpamplona.amethyst.commons.routstr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.routstr.node.RoutstrDiscovery
import com.vitorpamplona.quartz.routstr.node.RoutstrEndpoint
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * One Routstr node as the picker lists it, from its kind 38421 announcement alone: listing nodes
 * contacts none of them. [httpsUrl] is the address a key is saved under (NIP-FA wants https);
 * [onionUrl] is where calls go when Tor is on.
 */
@Immutable
data class RoutstrNode(
    val pubKey: HexKey,
    val dTag: String,
    val name: String?,
    val about: String?,
    val lastSeen: Long,
    val version: String?,
    val mints: List<String>,
    val models: List<String>,
    val httpsUrl: String?,
    val onionUrl: String?,
) {
    /** `38421:<pubkey>:<d>`, the announcement's address. */
    val address: String get() = "${RoutstrNodeEvent.KIND}:$pubKey:$dTag"

    val hasHttps: Boolean get() = httpsUrl != null
    val hasOnion: Boolean get() = onionUrl != null

    /** A name to show: the announced one, else the https host, else the onion's first characters. */
    val displayName: String
        get() =
            name
                ?: httpsUrl?.let { AgentProviderPresets.hostOf(it) }
                ?: onionUrl?.let { AgentProviderPresets.hostOf(it)?.take(16) + "…" }
                ?: dTag

    /** Announced this week ([RoutstrNodeEvent.MAX_AGE]): nodes re-announce daily, but live ones have been seen a week late. */
    fun isFresh(now: Long = TimeUtils.now()): Boolean = lastSeen >= now - RoutstrNodeEvent.MAX_AGE

    /**
     * Where to call it: the onion when Tor is on and it has one, else https. Null when neither
     * works here (an onion-only node without Tor).
     */
    fun callBase(torEnabled: Boolean): String? = if (torEnabled && onionUrl != null) onionUrl else httpsUrl

    /** The NIP-FA URL a key bought here is saved under: `https://…/v1`. Null for an onion-only node. */
    val keyUrl: String? get() = httpsUrl?.let { RoutstrNodes.keyUrl(it) }

    /** Whether [key]'s URL names this node. */
    fun owns(keyUrl: String): Boolean = RoutstrNodes.sameNode(keyUrl, httpsUrl) || RoutstrNodes.sameNode(keyUrl, onionUrl)

    companion object {
        fun from(event: RoutstrNodeEvent): RoutstrNode {
            val endpoints = event.endpoints()
            return RoutstrNode(
                pubKey = event.pubKey,
                dTag = event.dTag(),
                name = event.name()?.trim()?.ifEmpty { null },
                about = event.about()?.trim()?.ifEmpty { null },
                lastSeen = event.createdAt,
                version = event.version(),
                mints = event.mints().distinct(),
                models = event.models(),
                httpsUrl = endpoints.firstOrNull { it.type == RoutstrEndpoint.Type.HTTPS }?.url?.let(RoutstrNodes::root),
                onionUrl = endpoints.firstOrNull { it.type == RoutstrEndpoint.Type.ONION }?.url?.let(RoutstrNodes::root),
            )
        }
    }
}

/** The picker's nodes: [fresh] (seen this week) first, [stale] (seen this month) behind a button. */
@Immutable
data class RoutstrNodePicks(
    val fresh: List<RoutstrNode>,
    val stale: List<RoutstrNode>,
) {
    fun all(includeStale: Boolean): List<RoutstrNode> = if (includeStale) fresh + stale else fresh
}

object RoutstrNodes {
    /** A node's root: no trailing slash, no `/v1`. */
    fun root(url: String): String =
        url
            .trim()
            .trimEnd('/')
            .removeSuffix("/v1")
            .trimEnd('/')

    fun keyUrl(root: String): String = root(root) + "/v1"

    fun sameNode(
        a: String?,
        b: String?,
    ): Boolean {
        if (a == null || b == null) return false
        return root(a).lowercase() == root(b).lowercase()
    }

    /**
     * The picker's nodes from the announcements: the newest per address, only nodes with an
     * endpoint this client may call ([torEnabled] admits onion-only ones), none older than
     * [LISTED_MAX_AGE] (a month), the freshest first, then by name. [RoutstrNodePicks.stale] are
     * the ones not seen this week, which the picker keeps behind "Show N not seen this week": live
     * nodes that still answer have announced 7.6 days ago.
     */
    fun list(
        events: List<Event>,
        torEnabled: Boolean,
        now: Long = TimeUtils.now(),
    ): RoutstrNodePicks {
        val newest = HashMap<String, RoutstrNodeEvent>()
        events.forEach { event ->
            if (event !is RoutstrNodeEvent) return@forEach
            val address = "${event.pubKey}:${event.dTag()}"
            val held = newest[address]
            if (held == null || event.createdAt > held.createdAt) newest[address] = event
        }
        val listed =
            newest.values
                .asSequence()
                .filter { it.isFresh(now, LISTED_MAX_AGE) }
                .filter { it.usableEndpoints(torEnabled).isNotEmpty() }
                .map(RoutstrNode::from)
                .sortedWith(compareByDescending<RoutstrNode> { it.lastSeen / DAY }.thenBy { it.displayName.lowercase() })
                .toList()
        val (fresh, stale) = listed.partition { it.isFresh(now) }
        return RoutstrNodePicks(fresh, stale)
    }

    /** Nodes not seen for longer than this are not listed at all: a month, in seconds. */
    const val LISTED_MAX_AGE: Long = 30 * 24 * 60 * 60L

    /** Every word of [query] in the node's name, about or address, ignoring case. */
    fun matches(
        node: RoutstrNode,
        query: String,
    ): Boolean {
        val words =
            query
                .trim()
                .lowercase()
                .split(' ')
                .filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = listOfNotNull(node.name, node.about, node.httpsUrl, node.onionUrl).joinToString(" ").lowercase()
        return words.all { it in hay }
    }

    private const val DAY = 24 * 60 * 60L
}

/**
 * The node announcements (kind 38421) on [relays], from the last [maxAge] seconds unless
 * [maxAge] is null: one `fetchAll`, no node contacted.
 */
suspend fun INostrClient.fetchRoutstrNodes(
    relays: List<NormalizedRelayUrl> = RoutstrDiscovery.DEFAULT_RELAYS,
    maxAge: Long? = null,
    idleTimeoutMs: Long = 8_000L,
): List<Event> {
    val filter = RoutstrDiscovery.filter(since = maxAge?.let { TimeUtils.now() - it })
    return fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs)
}
