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
import kotlinx.coroutines.launch
import java.io.File

/**
 * On-device SQLite store that permanently keeps the user-directory events the
 * app needs to render people: kind 0 (profile metadata), relay lists (NIP-65,
 * DM, search and the NIP-51 relay lists) and trusted assertions (NIP-85), for
 * every user we see.
 *
 * The [store] is exposed to the relay client as just another relay via
 * [LocalRelayBuilder] +
 * [com.vitorpamplona.quartz.nip01Core.relay.client.single.local.LocalStoreRelayClient],
 * so any [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient]
 * subscription/publish targeting [LOCAL_RELAY_URL] is answered straight from
 * SQLite — no socket, no relay-server engine.
 *
 * It is populated by a write-through from
 * [com.vitorpamplona.amethyst.service.relayClient.CacheClientConnector]: every
 * persistable event arriving from a remote relay is also inserted here. Because
 * all persisted kinds are replaceable/addressable, the store keeps exactly one
 * current version per (kind, pubkey[, d-tag]), so it stays small even though it
 * never prunes by age. NIP-40 expiration and NIP-09 deletions are honoured by
 * the store's own triggers.
 *
 * A single global DB is used (not per-account): this is public, cross-account
 * data, and both the relay client and the cache are app-wide singletons.
 */
class LocalEventStore(
    private val dbFile: File,
    val scope: CoroutineScope,
) : AutoCloseable {
    companion object {
        /** Stable URL clients use to reach this relay. */
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

    // Opened lazily (off the main thread via warmup, or on first relay use).
    private val storeDelegate = lazy { openStore() }
    val store: EventStore by storeDelegate

    // 250ms batching window so a burst of profiles collapses into one transaction.
    private val writeBundler = BasicBundledInsert<Event>(delay = 250, dispatcher = Dispatchers.IO, scope = scope)

    private fun openStore(): EventStore {
        dbFile.parentFile?.mkdirs()
        val path = dbFile.absolutePath
        return try {
            EventStore(dbName = path, relay = LOCAL_RELAY_URL)
        } catch (e: Exception) {
            Log.w("LocalEventStore") { "DB open failed, recreating: ${e.message}" }
            deleteDbFiles(path)
            EventStore(dbName = path, relay = LOCAL_RELAY_URL)
        }
    }

    /** Opens the DB off the main thread before its first relay query/write. */
    fun warmup() {
        scope.launch {
            try {
                store.deleteExpiredEvents()
            } catch (e: Exception) {
                Log.w("LocalEventStore") { "Warmup failed: ${e.message}" }
            }
        }
    }

    /**
     * Write-through entry point. Called for every event consumed from a remote
     * relay; persists only the allow-listed kinds. Non-blocking — batches and
     * inserts on the store's coroutine scope.
     */
    fun enqueue(event: Event) {
        if (!shouldPersist(event)) return
        writeBundler.invalidateList(event) { batch ->
            // batchInsert uses per-row savepoints, so a UNIQUE-constraint clash
            // (an older replaceable losing to a newer one) skips that row instead
            // of failing the whole batch.
            store.batchInsert(batch.toList())
        }
    }

    override fun close() {
        if (storeDelegate.isInitialized()) {
            runCatching { store.close() }
                .onFailure { Log.w("LocalEventStore") { "Close error: ${it.message}" } }
        }
    }

    private fun deleteDbFiles(path: String) {
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(path + suffix).delete()
        }
    }
}
