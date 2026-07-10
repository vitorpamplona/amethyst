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
import com.vitorpamplona.amethyst.commons.actions.ConcordChatMessage
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The live read-model of one joined Concord community, driven by inbound stream
 * wraps. A screen/ViewModel binds to its flows; a subscription feeds it via
 * [ingest].
 *
 * It holds the community's [entry] (with secrets), derives the Control Plane
 * address up front, and — as control wraps arrive — re-folds the Control Plane
 * into [state] (metadata + channels + authority) and re-derives each channel's
 * Chat Plane address so subsequent channel wraps route to per-channel
 * [messagesFlow]s. This is the stateful counterpart to the pure
 * [ConcordActions]/[ConcordPlaneRegistry] helpers.
 */
class ConcordCommunitySession(
    val entry: ConcordCommunityListEntry,
    val myPubKey: HexKey,
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

    private val messageFlows = HashMap<HexKey, MutableStateFlow<List<ConcordChatMessage>>>()

    /** The current Chat Plane addresses to subscribe to, one per folded channel. */
    fun channelAddresses(): Set<HexKey> = lock.withLock { channelKeysByAddress.keys.toSet() }

    /** A flow of decrypted, ordered messages for the given channel (created on first use). */
    fun messagesFlow(channelIdHex: HexKey): StateFlow<List<ConcordChatMessage>> = lock.withLock { messageFlows.getOrPut(channelIdHex) { MutableStateFlow(emptyList()) } }

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
        val msgs = ConcordActions.channelMessages(wraps, key, channelIdHex, entry.rootEpoch)
        lock.withLock { messageFlows.getOrPut(channelIdHex) { MutableStateFlow(emptyList()) } }.value = msgs
    }
}
