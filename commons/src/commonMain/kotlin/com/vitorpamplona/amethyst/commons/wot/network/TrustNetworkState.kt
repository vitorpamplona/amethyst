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
package com.vitorpamplona.amethyst.commons.wot.network

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.moveOrCopy
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkCodec
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIds
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIndex
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkProgress
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkSyncResult
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.downloadTrustNetwork
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.reconcileTrustNetwork
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.updateTrustNetwork
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** A loaded trust network: who the provider asserts about, and where it came from. */
@Immutable
class TrustNetwork(
    val header: TrustNetworkHeader,
    val index: TrustNetworkIndex,
) {
    fun isFrom(provider: ServiceProviderTag) = header.provider == provider.pubkey && header.relay == provider.relayUrl.url
}

/** Why someone is or is not in the user's network. [isKnown] is null when no network is active. */
enum class TrustVerdict(
    val isKnown: Boolean?,
) {
    /** No network for the current provider: callers behave as if the feature were off. */
    NO_NETWORK(null),
    SELF(true),
    FOLLOW(true),

    /** The provider ranks them at or above the minimum score. */
    TRUSTED(true),

    /** The provider scored them, below the minimum score. */
    BELOW_MIN_SCORE(false),

    /** The provider has no card for them. */
    NOT_IN_NETWORK(false),
}

/** What one sync did: the [kind] that ran, its [result], and whether it replaced the network. */
class TrustNetworkRun(
    val kind: TrustNetworkSyncStatus.Kind,
    val result: TrustNetworkSyncResult?,
    val applied: Boolean,
    val error: String?,
)

/** What the background sync is doing, for the settings screen. */
@Immutable
data class TrustNetworkSyncStatus(
    val running: Kind? = null,
    /** Cards verified so far by the running sync. */
    val verified: Int = 0,
    /** The relay's count for the running sync, when it gave one. */
    val expected: Int? = null,
    /** A cold download is due but waits for an unmetered network. */
    val waitingForUnmetered: Boolean = false,
    /** Why the last sync failed, or null when it succeeded. */
    val lastError: String? = null,
) {
    enum class Kind { DOWNLOAD, UPDATE, FULL_CHECK }
}

/**
 * One account's Web of Trust network: the NIP-85 rank provider's cards, as a
 * [TrustNetworkIndex] kept on disk and in memory, outside `LocalCache`.
 *
 *  - **Active** whenever the account's kind 10040 names a `30382:rank` provider and an index
 *    for that provider is loaded. While inactive every consumer behaves as before the feature
 *    existed, so the first download never empties a feed.
 *  - **Loads at construction**, so a process woken by a push notification can wait on
 *    [awaitLoaded] (tens of ms) instead of letting strangers through.
 *  - **Syncs** on a dedicated client from [clientBuilder]: a cold download when there is no
 *    index for the provider, a small update when the last one is older than [UPDATE_EVERY_SECS],
 *    and a negentropy full check every [FULL_CHECK_EVERY_SECS]. Nothing syncs on its own: the
 *    platform calls [syncIfStale] (on foreground, from a periodic job).
 *
 * Files live in [directory] (the account's own directory, so deleting the account deletes
 * them): `network-v1.bin` (the index, read at startup) and `network-ids-v1.bin` (event ids,
 * read only by syncs).
 */
