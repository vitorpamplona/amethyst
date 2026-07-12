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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A validated inner chat rumor emitted by a session: its parent [communityId] and
 * [channelIdHex], plus the typed [rumor] (kind 9 message, 1111 reply, 7 reaction,
 * 5 delete, …). The sink lands it in a store keyed by rumor id so the normal
 * reaction/reply/delete/OTS/zap machinery wires up automatically.
 */
typealias ConcordRumorSink = (communityId: HexKey, channelIdHex: HexKey, rumor: Event) -> Unit

/**
 * The live read-model of one joined Concord community, driven by inbound stream
 * wraps fed via [ingest].
 *
 * It holds the community's [entry] (with secrets), derives the Control Plane
 * address up front, and — as control wraps arrive — re-folds the Control Plane
 * into [state] (metadata + channels + authority) and re-derives each channel's
 * Chat Plane address so subsequent channel wraps decrypt. **It does not store
 * messages itself:** each validated chat rumor is handed to [onRumor], whose
 * platform-side sink lands it in the shared event store (`LocalCache`) as a real
 * Note attached to the channel — so previews, threading, reactions and zaps reuse
 * the same machinery every other chat does. Re-emitting is safe because the sink
 * dedups by rumor id. This is the stateful counterpart to the pure
 * [ConcordActions]/[ConcordPlaneRegistry] helpers.
 */
class ConcordCommunitySession(
    val entry: ConcordCommunityListEntry,
    val myPubKey: HexKey,
    private val onRumor: ConcordRumorSink = { _, _, _ -> },
) {
    private val root = entry.root.hexToByteArray()
    private val communityIdBytes = entry.id.hexToByteArray()

    private val controlPlaneKey: GroupKey = ConcordActions.controlPlane(root, communityIdBytes, entry.rootEpoch)

    /** The Control Plane stream address to subscribe to (known from the entry alone). */
    val controlPlaneAddress: HexKey get() = controlPlaneKey.publicKeyHex

    private val lock = KmpLock()

    // Deduped inbound wraps.
    private val controlWraps = LinkedHashMap<HexKey, Event>()
    private val channelWrapsById = HashMap<HexKey, LinkedHashMap<HexKey, Event>>() // channelIdHex -> (wrapId -> wrap)

    // channel plane pubkey -> (channelIdHex, key), refreshed on each control re-fold.
    private var channelKeysByAddress = HashMap<HexKey, Pair<HexKey, GroupKey>>()

    private val _state = MutableStateFlow<ConcordCommunityState?>(null)
    val state: StateFlow<ConcordCommunityState?> = _state

    /** The current Chat Plane addresses to subscribe to, one per folded channel. */
    fun channelAddresses(): Set<HexKey> = lock.withLock { channelKeysByAddress.keys.toSet() }

    /**
     * Every stream key whose kind-1059 wraps this session reads: the Control Plane plus
     * one per folded channel. These are the identities a NIP-42 relay must see the
     * connection authenticate as (kind 22242) to serve the wraps — a Concord wrap is
     * authored by the stream key and `p`-tagged to a throwaway ephemeral key, so the
     * member is neither author nor recipient and the relay refuses unless we AUTH as the
     * stream key itself.
     */
    fun streamKeys(): List<GroupKey> = lock.withLock { listOf(controlPlaneKey) + channelKeysByAddress.values.map { it.second } }

    /** The community's current Control Plane editions — the input a moderation edition chains onto. */
    fun controlEditions(): List<ControlEdition> = lock.withLock { ConcordActions.controlEditions(controlWraps.values.toList(), controlPlaneKey) }

    /** The Control Plane key, for authoring moderation editions. */
    fun controlPlaneKey(): GroupKey = controlPlaneKey

    /** This account's standing, from the current fold. */
    fun membership(): ConcordMembership {
        val s = _state.value ?: return ConcordMembership.MEMBER
        return ConcordMembership.of(s.authority, myPubKey)
    }

    /**
     * Ingests a stream [wrap]. If it belongs to this community's Control Plane it
     * re-folds; if it belongs to a known channel plane it re-projects that
     * channel's messages. Returns true if the wrap was recognized and applied.
     */
    fun ingest(wrap: Event): Boolean {
        when (wrap.pubKey) {
            controlPlaneAddress -> {
                lock.withLock {
                    if (controlWraps.put(wrap.id, wrap) != null) return true // dup
                }
                refold()
                return true
            }
            else -> {
                val channelRef = lock.withLock { channelKeysByAddress[wrap.pubKey] } ?: return false
                val (channelIdHex, _) = channelRef
                lock.withLock {
                    channelWrapsById.getOrPut(channelIdHex) { LinkedHashMap() }.put(wrap.id, wrap)
                }
                reprojectChannel(channelIdHex)
                return true
            }
        }
    }

    private fun refold() {
        val wraps = lock.withLock { controlWraps.values.toList() }
        val folded = ConcordActions.foldCommunity(wraps, controlPlaneKey, entry.owner)
        _state.value = folded

        // Re-derive channel plane addresses from the fresh fold.
        val next = HashMap<HexKey, Pair<HexKey, GroupKey>>()
        for (channelIdHex in folded.channels.keys) {
            val key = ConcordActions.publicChannel(root, channelIdHex.hexToByteArray(), entry.rootEpoch)
            next[key.publicKeyHex] = channelIdHex to key
        }
        lock.withLock { channelKeysByAddress = next }

        // Any channel wraps already buffered can now project.
        for (channelIdHex in folded.channels.keys) reprojectChannel(channelIdHex)
    }

    private fun reprojectChannel(channelIdHex: HexKey) {
        val key = lock.withLock { channelKeysByAddress.values.firstOrNull { it.first == channelIdHex }?.second } ?: return
        val wraps = lock.withLock { channelWrapsById[channelIdHex]?.values?.toList() } ?: return
        // Decrypt + validate every bound rumor and hand it to the sink. The sink dedups
        // by rumor id, so re-emitting the whole buffer on each fold is idempotent.
        ConcordActions.channelRumors(wraps, key, channelIdHex, entry.rootEpoch).forEach { rumor ->
            onRumor(entry.id, channelIdHex, rumor)
        }
    }
}
