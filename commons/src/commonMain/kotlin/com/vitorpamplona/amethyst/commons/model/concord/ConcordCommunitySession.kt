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
import com.vitorpamplona.quartz.concord.cord04Roles.EditionFold
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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
typealias ConcordRumorSink = (communityId: HexKey, channelIdHex: HexKey, rumor: Event, seenOnRelays: Set<NormalizedRelayUrl>) -> Unit

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
    private val onRumor: ConcordRumorSink = { _, _, _, _ -> },
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

    /**
     * The Control Plane of every **prior** epoch we still hold a root for (address ->
     * key + epoch), newest-held first and bounded like the channel backfill.
     *
     * This is the anti-rollback memory. A CORD-06 Refounding re-wraps one edition per
     * entity at the new epoch and the *rotator* chooses which one, so it can serve v1
     * of a chain that had already reached v2 — restoring a revoked role, clearing a
     * banlist — with every signature genuine. Folding the epochs we still hold roots
     * for gives us the [EntityFloor] each entity must connect to, and because
     * `heldRoots` is already persisted in the kind-13302 community list, that memory
     * survives a process restart without any new storage.
     */
    private val historicalControlKeys: Map<HexKey, Pair<GroupKey, Long>> =
        entry.heldRoots
            .filter { it.epoch < entry.rootEpoch }
            .sortedByDescending { it.epoch }
            .take(ConcordActions.MAX_BACKFILL_EPOCHS)
            .mapNotNull { held ->
                val key = runCatching { ConcordActions.controlPlane(held.key.hexToByteArray(), communityIdBytes, held.epoch) }.getOrNull() ?: return@mapNotNull null
                key.publicKeyHex to (key to held.epoch)
            }.toMap()

    /** The prior-epoch Control Plane addresses to subscribe to, so the rollback floor can be rebuilt. */
    fun historicalControlPlaneAddresses(): Set<HexKey> = historicalControlKeys.keys

    private val lock = KmpLock()

    // Deduped inbound wraps.
    private val controlWraps = LinkedHashMap<HexKey, Event>()

    // Prior-epoch Control Plane address -> (wrapId -> wrap). Kept apart from [controlWraps]: these
    // never join the live fold, they only produce the anti-rollback floor.
    private val historicalControlWraps = HashMap<HexKey, LinkedHashMap<HexKey, Event>>()

    private val channelWrapsById = HashMap<HexKey, LinkedHashMap<HexKey, Event>>() // channelIdHex -> (wrapId -> wrap)
    private val guestbookWraps = LinkedHashMap<HexKey, Event>()
    private val baseRekeyWraps = LinkedHashMap<HexKey, Event>()

    // channel plane pubkey -> (channelIdHex, key), refreshed on each control re-fold.
    private var channelKeysByAddress = HashMap<HexKey, Pair<HexKey, GroupKey>>()

    // Prior-epoch channel plane pubkey -> (channelIdHex, key, epoch), for pre-Refounding history.
    // A CORD-06 Refounding rotates the root per epoch, so older messages live under a different
    // plane per held root; we re-derive those here so historical wraps are subscribed, AUTHed, and
    // decrypted alongside the current epoch. Empty when the account holds no prior roots.
    private var historicalChannelKeysByAddress = HashMap<HexKey, Triple<HexKey, GroupKey, Long>>()

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

    private var memberHarvestStarted = false

    /**
     * Returns true exactly once — for the caller that should run the one-shot full-history member-roster
     * harvest (page every channel's history back to a bounded window so ingest can fold the older posters
     * into [observedAuthors]). Idempotent, so re-opening the members screen never re-pages.
     */
    fun beginMemberHarvest(): Boolean =
        lock.withLock {
            if (memberHarvestStarted) {
                false
            } else {
                memberHarvestStarted = true
                true
            }
        }

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

    /**
     * Every Chat Plane address to subscribe to: one per folded channel at the current epoch, plus
     * each channel's prior-epoch planes we still hold a root for (pre-Refounding history).
     */
    fun channelAddresses(): Set<HexKey> = lock.withLock { channelKeysByAddress.keys + historicalChannelKeysByAddress.keys }

    /** The Chat Plane stream address for [channelIdHex], once this community has folded that channel (else null). */
    fun channelPlaneAddress(channelIdHex: HexKey): HexKey? = lock.withLock { channelKeysByAddress.entries.firstOrNull { it.value.first == channelIdHex }?.key }

    /**
     * Every Chat Plane stream address for [channelIdHex] across epochs: the current one plus each
     * prior-epoch plane we hold a root for. Used by the history pager as the REQ `authors` set so a
     * single backward `until` sweep walks the channel's whole cross-Refounding timeline (older
     * messages have smaller `created_at` regardless of epoch), and "All caught up" means every epoch
     * is drained — not just the current one. Empty until the Control Plane folds the channel.
     */
    fun channelPlaneAddressesAllEpochs(channelIdHex: HexKey): List<HexKey> =
        lock.withLock {
            val current = channelKeysByAddress.entries.firstOrNull { it.value.first == channelIdHex }?.key
            val historical = historicalChannelKeysByAddress.entries.filter { it.value.first == channelIdHex }.map { it.key }
            (listOfNotNull(current) + historical)
        }

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
            listOf(controlPlaneKey) +
                // Prior-epoch Control Planes: the anti-rollback floor is folded from them, so the
                // gated relays must serve their wraps too.
                historicalControlKeys.values.map { it.first } +
                channelKeysByAddress.values.map { it.second } +
                // Prior-epoch channel stream keys so the gated relays serve their older wraps too.
                historicalChannelKeysByAddress.values.map { it.second }
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
    fun ingest(
        wrap: Event,
        seenOnRelays: Set<NormalizedRelayUrl> = emptySet(),
    ): ConcordIngestOutcome {
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
                // A prior-epoch Control Plane wrap: buffer it and re-fold, so the anti-rollback
                // floor rises as the old epochs drain in. Structural — the floor can change the
                // folded state (and therefore the plane set) exactly like a live control wrap.
                if (wrap.pubKey in historicalControlKeys) {
                    lock.withLock {
                        val buffer = historicalControlWraps.getOrPut(wrap.pubKey) { LinkedHashMap() }
                        if (buffer.put(wrap.id, wrap) != null) return ConcordIngestOutcome.NON_STRUCTURAL // dup
                    }
                    refold()
                    return ConcordIngestOutcome.STRUCTURAL
                }
                val current = lock.withLock { channelKeysByAddress[wrap.pubKey] }
                if (current != null) {
                    val (channelIdHex, key) = current
                    return ingestChannelWrap(wrap, channelIdHex, key, entry.rootEpoch, seenOnRelays)
                }
                // A prior-epoch plane (pre-Refounding history). Decrypt with that epoch's key and
                // bind-check against that epoch. Keyed separately from the current buffer so a re-fold
                // (which rebuilds only the current-epoch keys) never re-projects the historical ones.
                val historical = lock.withLock { historicalChannelKeysByAddress[wrap.pubKey] } ?: return ConcordIngestOutcome.NOT_MINE
                val (channelIdHex, key, epoch) = historical
                return ingestChannelWrap(wrap, channelIdHex, key, epoch, seenOnRelays)
            }
        }
    }

    /** Shared channel-wrap ingest for any epoch: typing → typing state, else buffer-dedup + emit. */
    private fun ingestChannelWrap(
        wrap: Event,
        channelIdHex: HexKey,
        key: GroupKey,
        epoch: Long,
        seenOnRelays: Set<NormalizedRelayUrl>,
    ): ConcordIngestOutcome {
        // An ephemeral wrap on a channel plane is a transient signal (typing) — fold it into the
        // typing state, never the stored buffer or the Note sink. Typing is a current-epoch live
        // signal, so a prior-epoch ephemeral (there won't be any — old epochs are frozen) is harmless.
        if (wrap.kind == ConcordStreamEnvelope.KIND_WRAP_EPHEMERAL) {
            ingestTyping(wrap, channelIdHex, key, epoch)
            return ConcordIngestOutcome.NON_STRUCTURAL
        }
        val isNew =
            lock.withLock {
                channelWrapsById.getOrPut(channelIdHex) { LinkedHashMap() }.put(wrap.id, wrap) == null
            }
        // Project only the newly-arrived wrap — the buffer's earlier wraps were already emitted when
        // they landed, so re-decrypting the whole history on every message would be O(history) per
        // message (quadratic over a channel's lifetime). A duplicate re-delivery (isNew == false) is a
        // no-op. A full-history sweep (member-roster harvest) relies on this staying O(1) per wrap.
        if (isNew) emitChannelRumors(channelIdHex, key, epoch, listOf(wrap), seenOnRelays)
        // A chat message lands in the feed via [onRumor] → LocalCache, independent of the revision; it
        // changes no plane address, so it must NOT bump (see the storm note above).
        return ConcordIngestOutcome.NON_STRUCTURAL
    }

    private fun ingestTyping(
        wrap: Event,
        channelIdHex: HexKey,
        key: GroupKey,
        epoch: Long,
    ) {
        val rumor = ConcordStreamEnvelope.openOrNull(wrap, key)?.rumor ?: return
        if (!ChannelChat.isTyping(rumor) || !ChannelChat.isBoundTo(rumor, channelIdHex, epoch)) return
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
                val folded =
                    ConcordCommunityState.fold(
                        ConcordActions.controlEditions(wraps, controlPlaneKey),
                        entry.owner,
                        controlFloorsLocked(),
                    )

                val prevChannels = channelKeysByAddress.values.mapTo(HashSet()) { it.first }
                val next = HashMap<HexKey, Pair<HexKey, GroupKey>>()
                for (channelIdHex in folded.channels.keys) {
                    val key = ConcordActions.publicChannel(root, channelIdHex.hexToByteArray(), entry.rootEpoch)
                    next[key.publicKeyHex] = channelIdHex to key
                }
                channelKeysByAddress = next

                // Re-derive the prior-epoch planes for the same (epoch-invariant) channel ids, so older
                // pre-Refounding history is subscribed/AUTHed/decrypted. Channels are known only after a
                // fold, hence derived here rather than up front.
                val historical = HashMap<HexKey, Triple<HexKey, GroupKey, Long>>()
                for (plane in ConcordActions.historicalChannelPlanes(entry.heldRoots, folded.channels.keys)) {
                    historical[plane.key.publicKeyHex] = Triple(plane.channelIdHex, plane.key, plane.epoch)
                }
                historicalChannelKeysByAddress = historical

                _state.value = folded
                folded.channels.keys.filterNot { it in prevChannels }
            }

        // Project only channels appearing for the first time. Existing channels' wraps were already
        // emitted incrementally as they arrived (a channel plane is only subscribed after it folds, so
        // a channel's buffer never pre-dates its first fold) — re-projecting all channels on every
        // control edition would be O(channels × history) of redundant decryption.
        for (channelIdHex in newChannels) reprojectChannel(channelIdHex)
    }

    /**
     * The per-entity anti-rollback floor: the authority-gated heads of every prior epoch's
     * Control Plane we still hold a root for, folded **oldest epoch first** so each epoch is
     * itself anchored at the one before it and the floor only ever rises.
     *
     * The current epoch must then connect to these heads; an entity whose offered chain cannot
     * reach its floor keeps the state we last folded (see [EditionFold.admissible]) and the
     * refusal is warned. Empty for a fresh joiner (no held roots), which is exactly right — it
     * legitimately has no history and must still accept the compacted head as its baseline.
     *
     * Caller must hold [lock]; a fold reads the wrap buffers.
     */
    private fun controlFloorsLocked(): Map<String, EntityFloor> {
        if (historicalControlKeys.isEmpty()) return emptyMap()
        var floors = emptyMap<String, EntityFloor>()
        for ((address, keyAtEpoch) in historicalControlKeys.entries.sortedBy { it.value.second }) {
            val wraps = historicalControlWraps[address]?.values?.toList() ?: continue
            val editions = ConcordActions.controlEditions(wraps, keyAtEpoch.first)
            if (editions.isEmpty()) continue
            floors = ConcordCommunityState.authorizedHeads(editions, entry.owner, floors)
        }
        return floors
    }

    private fun refoldGuestbook() {
        lock.withLock {
            val wraps = guestbookWraps.values.toList()
            _members.value = ConcordActions.guestbookMembers(wraps, guestbookKey)
        }
    }

    /** Re-decrypts and re-projects a channel's WHOLE wrap buffer at the current epoch. Only for a
     *  re-fold (keys may change). Prior-epoch wraps in the buffer simply won't open under the current
     *  key and are skipped — they were already emitted when they landed (the sink dedups by id). */
    private fun reprojectChannel(channelIdHex: HexKey) {
        val key = lock.withLock { channelKeysByAddress.values.firstOrNull { it.first == channelIdHex }?.second } ?: return
        val wraps = lock.withLock { channelWrapsById[channelIdHex]?.values?.toList() } ?: return
        emitChannelRumors(channelIdHex, key, entry.rootEpoch, wraps)
    }

    /**
     * Decrypts + validates [wraps] on [channelIdHex], hands each bound rumor to the sink (which dedups
     * by rumor id, so re-emitting is idempotent), and folds their authors into [observedAuthors] — every
     * author we decrypt is observably present (CORD-02 §5), a member even without a Guestbook Join.
     */
    private fun emitChannelRumors(
        channelIdHex: HexKey,
        key: GroupKey,
        epoch: Long,
        wraps: List<Event>,
        seenOnRelays: Set<NormalizedRelayUrl> = emptySet(),
    ) {
        val authors = HashSet<HexKey>()
        ConcordActions.channelRumors(wraps, key, channelIdHex, epoch).forEach { rumor ->
            authors.add(rumor.pubKey.lowercase())
            onRumor(entry.id, channelIdHex, rumor, seenOnRelays)
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
