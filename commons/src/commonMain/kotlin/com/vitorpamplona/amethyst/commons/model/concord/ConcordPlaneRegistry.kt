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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.envelope.OpenedStreamEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray

/** What kind of plane an address belongs to. */
enum class ConcordPlaneKind {
    CONTROL,
    CHANNEL,
}

/** A known Concord plane: its kind, community, optional channel, and the key to open its wraps. */
class ConcordPlane(
    val kind: ConcordPlaneKind,
    val communityId: HexKey,
    val channelId: ConcordChannelId?,
    val key: GroupKey,
)

/** The routed result of opening an inbound wrap that belonged to a known plane. */
class RoutedRumor(
    val plane: ConcordPlane,
    val opened: OpenedStreamEvent,
)

/**
 * Maps derived plane addresses (`group_key.pk`) to the keys that open them, so an
 * inbound kind-1059 wrap can be recognized as Concord traffic and decrypted with
 * the right per-plane key.
 *
 * This is the counterpart to [ConcordChannelListState] on the read path: the list
 * gives the community secrets, and this registry expands them into the concrete
 * plane addresses to watch. Because a Concord wrap's `p` tag is ephemeral, address
 * matching (`wrap.pubkey` → registered plane) is the only way to route it — a
 * non-member never registers the address, so they never decrypt.
 *
 * Control-plane addresses are known from a community entry alone; channel-plane
 * addresses become known only after the Control Plane folds ([registerChannels]).
 * Thread-safe so the ingest path and UI can share one registry.
 */
class ConcordPlaneRegistry {
    private val lock = KmpLock()
    private val planes = HashMap<HexKey, ConcordPlane>()

    /** Registers every joined community's Control Plane address. Idempotent. */
    fun registerControlPlanes(entries: List<ConcordCommunityListEntry>) =
        lock.withLock {
            for (e in entries) {
                val cp = ConcordKeyDerivation.controlPlaneKey(e.root.hexToByteArray(), e.id.hexToByteArray(), e.rootEpoch)
                planes[cp.publicKeyHex] = ConcordPlane(ConcordPlaneKind.CONTROL, e.id, null, cp)
            }
        }

    /** Registers the Chat Plane address of every channel in a folded community [state]. */
    fun registerChannels(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
    ) = lock.withLock {
        val root = entry.root.hexToByteArray()
        for (channelIdHex in state.channels.keys) {
            val ch =
                com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeys
                    .publicChannel(root, channelIdHex.hexToByteArray(), entry.rootEpoch)
            planes[ch.publicKeyHex] = ConcordPlane(ConcordPlaneKind.CHANNEL, entry.id, ConcordChannelId(entry.id, channelIdHex), ch)
        }
    }

    /** True if [pubKeyHex] is a Concord plane address this account can open. */
    fun isKnownPlane(pubKeyHex: HexKey): Boolean = lock.withLock { pubKeyHex in planes }

    fun planeFor(pubKeyHex: HexKey): ConcordPlane? = lock.withLock { planes[pubKeyHex] }

    /**
     * If [wrap] is a kind-1059 event at a registered plane address, opens it and
     * returns the routed rumor; otherwise null (not Concord, or not ours to read).
     */
    fun route(wrap: Event): RoutedRumor? {
        val plane = planeFor(wrap.pubKey) ?: return null
        val opened = ConcordStreamEnvelope.openOrNull(wrap, plane.key) ?: return null
        return RoutedRumor(plane, opened)
    }

    fun clear() = lock.withLock { planes.clear() }
}
