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
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A validated inner chat rumor emitted by a session: its parent [communityId] and
 * [channelIdHex], plus the typed [rumor] (kind 9 message, 1111 reply, 7 reaction,
 * 5 delete, …). The sink lands it in a store keyed by rumor id so the normal
 * reaction/reply/delete/OTS/zap machinery wires up automatically.
 */
typealias ConcordRumorSink = (communityId: HexKey, channelIdHex: HexKey, rumor: Event) -> Unit

/**
 * The result of feeding one wrap to a session's [ConcordCommunitySession.ingest]. It separates
 * "was it ours" from "did it change structure", so only structure-changing wraps bump the session
 * revision (and thus re-derive plane subscriptions). Landing every chat message as a revision bump
 * re-REQs every plane per message and gets the client rate-limited off the relays.
 */
enum class ConcordIngestOutcome {
    /** The wrap is not addressed to any plane this session knows. Keep routing it elsewhere. */
    NOT_MINE,

    /** Ours and applied, but nothing the subscription set / folded structure depends on changed —
     *  a chat/reaction/reply/delete message landing, or a duplicate wrap. Must NOT bump the revision. */
    NON_STRUCTURAL,

    /** Ours and changed structure: a Control-Plane fold (metadata/channels/membership/authority), a
     *  guestbook membership change, or a buffered base-rekey. Bumps the revision. */
    STRUCTURAL,
    ;

