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
package com.vitorpamplona.amethyst.service.localStore

import com.vitorpamplona.amethyst.commons.service.BasicBundledInsert
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.addressables.AddressableAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.events.EventAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * On-device SQLite "local relay" that permanently keeps the user-directory
 * events the app needs to render people offline:
 *
 *  - kind 0 (profile metadata) for every user we see
 *  - relay lists (NIP-65, DM, search and the NIP-51 relay lists)
 *  - trusted assertions (NIP-85)
 *
 * These are all replaceable/addressable, so the SQLite store keeps exactly one
 * current version per (kind, pubkey[, d-tag]); the DB stays small even though it
 * never prunes by age. NIP-40 expiration and NIP-09 deletions are still honoured
 * by the store's own triggers.
 *
 * Events arriving from remote relays are written-through here (see
 * [CacheClientConnector]); on startup [hydrate] feeds them back into
 * [com.vitorpamplona.amethyst.model.LocalCache] so users and their notes load
 * before any remote relay connects.
 *
 * A single global DB is used (not per-account): this is public, cross-account
 * data, and both the cache and the relay client are app-wide singletons.
 */
class LocalEventStore(
    private val dbFile: File,
    private val scope: CoroutineScope,
) : AutoCloseable {
    companion object {
        /** Synthetic URL used to tag events that originate from this store. */
        val LOCAL_RELAY_URL = NormalizedRelayUrl("ws://localhost/amethyst-local/")

        /** The only kinds this store keeps. Everything else is ignored. */
        val PERSISTED_KINDS: Set<Int> =
            setOf(
                MetadataEvent.KIND, // 0
                // Relay lists
                AdvertisedRelayListEvent.KIND, // 10002 NIP-65
                ChatMessageRelayListEvent.KIND, // 10050 DM inbox relays
                SearchRelayListEvent.KIND, // 10007
                BlockedRelayListEvent.KIND, // 10006
                RelayFeedsListEvent.KIND, // 10012
                IndexerRelayListEvent.KIND, // 10086
                ProxyRelayListEvent.KIND, // 10087
                BroadcastRelayListEvent.KIND, // 10088
                TrustedRelayListEvent.KIND, // 10089
                // Trusted assertions (NIP-85)
                TrustProviderListEvent.KIND, // 10040
                ContactCardEvent.KIND, // 30382
                EventAssertionEvent.KIND, // 30383
                AddressableAssertionEvent.KIND, // 30384
                ExternalIdAssertionEvent.KIND, // 30385
            )

        fun shouldPersist(event: Event): Boolean = event.kind in PERSISTED_KINDS
    }

    @Volatile
    private var store: EventStore? = null

    // 250ms batching window so a burst of profiles collapses into one transaction.
    private val writeBundler = BasicBundledInsert<Event>(delay = 250, dispatcher = Dispatchers.IO, scope = scope)

    /** Opens (or recreates on corruption) the DB. Safe to call once at boot. */
    suspend fun open() {
        if (store != null) return
        dbFile.parentFile?.mkdirs()
        val path = dbFile.absolutePath
        store =
            try {
                EventStore(dbName = path, relay = LOCAL_RELAY_URL)
            } catch (e: Exception) {
                Log.w("LocalEventStore") { "DB open failed, recreating: ${e.message}" }
                deleteDbFiles(path)
                EventStore(dbName = path, relay = LOCAL_RELAY_URL)
            }

        try {
            store?.deleteExpiredEvents()
        } catch (e: Exception) {
            Log.w("LocalEventStore") { "deleteExpiredEvents failed: ${e.message}" }
        }
    }

    /**
     * Write-through entry point. Called for every event consumed from a remote
     * relay; persists only the allow-listed kinds. Non-blocking — batches and
     * inserts on the store's coroutine scope.
     */
    fun enqueue(event: Event) {
        if (!shouldPersist(event)) return
        val s = store ?: return
        writeBundler.invalidateList(event) { batch ->
            // batchInsert uses per-row savepoints, so a UNIQUE-constraint clash
            // (an older replaceable losing to a newer one) skips that row instead
            // of failing the whole batch.
            s.batchInsert(batch.toList())
        }
    }

    /**
     * Streams every persisted event back into the cache via [onEvent], so users
     * and their relay lists / assertions are available offline. Events were
     * already signature-verified before they were stored.
     */
    suspend fun hydrate(onEvent: (Event) -> Unit) {
        val s = store ?: return
        try {
            s.query<Event>(Filter(kinds = PERSISTED_KINDS.toList())) { event ->
                onEvent(event)
            }
        } catch (e: Exception) {
            Log.w("LocalEventStore") { "Hydration failed: ${e.message}" }
        }
    }

    /** Number of stored events (for diagnostics/logging). */
    suspend fun count(): Int = store?.count(Filter()) ?: 0

    override fun close() {
        try {
            store?.close()
        } catch (e: Exception) {
            Log.w("LocalEventStore") { "Close error: ${e.message}" }
        }
        store = null
    }

    private fun deleteDbFiles(path: String) {
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(path + suffix).delete()
        }
    }
}
