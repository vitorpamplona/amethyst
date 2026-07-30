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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray

/**
 * One address a channel's traffic rides at, together with the [epoch] its wraps are bound to.
 *
 * The epoch travels *with* the address rather than being taken from the community: a public
 * channel's plane is keyed by the `community_root` at the **root** epoch, while a private
 * channel's is keyed by its own delivered key at its **own** channel epoch (CORD-03 §1). Binding
 * a private channel's wraps against the root epoch would reject every one of them.
 */
data class ConcordChannelPlane(
    val channelIdHex: HexKey,
    val epoch: Long,
    val key: GroupKey,
)

/**
 * Every plane coordinate of one channel **this account can actually open**.
 *
 * [reads] is what to subscribe and decrypt, newest epoch first, and always contains [write].
 * A public channel reads across every held root epoch, so history survives a CORD-06 Refounding;
 * a private channel has exactly one, because a per-channel rekey replaces its delivered key
 * rather than accumulating epochs.
 */
data class ConcordChannelPlanes(
    val channelIdHex: HexKey,
    val isPrivate: Boolean,
    /** Where our own new messages are published, and the epoch to stamp them with. */
    val write: ConcordChannelPlane,
    /** Every address to watch, [write] first. */
    val reads: List<ConcordChannelPlane>,
)

/**
 * Resolves a community's channels into the plane coordinates this account can use — the single
 * place that decides which secret addresses a channel.
 *
 * It exists because that decision was previously made independently at six call sites (the plane
 * registry, the subscription planner, the session's re-fold, and three send paths), and every one
 * of them derived from the `community_root`. For a `private: true` channel that is the **wrong
 * secret**: its plane is keyed by a per-member key delivered in the kind-13302 bundle
 * ([ConcordCommunityListEntry.privateChannels]), so deriving from the root put private traffic on
 * an address every member of the community could read and write — and, in the other direction,
 * made a correctly-addressed private channel look permanently empty.
 *
 * A channel whose key we do not hold is **omitted entirely**, matching the reference client: the
 * room cannot be opened, and offering a row that opens nothing is worse than not offering it.
 */
object ConcordChannelPlanner {
    /**
     * Every channel of [state] this account can open, in fold order, followed by any private
     * channel whose key we hold but whose definition has not folded yet (a fresh join sees its
     * private rooms before the Control Plane catches up; the fold's name wins once it lands).
     *
     * A `null` [state] means the Control Plane has not folded, so only bundle-held private
     * channels are known.
     */
    fun channelPlanes(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState?,
    ): List<ConcordChannelPlanes> {
        val privateKeys = privateKeysOf(entry)
        val folded = state?.channels.orEmpty()
        val out = ArrayList<ConcordChannelPlanes>(folded.size + privateKeys.size)

        for ((channelIdHex, channel) in folded) {
            val planes =
                if (channel.definition.private) {
                    privateKeys[channelIdHex]?.let { privatePlanes(channelIdHex, it) }
                } else {
                    publicPlanes(entry, channelIdHex)
                }
            planes?.let { out.add(it) }
        }
        for ((channelIdHex, held) in privateKeys) {
            if (channelIdHex in folded) continue
            out.add(privatePlanes(channelIdHex, held))
        }
        return out
    }

    /**
     * The coordinates of one channel, or null when this account cannot open it — an unheld private
     * channel, or a channel id absent from both the fold and the bundle.
     *
     * Derives only the requested channel's keys, so a send path doesn't pay for the whole community.
     */
    fun channelPlanesFor(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState?,
        channelIdHex: HexKey,
    ): ConcordChannelPlanes? {
        val definition = state?.channels?.get(channelIdHex)?.definition
        val held = privateKeysOf(entry)[channelIdHex]
        return when {
            // Known private, either from the fold or from the bundle alone (fold still catching up).
            definition?.private == true || (definition == null && held != null) ->
                held?.let { privatePlanes(channelIdHex, it) }
            definition != null -> publicPlanes(entry, channelIdHex)
            else -> null
        }
    }

    /** Where a new message on [channelIdHex] must be published, or null if we may not write there. */
    fun writePlane(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState?,
        channelIdHex: HexKey,
    ): ConcordChannelPlane? = channelPlanesFor(entry, state, channelIdHex)?.write

    /**
     * The bundle's delivered private-channel keys by channel id. Last wins: the bundle carries one
     * key per private channel, since a per-channel rekey replaces the entry instead of appending an
     * epoch — which is also why a private channel has a single read plane.
     */
    private fun privateKeysOf(entry: ConcordCommunityListEntry): Map<HexKey, PrivateChannelKey> = entry.privateChannels.associateBy { it.channelId }

    private fun privatePlanes(
        channelIdHex: HexKey,
        held: PrivateChannelKey,
    ): ConcordChannelPlanes {
        val plane =
            ConcordChannelPlane(
                channelIdHex,
                held.epoch,
                ConcordActions.privateChannel(held.key.hexToByteArray(), channelIdHex.hexToByteArray(), held.epoch),
            )
        return ConcordChannelPlanes(channelIdHex, isPrivate = true, write = plane, reads = listOf(plane))
    }

    private fun publicPlanes(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
    ): ConcordChannelPlanes {
        val idBytes = channelIdHex.hexToByteArray()
        val write =
            ConcordChannelPlane(
                channelIdHex,
                entry.rootEpoch,
                ConcordActions.publicChannel(entry.root.hexToByteArray(), idBytes, entry.rootEpoch),
            )
        // Prior epochs the account still holds a root for: a Refounding rotates the root, so
        // pre-refounding history lives on a different address per epoch. Bounded — every covered
        // epoch multiplies the subscription + AUTH footprint by the channel count.
        val older =
            entry.heldRoots
                .sortedByDescending { it.epoch }
                .take(ConcordActions.MAX_BACKFILL_EPOCHS)
                .map { held ->
                    ConcordChannelPlane(
                        channelIdHex,
                        held.epoch,
                        ConcordActions.publicChannel(held.key.hexToByteArray(), idBytes, held.epoch),
                    )
                }
        // heldRoots may or may not include the current root depending on how the entry was written
        // (stranded recovery folds it in), so dedupe by address and keep the write plane first.
        val reads = (listOf(write) + older).distinctBy { it.key.publicKeyHex }
        return ConcordChannelPlanes(channelIdHex, isPrivate = false, write = write, reads = reads)
    }
}