class TrustNetworkState(
    private val rankProvider: StateFlow<ServiceProviderTag?>,
    val minTrustScore: StateFlow<Int>,
    private val directory: Path?,
    private val clientBuilder: (() -> INostrClient)?,
    private val scope: CoroutineScope,
    private val fileSystem: FileSystem = platformFileSystem,
    /** False while on a metered network: background cold downloads (tens of MB) then wait. */
    private val canDownloadLarge: () -> Boolean = { true },
    /** How long an index on disk outlives an empty provider list at startup. */
    private val providerGraceMs: Long = PROVIDER_GRACE_MS,
    /**
     * Whether a provider change starts the due sync by itself. The apps want that; a one-shot
     * caller (the CLI) turns it off and calls [syncNow].
     */
    private val autoSync: Boolean = true,
) {
    private val _network = MutableStateFlow<TrustNetwork?>(null)

    /** The active network, or null while there is no provider or no index for it yet. */
    val network: StateFlow<TrustNetwork?> = _network.asStateFlow()

    private val _status = MutableStateFlow(TrustNetworkSyncStatus())
    val status: StateFlow<TrustNetworkSyncStatus> = _status.asStateFlow()

    private val loaded = MutableStateFlow(false)

    /** The provider [network] was last matched against, once that has happened. */
    private val appliedProvider = MutableStateFlow<Applied?>(null)

    private class Applied(
        val provider: ServiceProviderTag?,
    )

    /** Set by [expectNewProvider]: the next provider change downloads at once, on any network. */
    private var forceNextDownload = false
    private val syncLock = Mutex()
    private var syncJob: Job? = null

    /** Claimed before a sync starts, so two triggers at once (foreground, worker) start one. */
    @OptIn(ExperimentalAtomicApi::class)
    private val syncing = AtomicBoolean(false)

    private val indexFile = directory?.div(INDEX_FILE)
    private val idsFile = directory?.div(IDS_FILE)

    init {
        scope.launch(Dispatchers.IO) {
            // Load whatever is on disk right away: the provider list may take a moment to
            // resolve, and a push must not wait on that.
            _network.value = readIndex()
            loaded.value = true

            var first = true
            rankProvider.collectLatest { provider ->
                if (provider == null && first) {
                    // At startup the provider flow starts empty before the 10040 (or its backup)
                    // is read. Keep the tentative index for a grace period; a real provider
                    // cancels this wait.
                    delay(providerGraceMs)
                }
                first = false
                onProvider(provider)
            }
        }
    }

    /** True when an index for the current provider is loaded and filtering applies. */
    val isActive: Boolean get() = _network.value != null

    /** True when [pubkey] ranks at or above the minimum score. False when inactive. */
    fun passes(pubkey: HexKey): Boolean = _network.value?.index?.passes(pubkey, minTrustScore.value) ?: false

    fun rankOf(pubkey: HexKey): Int? = _network.value?.index?.rankOf(pubkey)

    fun followersOf(pubkey: HexKey): Int? = _network.value?.index?.followersOf(pubkey)

    /**
     * Why [pubkey] is or is not in [me]'s network, given who [me] follows. The single rule
     * behind every Web of Trust decision (DM tabs, notifications, replies, `amy trust check`).
     */
    fun explain(
        pubkey: HexKey,
        me: HexKey,
        follows: Set<HexKey>,
    ): TrustVerdict {
        val index = _network.value?.index ?: return TrustVerdict.NO_NETWORK
        if (pubkey == me) return TrustVerdict.SELF
        if (pubkey in follows) return TrustVerdict.FOLLOW
        val rank = index.rankOf(pubkey) ?: return TrustVerdict.NOT_IN_NETWORK
        return if (rank >= minTrustScore.value) TrustVerdict.TRUSTED else TrustVerdict.BELOW_MIN_SCORE
    }

    /**
     * Waits until the index file has been read (or [timeoutMs] passes). Returns at once after
     * the first load. For code that runs in a freshly started process, like push handling.
     */
    suspend fun awaitLoaded(timeoutMs: Long = 3_000) {
        if (loaded.value) return
        withTimeoutOrNull(timeoutMs) { loaded.first { it } }
    }

    /**
     * Waits until the index has been read and matched against the current provider, so
     * [network] is final. For one-shot callers; the apps use [awaitLoaded].
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000) {
        withTimeoutOrNull(timeoutMs) { appliedProvider.first { it != null && it.provider == rankProvider.value } }
    }

    private suspend fun onProvider(provider: ServiceProviderTag?) {
        if (provider == null) {
            _network.value = null
            appliedProvider.value = Applied(null)
            return
        }
        val current = _network.value
        if (current == null || !current.isFrom(provider)) {
            _network.value = withContext(Dispatchers.IO) { readIndex()?.takeIf { it.isFrom(provider) } }
        }
        appliedProvider.value = Applied(provider)
        val force = forceNextDownload
        forceNextDownload = false
        if (autoSync) syncIfStale(force)
    }

    /**
     * Call before publishing a new provider from the settings screen: the user is waiting for
     * it, so its first download starts as soon as the new 10040 is seen, even on mobile data.
     */
    fun expectNewProvider() {
        forceNextDownload = true
    }

    /**
     * Starts whatever sync is due, unless one is running: a download when there is no index,
     * an update every [UPDATE_EVERY_SECS], a full check every [FULL_CHECK_EVERY_SECS].
     * [force] runs an update (or the download) now, and lets a download use a metered network.
     */
    fun syncIfStale(force: Boolean = false) {
        val provider = rankProvider.value ?: return
        if (clientBuilder == null || directory == null) return
        if (isSyncing()) return

        val current = _network.value?.takeIf { it.isFrom(provider) }
        val now = TimeUtils.now()
        val kind =
            when {
                current == null -> TrustNetworkSyncStatus.Kind.DOWNLOAD
                now - current.header.lastFullCheck >= FULL_CHECK_EVERY_SECS -> TrustNetworkSyncStatus.Kind.FULL_CHECK
                force || now - current.header.lastUpdate >= UPDATE_EVERY_SECS -> TrustNetworkSyncStatus.Kind.UPDATE
                else -> return
            }

        if (kind == TrustNetworkSyncStatus.Kind.DOWNLOAD && !force && !canDownloadLarge()) {
            _status.update { it.copy(waitingForUnmetered = true) }
            return
        }

        launchSync { runSync(provider, current, kind) }
    }

    /** Suspends until the running sync, if any, finishes. */
    suspend fun awaitIdle() {
        syncJob?.join()
    }

    /** Re-downloads everything for the current provider, discarding the local index. */
    fun redownload() {
        val provider = rankProvider.value ?: return
        if (clientBuilder == null || directory == null) return
        if (isSyncing()) return
        launchSync { runSync(provider, null, TrustNetworkSyncStatus.Kind.DOWNLOAD) }
    }

    /**
     * Runs a sync now and returns what happened: [kind], or whatever is due when null
     * (download, full check or update). For one-shot callers like the CLI; the apps use
     * [syncIfStale]. Returns null when there is no provider, nowhere to store the index, no
     * client, or another sync is running.
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun syncNow(kind: TrustNetworkSyncStatus.Kind? = null): TrustNetworkRun? {
        awaitReady()
        val provider = rankProvider.value ?: return null
        if (clientBuilder == null || directory == null) return null
        if (!syncing.compareAndSet(expectedValue = false, newValue = true)) return null
        try {
            return syncLock.withLock {
                val current = _network.value?.takeIf { it.isFrom(provider) }
                val due =
                    when {
                        kind != null -> kind
                        current == null -> TrustNetworkSyncStatus.Kind.DOWNLOAD
                        TimeUtils.now() - current.header.lastFullCheck >= FULL_CHECK_EVERY_SECS -> TrustNetworkSyncStatus.Kind.FULL_CHECK
                        else -> TrustNetworkSyncStatus.Kind.UPDATE
                    }
                runSync(provider, current.takeUnless { due == TrustNetworkSyncStatus.Kind.DOWNLOAD }, due)
            }
        } finally {
            syncing.store(false)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun isSyncing() = syncing.load()

    @OptIn(ExperimentalAtomicApi::class)
    private fun launchSync(sync: suspend () -> Unit) {
        if (!syncing.compareAndSet(expectedValue = false, newValue = true)) return
        syncJob =
            scope.launch(Dispatchers.IO) {
                try {
                    syncLock.withLock { sync() }
                } finally {
                    syncing.store(false)
                }
            }
    }

    private suspend fun runSync(
        provider: ServiceProviderTag,
        current: TrustNetwork?,
        requested: TrustNetworkSyncStatus.Kind,
    ): TrustNetworkRun {
        val builder = clientBuilder ?: return TrustNetworkRun(requested, null, applied = false, error = "no client")
        // An update or full check needs the id column; without it, download again.
        val ids = if (current != null) readIds()?.takeIf { it.size == current.index.size } else null
        val kind = if (current == null || ids == null) TrustNetworkSyncStatus.Kind.DOWNLOAD else requested

        _status.value = TrustNetworkSyncStatus(running = kind)
        val progress =
            TrustNetworkProgress { verified, expected ->
                _status.update { it.copy(verified = verified, expected = expected) }
            }

        val client = builder()
        try {
            client.connect()
            val result: TrustNetworkSyncResult? =
                when (kind) {
                    TrustNetworkSyncStatus.Kind.DOWNLOAD -> {
                        client.downloadTrustNetwork(provider.pubkey, provider.relayUrl, progress)
                    }

                    TrustNetworkSyncStatus.Kind.UPDATE -> {
                        client.updateTrustNetwork(current!!.header, current.index, ids!!, provider.relayUrl, progress)
                    }

                    TrustNetworkSyncStatus.Kind.FULL_CHECK -> {
                        client.reconcileTrustNetwork(current!!.header, current.index, ids!!, provider.relayUrl, progress)
                            ?: client.downloadTrustNetwork(provider.pubkey, provider.relayUrl, progress)
                    }
                }

            if (result == null || !result.complete) {
                // A partial walk must not become the network (a cold one would wrongly reject
                // everyone it missed), nor advance the cursor past what it skipped.
                val error = result?.detail ?: "incomplete"
                _status.value = TrustNetworkSyncStatus(lastError = error)
                return TrustNetworkRun(kind, result, applied = false, error = error)
            }

            if (kind == TrustNetworkSyncStatus.Kind.DOWNLOAD && result.index.size == 0) {
                // A provider still computing a new user's scores has published nothing yet.
                // An empty network would leave only follows as "known", so keep waiting.
                _status.value = TrustNetworkSyncStatus(lastError = NO_SCORES_YET)
                return TrustNetworkRun(kind, result, applied = false, error = NO_SCORES_YET)
            }

            // The provider may have changed while we were downloading.
            if (rankProvider.value != provider) {
                _status.value = TrustNetworkSyncStatus()
                return TrustNetworkRun(kind, result, applied = false, error = "provider changed")
            }

            writeFiles(result.header, result.index, result.ids)
            _network.value = TrustNetwork(result.header, result.index)
            _status.value = TrustNetworkSyncStatus()
            Log.d(TAG) { "$kind done: ${result.index.size} entries, ${result.received} received, ${result.invalid} invalid (${result.detail})" }
            return TrustNetworkRun(kind, result, applied = true, error = null)
        } catch (e: CancellationException) {
            _status.value = TrustNetworkSyncStatus()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Trust network $kind failed for ${provider.relayUrl.url}", e)
            val error = e.message ?: e::class.simpleName
            _status.value = TrustNetworkSyncStatus(lastError = error)
            return TrustNetworkRun(kind, null, applied = false, error = error)
        } finally {
            client.close()
        }
    }

    private fun readIndex(): TrustNetwork? {
        val file = indexFile ?: return null
        return try {
            if (!fileSystem.exists(file)) return null
            val bytes = fileSystem.read(file) { readByteArray() }
            TrustNetworkCodec.decodeIndex(bytes)?.let { (header, index) -> TrustNetwork(header, index) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the trust network index", e)
            null
        }
    }

    private fun readIds(): TrustNetworkIds? {
        val file = idsFile ?: return null
        return try {
            if (!fileSystem.exists(file)) return null
            TrustNetworkCodec.decodeIds(fileSystem.read(file) { readByteArray() })
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the trust network ids", e)
            null
        }
    }

    private fun writeFiles(
        header: TrustNetworkHeader,
        index: TrustNetworkIndex,
        ids: TrustNetworkIds,
    ) {
        val dir = directory ?: return
        fileSystem.createDirectories(dir)
        // ids first: an index on disk always has matching ids, or a size mismatch that
        // makes the next sync download again.
        atomicWrite(dir / IDS_FILE, TrustNetworkCodec.encodeIds(ids))
        atomicWrite(dir / INDEX_FILE, TrustNetworkCodec.encodeIndex(header, index))
    }

    private fun atomicWrite(
        file: Path,
        bytes: ByteArray,
    ) {
        val temp = file.parent!! / "${file.name}.tmp"
        fileSystem.write(temp) { write(bytes) }
        fileSystem.moveOrCopy(temp, file)
    }

    companion object {
        private const val TAG = "TrustNetworkState"
        const val INDEX_FILE = "network-v1.bin"
        const val IDS_FILE = "network-ids-v1.bin"

        /**
         * Some providers recompute on demand, and checking costs two COUNTs of a few bytes when
         * nothing changed (see `updateTrustNetwork`), so every app open after 15 minutes asks.
         */
        const val UPDATE_EVERY_SECS = 15 * 60L
        const val FULL_CHECK_EVERY_SECS = 7 * 24 * 60 * 60L
        private const val PROVIDER_GRACE_MS = 5_000L

        /** [TrustNetworkSyncStatus.lastError] when the provider has published no cards yet. */
        const val NO_SCORES_YET = "no-scores-yet"
    }
}