    /** True when the wrap belonged to this session (whether or not it changed structure). */
    val claimed get() = this != NOT_MINE
}

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

    /** The Guestbook Plane at this epoch — where member join/leave motions ride (CORD-02 §5). */
    private val guestbookKey: GroupKey = ConcordActions.guestbookPlane(root, communityIdBytes, entry.rootEpoch)

    /**
     * The base-rotation rekey address for the *next* epoch (CORD-06 §2). A member
     * precomputes it from the root they already hold so an inbound Refounding — which
     * delivers the next root here — is received live rather than only on re-open.
     */
    private val nextBaseRekeyKey: GroupKey = ConcordActions.nextBaseRekeyPlane(root, communityIdBytes, entry.rootEpoch)

    /** The Control Plane stream address to subscribe to (known from the entry alone). */
    val controlPlaneAddress: HexKey get() = controlPlaneKey.publicKeyHex

    /** The Guestbook Plane stream address to subscribe to (known from the entry alone). */
    val guestbookAddress: HexKey get() = guestbookKey.publicKeyHex

    /** The next-epoch base-rekey stream address to watch for an inbound Refounding. */
    val nextBaseRekeyAddress: HexKey get() = nextBaseRekeyKey.publicKeyHex

    private val lock = KmpLock()

    // Deduped inbound wraps.
    private val controlWraps = LinkedHashMap<HexKey, Event>()
    private val channelWrapsById = HashMap<HexKey, LinkedHashMap<HexKey, Event>>() // channelIdHex -> (wrapId -> wrap)
    private val guestbookWraps = LinkedHashMap<HexKey, Event>()
    private val baseRekeyWraps = LinkedHashMap<HexKey, Event>()

    // channel plane pubkey -> (channelIdHex, key), refreshed on each control re-fold.
    private var channelKeysByAddress = HashMap<HexKey, Pair<HexKey, GroupKey>>()

    private val _state = MutableStateFlow<ConcordCommunityState?>(null)
    val state: StateFlow<ConcordCommunityState?> = _state

    private val _members = MutableStateFlow<Set<HexKey>>(emptySet())

    /** The live Guestbook membership set (self-signed joins minus later leaves). */
    val members: StateFlow<Set<HexKey>> = _members

    private val _observedAuthors = MutableStateFlow<Set<HexKey>>(emptySet())

    /**
     * Everyone whose decrypted channel message this session has seen (lowercase hex). CORD-02 §5:
     * "an author seen publishing is observably present, auto-included even if their Join never
     * arrived." Most members never send a Guestbook Join, so this is the bulk of the real roster.
     */
    val observedAuthors: StateFlow<Set<HexKey>> = _observedAuthors

    // channelIdHex -> (other member pubkey -> createdAt secs of their latest typing heartbeat).
    private val typingByChannel = HashMap<HexKey, HashMap<HexKey, Long>>()
    private val _typing = MutableStateFlow<Map<HexKey, Map<HexKey, Long>>>(emptyMap())

    /**
     * The latest typing-heartbeat time (createdAt secs) per channel per *other* member (kind 23311,
     * CORD-03). The UI applies its own freshness window and shows those still typing.
     */
    val typing: StateFlow<Map<HexKey, Map<HexKey, Long>>> = _typing

    /**
     * The community's full membership (lowercase hex): everyone who announced on the Guestbook or
     * was seen publishing a channel message ([observedAuthors]), plus the owner and every
     * role-holder, minus the banned. Best-effort — a member who joined without a Guestbook motion,
     * holds no role, and never posted is invisible (key possession leaves no trace), so this is a
     * floor, not a census.
     */
    fun allMembers(): Set<HexKey> {
        val s = _state.value
        val roster = if (s != null) s.authority.roleHolders() + s.ownerPubKey.lowercase() else emptySet()
        val banned = s?.authority?.bannedMembers().orEmpty()
        return (_members.value + _observedAuthors.value + roster) - banned
    }

    /** The size of [allMembers] — the community's true (best-effort) member count. */
    fun memberCount(): Int = allMembers().size

    /** The current Chat Plane addresses to subscribe to, one per folded channel. */
    fun channelAddresses(): Set<HexKey> = lock.withLock { channelKeysByAddress.keys.toSet() }

    /** The Chat Plane stream address for [channelIdHex], once this community has folded that channel (else null). */
    fun channelPlaneAddress(channelIdHex: HexKey): HexKey? = lock.withLock { channelKeysByAddress.entries.firstOrNull { it.value.first == channelIdHex }?.key }

    /** The base-rotation rekey [GroupKey] a member opens an inbound Refounding under. */
    fun nextBaseRekeyKey(): GroupKey = nextBaseRekeyKey

    /** The buffered kind-3303 base-rotation wraps seen at [nextBaseRekeyAddress], for the account to drain. */
    fun pendingBaseRekeyWraps(): List<Event> = lock.withLock { baseRekeyWraps.values.toList() }

    /**
     * Every stream key whose kind-1059 wraps this session reads: the Control Plane plus
     * one per folded channel. These are the identities a NIP-42 relay must see the
     * connection authenticate as (kind 22242) to serve the wraps — a Concord wrap is
     * authored by the stream key and `p`-tagged to a throwaway ephemeral key, so the
     * member is neither author nor recipient and the relay refuses unless we AUTH as the
     * stream key itself.
     *
     * The Guestbook + next-epoch base-rekey planes ([auxStreamKeys]) are intentionally
     * NOT included here: mixing them into the shared control/channel AUTH set starved the
     * subscription on relays that gate a REQ on stream-key AUTH (control stopped folding,
     * channels went empty). They AUTH on their own isolated subscription instead.
     */
    fun streamKeys(): List<GroupKey> =
        lock.withLock {
            listOf(controlPlaneKey) + channelKeysByAddress.values.map { it.second }
        }

    /** The CORD-06 auxiliary plane keys (Guestbook + next base-rekey) for their own isolated AUTH. */
    fun auxStreamKeys(): List<GroupKey> = listOf(guestbookKey, nextBaseRekeyKey)

    /** The community's current Control Plane editions — the input a moderation edition chains onto. */
    fun controlEditions(): List<ControlEdition> = lock.withLock { ConcordActions.controlEditions(controlWraps.values.toList(), controlPlaneKey) }

    /** The raw Control Plane wraps buffered so far — the input a Refounding compacts (CORD-06 §3). */
    fun controlPlaneWraps(): List<Event> = lock.withLock { controlWraps.values.toList() }

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
     * channel's messages. The [ConcordIngestOutcome] tells the caller both whether
     * the wrap was ours and — crucially — whether it changed *structure* (a fold that
     * moves the subscription set / metadata) versus just landing a chat message. Only
     * a [ConcordIngestOutcome.STRUCTURAL] result should bump the session revision;
     * bumping on every message re-derives every plane's REQ per message and rate-limits
     * the relays (they close the plane subs mid-load, so channels appear empty).
     */
    fun ingest(wrap: Event): ConcordIngestOutcome {
        when (wrap.pubKey) {
            controlPlaneAddress -> {
                lock.withLock {
                    if (controlWraps.put(wrap.id, wrap) != null) return ConcordIngestOutcome.NON_STRUCTURAL // dup
                }
                refold()
                return ConcordIngestOutcome.STRUCTURAL
            }
            guestbookAddress -> {
                lock.withLock {
                    if (guestbookWraps.put(wrap.id, wrap) != null) return ConcordIngestOutcome.NON_STRUCTURAL // dup
                }
                refoldGuestbook()
                return ConcordIngestOutcome.STRUCTURAL
            }
            nextBaseRekeyAddress -> {
                // Buffer only — decrypting a base-rotation blob needs the account signer, so the
                // app layer drains [pendingBaseRekeyWraps] with it and authorizes the rotator. That
                // drain runs off the revision tick, so a buffered rekey must bump (rare — a rekey,
                // not a message).
                lock.withLock { baseRekeyWraps[wrap.id] = wrap }
                return ConcordIngestOutcome.STRUCTURAL
            }
            else -> {
                val channelRef = lock.withLock { channelKeysByAddress[wrap.pubKey] } ?: return ConcordIngestOutcome.NOT_MINE
                val (channelIdHex, key) = channelRef
                // An ephemeral wrap on a channel plane is a transient signal (typing) — fold it into
                // the typing state, never into the stored message buffer or the Note sink. The typing
                // UI collects the [typing] StateFlow directly, so this needs no structural revision bump.
                if (wrap.kind == ConcordStreamEnvelope.KIND_WRAP_EPHEMERAL) {
                    ingestTyping(wrap, channelIdHex, key)
                    return ConcordIngestOutcome.NON_STRUCTURAL
                }
                val isNew =
                    lock.withLock {
                        channelWrapsById.getOrPut(channelIdHex) { LinkedHashMap() }.put(wrap.id, wrap) == null
                    }
                // Project only the newly-arrived wrap — the buffer's earlier wraps were already
                // emitted when they landed, so re-decrypting the whole history on every message
                // would be O(history) per message (quadratic over a channel's lifetime). A duplicate
                // re-delivery (isNew == false) is a no-op.
                if (isNew) emitChannelRumors(channelIdHex, key, listOf(wrap))
                // A chat message lands in the feed via [onRumor] → LocalCache, independent of the
                // revision; it changes no plane address, so it must NOT bump (see the storm note above).
                return ConcordIngestOutcome.NON_STRUCTURAL
            }
        }
    }

    private fun ingestTyping(
        wrap: Event,
        channelIdHex: HexKey,
        key: GroupKey,
    ) {
        val rumor = ConcordStreamEnvelope.openOrNull(wrap, key)?.rumor ?: return
        if (!ChannelChat.isTyping(rumor) || !ChannelChat.isBoundTo(rumor, channelIdHex, entry.rootEpoch)) return
        val who = rumor.pubKey.lowercase()
        if (who == myPubKey.lowercase()) return // never show my own typing back to me
        val now = TimeUtils.now()
        // Update the map and publish inside the lock so a concurrent heartbeat on another
        // channel can't publish an older snapshot last and drop this channel's typers.
        lock.withLock {
            val perChannel = typingByChannel.getOrPut(channelIdHex) { HashMap() }
            val prev = perChannel[who]
            // Clamp a peer's heartbeat to our clock: a wildly future-dated createdAt would never
            // fall out of the freshness window below and would block later real heartbeats.
            val stamp = minOf(rumor.createdAt, now)
            if (prev == null || stamp > prev) perChannel[who] = stamp
            perChannel.entries.retainAll { now - it.value <= TYPING_STALE_SECS }
            if (perChannel.isEmpty()) typingByChannel.remove(channelIdHex)
            _typing.value = typingByChannel.mapValues { it.value.toMap() }
        }
    }

    private fun refold() {
        // Read the buffer, fold, re-derive channel keys, and publish state atomically under the
        // lock so a concurrent control wrap can't publish a smaller fold last. Control editions
        // are rare (not per-message), so serializing the fold is cheap.
        val newChannels =
            lock.withLock {
                val wraps = controlWraps.values.toList()
                val folded = ConcordActions.foldCommunity(wraps, controlPlaneKey, entry.owner)

                val prevChannels = channelKeysByAddress.values.mapTo(HashSet()) { it.first }
                val next = HashMap<HexKey, Pair<HexKey, GroupKey>>()
                for (channelIdHex in folded.channels.keys) {
                    val key = ConcordActions.publicChannel(root, channelIdHex.hexToByteArray(), entry.rootEpoch)
                    next[key.publicKeyHex] = channelIdHex to key
                }
                channelKeysByAddress = next
                _state.value = folded
                folded.channels.keys.filterNot { it in prevChannels }
            }

        // Project only channels appearing for the first time. Existing channels' wraps were already
        // emitted incrementally as they arrived (a channel plane is only subscribed after it folds, so
        // a channel's buffer never pre-dates its first fold) — re-projecting all channels on every
        // control edition would be O(channels × history) of redundant decryption.
        for (channelIdHex in newChannels) reprojectChannel(channelIdHex)
    }

    private fun refoldGuestbook() {
        lock.withLock {
            val wraps = guestbookWraps.values.toList()
            _members.value = ConcordActions.guestbookMembers(wraps, guestbookKey)
        }
    }

    private fun reprojectChannel(channelIdHex: HexKey) {
        val key = lock.withLock { channelKeysByAddress.values.firstOrNull { it.first == channelIdHex }?.second } ?: return
        val wraps = lock.withLock { channelWrapsById[channelIdHex]?.values?.toList() } ?: return
        emitChannelRumors(channelIdHex, key, wraps)
    }

    /** Decrypt + validate the given [wraps], hand each bound rumor to the sink, and fold its author into
     *  the observed roster. The sink dedups by rumor id, so re-emitting a wrap is idempotent. */
    private fun emitChannelRumors(
        channelIdHex: HexKey,
        key: GroupKey,
        wraps: List<Event>,
    ) {
        val authors = HashSet<HexKey>()
        ConcordActions.channelRumors(wraps, key, channelIdHex, entry.rootEpoch).forEach { rumor ->
            authors.add(rumor.pubKey.lowercase())
            onRumor(entry.id, channelIdHex, rumor)
        }
        // Every author we just decrypted is observably present (CORD-02 §5), so fold them into the
        // roster even if they never posted a Guestbook Join. Atomic so a concurrent add isn't lost.
        if (authors.isNotEmpty()) {
            _observedAuthors.update { if (it.containsAll(authors)) it else it + authors }
        }
    }

    companion object {
        /** A typing heartbeat is considered current for this many seconds after it's seen. */
        const val TYPING_STALE_SECS = 8L
    }
}
