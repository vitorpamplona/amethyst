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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserMetadataCache
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserRelaysCache
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.UserNip05Cache
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.UserStatusCache
import com.vitorpamplona.amethyst.commons.model.nip56Reports.UserReportCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.UserCardsCache
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlin.concurrent.Volatile

interface UserDependencies

/**
 * Lookup capability the [User] needs from the surrounding cache. Kept
 * narrow on purpose — only what the lazy pinned-note accessors require,
 * so test fakes are a one-liner and User stays decoupled from the full
 * `LocalCache` surface.
 */
fun interface UserContext {
    fun addressableNote(addr: Address): Note
}

@Stable
class User(
    val pubkeyHex: String,
    context: UserContext,
) {
    // ============================================================
    // Per-user pinned replaceable notes (kind:10002 / 10050 / 10019)
    // ============================================================
    // Held by strong-reference fields until the User itself is collected.
    // The underlying `LocalCache.addressables` map is WeakReference-backed,
    // so without these strong refs the note shells (and any event loaded
    // into them) could vanish on the next GC even though the event was
    // successfully delivered to the cache. Adding a new pinned kind is a
    // one-liner here.

    val nip65RelayListNote: Note = context.addressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))

    val dmRelayListNote: Note = context.addressableNote(ChatMessageRelayListEvent.createAddress(pubkeyHex))

    val nutzapInfoNote: Note = context.addressableNote(NutzapInfoEvent.createAddress(pubkeyHex))

    // These objects are designed to keep the cache
    // while this user obj is being used anywhere.
    //
    // `@Volatile` + double-checked locking (see [lazyCacheLock]): these are created on demand
    // from BOTH the Compose main thread — every `observeUserInfo`/`observeUserName` composition
    // calls the accessor — and the relay/IO threads consuming events (`updateUserInfo` ->
    // `metadata()`). The plain `field ?: Create().also { field = it }` idiom is not atomic: two
    // threads can both read null, each build a cache, and whoever writes first gets orphaned.
    // A UI flow collected from an orphaned UserMetadataCache never receives the metadata, so
    // that composable is stuck on its `pubkeyDisplayHex()` fallback forever — while a sibling
    // composable that read the surviving instance renders the real name. That is exactly the
    // "group DM title shows npubs while the facepile beside it shows names" symptom.
    @Volatile private var metadata: UserMetadataCache? = null

    @Volatile private var reports: UserReportCache? = null

    @Volatile private var cards: UserCardsCache? = null

    @Volatile private var nip05: UserNip05Cache? = null

    @Volatile private var status: UserStatusCache? = null

    @Volatile private var relays: UserRelaysCache? = null

    fun pubkey() = Hex.decode(pubkeyHex)

    fun pubkeyNpub() = pubkey().toNpub()

    fun pubkeyDisplayHex() = pubkeyNpub().toShortDisplay(5)

    fun dmInboxRelayList() = dmRelayListNote.event as? ChatMessageRelayListEvent

    fun authorRelayList() = nip65RelayListNote.event as? AdvertisedRelayListEvent

    fun nutzapInfo() = nutzapInfoNote.event as? NutzapInfoEvent

    /** True when this user has published a kind:10019 with a P2PK pubkey. */
    fun acceptsNutzaps(): Boolean = nutzapInfo()?.p2pkPubkey() != null

    /** Mints the user has declared accept nutzaps. Empty when no kind:10019. */
    fun nutzapMints(): List<NutzapMintTag> = nutzapInfo()?.mints().orEmpty()

    /** The recipient P2PK pubkey nutzaps to this user must lock to. */
    fun nutzapP2pkPubkey(): String? = nutzapInfo()?.p2pkPubkey()

    fun toNProfile() = NProfile.create(pubkeyHex, relayHints())

    fun outboxRelays() = authorRelayList()?.writeRelaysNorm()

    fun relayHints() = outboxRelays()?.take(3) ?: relays?.mostUsed()?.take(3) ?: emptyList()

    fun inboxRelays() = authorRelayList()?.readRelaysNorm()

    fun dmInboxRelays() = dmInboxRelayList()?.relays()?.ifEmpty { null } ?: inboxRelays()

    /**
     * Strict variant of [dmInboxRelays] that returns ONLY the user's NIP-17
     * inbox relays (kind:10050) and never falls back to the NIP-65 read
     * marker (kind:10002).
     *
     * Per NIP-17 §Publishing, gift wraps MUST land on relays advertised in
     * the recipient's kind:10050 — the NIP-65 read fallback in
     * [dmInboxRelays] is a UI-convenience heuristic that leaks the DM
     * metadata to relays the recipient did not designate for DMs. Any code
     * that decides "can I actually deliver a NIP-17 wrap to this user"
     * should call this strict variant; UI hints and probe-time bootstrap
     * paths may continue to use the lenient one.
     *
     * Returns `null` when the recipient has no published kind:10050 (or an
     * empty one) — callers treat this as "unreachable via NIP-17".
     */
    fun dmInboxRelaysStrict() = dmInboxRelayList()?.relays()?.ifEmpty { null }

    fun bestRelayHint() = authorRelayList()?.writeRelaysNorm()?.firstOrNull() ?: mostUsedNonLocalRelay()

    fun allUsedRelaysOrNull() = relays?.allOrNull()

    fun allUsedRelays() = relays?.allOrNull() ?: emptySet()

    fun mostUsedNonLocalRelay() = relays?.mostUsedNonLocalRelay()

    fun toPTag() = PTag(pubkeyHex, bestRelayHint())

    fun toNostrUri() = "nostr:${toNProfile()}"

    fun toBestDisplayName(): String = metadataOrNull()?.bestName() ?: pubkeyDisplayHex()

    fun profilePicture(): String? = metadataOrNull()?.profilePicture()

    fun lnAddress(): String? = metadataOrNull()?.lnAddress()

    fun addRelayBeingUsed(
        relay: NormalizedRelayUrl,
        eventTime: Long,
    ) = relayState().add(relay, eventTime)

    fun updateUserInfo(
        newUserInfo: UserMetadata,
        metaEvent: MetadataEvent,
    ) {
        newUserInfo.cleanBlankNames()
        newUserInfo.convertLud06toLud16IfNeeded()

        metadata().newMetadata(newUserInfo, metaEvent)
        // doesn't create Nip05 unless needed.
        nip05StateOrNull()?.newMetadata(newUserInfo.nip05, metaEvent.pubKey)
    }

    fun reportsOrNull(): UserReportCache? = reports

    fun reports(): UserReportCache = reports ?: lazyCacheLock.withLock { reports ?: UserReportCache().also { reports = it } }

    fun cardsOrNull(): UserCardsCache? = cards

    fun cards(): UserCardsCache = cards ?: lazyCacheLock.withLock { cards ?: UserCardsCache().also { cards = it } }

    fun metadataOrNull(): UserMetadataCache? = metadata

    fun metadata(): UserMetadataCache = metadata ?: lazyCacheLock.withLock { metadata ?: UserMetadataCache().also { metadata = it } }

    fun nip05StateOrNull(): UserNip05Cache? = nip05

    fun nip05State(): UserNip05Cache =
        nip05 ?: lazyCacheLock.withLock {
            nip05 ?: UserNip05Cache().also {
                nip05 = it
                val meta = metadata().flow.value
                if (meta != null) {
                    it.newMetadata(meta.info.nip05, pubkeyHex)
                }
            }
        }

    fun statusStateOrNull(): UserStatusCache? = status

    fun statusState(): UserStatusCache = status ?: lazyCacheLock.withLock { status ?: UserStatusCache().also { status = it } }

    fun relayStateOrNull(): UserRelaysCache? = relays

    fun relayState(): UserRelaysCache = relays ?: lazyCacheLock.withLock { relays ?: UserRelaysCache().also { relays = it } }

    companion object {
        /**
         * Guards the double-checked lazy creation of every per-user cache above.
         *
         * Shared process-wide on purpose: it is only ever held for the few instructions that
         * allocate a cache object, and one lock costs far less than one per [User] in a store
         * that holds tens of thousands of them. [KmpLock] is reentrant, so `nip05State()`
         * calling `metadata()` under the lock cannot deadlock.
         */
        private val lazyCacheLock = KmpLock()
    }
}

fun Set<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun Set<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()

fun List<User>.toHexes() = map { it.pubkeyHex }

fun List<User>.toHexSet() = mapTo(LinkedHashSet(size)) { it.pubkeyHex }

fun List<User>.toSortedHexes() = map { it.pubkeyHex }.sorted()
