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
package com.vitorpamplona.amethyst.desktop.relay

import com.vitorpamplona.amethyst.commons.service.BasicBundledInsert
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LocalRelayStore(
    private val scope: CoroutineScope,
) : AutoCloseable {
    companion object {
        val LOCAL_RELAY_URL: NormalizedRelayUrl = NormalizedRelayUrl("ws://localhost/amethyst-local/")

        private fun dbDir(pubKeyHex: String): File = File(System.getProperty("user.home"), ".amethyst/accounts/${pubKeyHex.take(8)}")

        fun dbFile(pubKeyHex: String): File = File(dbDir(pubKeyHex), "events.db")
    }

    private val lock = Any()

    @Volatile
    private var store: EventStore? = null

    @Volatile
    private var currentPubKey: String? = null

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _writesDisabled = MutableStateFlow(false)
    val writesDisabled: StateFlow<Boolean> = _writesDisabled.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _eventCount = MutableStateFlow(0L)
    val eventCount: StateFlow<Long> = _eventCount.asStateFlow()

    private val _dbSizeBytes = MutableStateFlow(0L)
    val dbSizeBytes: StateFlow<Long> = _dbSizeBytes.asStateFlow()

    private val writeBundler =
        BasicBundledInsert<Event>(
            delay = 250,
            dispatcher = Dispatchers.IO,
            scope = scope,
        )

    fun openForAccount(pubKeyHex: String) {
        synchronized(lock) {
            if (currentPubKey == pubKeyHex && store != null) return
            close()
            currentPubKey = pubKeyHex
            val dir = dbDir(pubKeyHex)
            dir.mkdirs()
            val path = File(dir, "events.db").absolutePath
            try {
                store = EventStore(dbName = path, relay = LOCAL_RELAY_URL)
                _lastError.value = null
                refreshStats()
            } catch (e: Exception) {
                Log.w("LocalRelayStore") { "DB open failed, recreating: ${e.message}" }
                try {
                    deleteDbFiles(path)
                    store = EventStore(dbName = path, relay = LOCAL_RELAY_URL)
                    _lastError.value = "Database was recreated: ${e.message}"
                } catch (e2: Exception) {
                    _lastError.value = "Cannot open local store: ${e2.message}"
                    Log.e("LocalRelayStore", "Failed to recreate DB", e2)
                }
            }
        }
    }

    fun enqueue(event: Event) {
        if (!_enabled.value || _writesDisabled.value) return
        val s = store ?: return
        writeBundler.invalidateList(event) { batch ->
            try {
                s.transaction {
                    batch.forEach { insert(it) }
                }
            } catch (e: Exception) {
                Log.w("LocalRelayStore") { "Batch insert failed: ${e.message}" }
            }
        }
    }

    suspend fun hydrate(cache: DesktopLocalCache) {
        val s = store ?: return
        val relay = LOCAL_RELAY_URL
        Log.d("LocalRelayStore") { "Starting hydration..." }

        try {
            // Phase 1: Contact list (kind 3) — need follow list for filtering
            val contactFilter = Filter(kinds = listOf(3), limit = 10)
            s.query<Event>(contactFilter).forEach { event ->
                cache.consume(event, relay, wasVerified = true)
            }

            // Phase 2: Metadata (kind 0) for followed users
            val followed = cache.followedUsers.value
            if (followed.isNotEmpty()) {
                followed.chunked(500).forEach { chunk ->
                    val metaFilter =
                        Filter(
                            kinds = listOf(0),
                            authors = chunk.toList(),
                            limit = chunk.size,
                        )
                    s.query<Event>(metaFilter).forEach { event ->
                        cache.consume(event, relay, wasVerified = true)
                    }
                }
            }

            // Phase 3: Recent content events (last 7 days, max 5000)
            val since = (System.currentTimeMillis() / 1000) - (7 * 24 * 3600)
            val contentFilter =
                Filter(
                    kinds = listOf(1, 6, 7, 16, 1111, 9735),
                    since = since,
                    limit = 5000,
                )
            s.query<Event>(contentFilter).forEach { event ->
                cache.consume(event, relay, wasVerified = true)
            }

            refreshStats()
            Log.d("LocalRelayStore") { "Hydration complete. Events: ${_eventCount.value}" }
        } catch (e: Exception) {
            _lastError.value = "Hydration failed: ${e.message}"
            Log.w("LocalRelayStore") { "Hydration error: ${e.message}" }
        }
    }

    suspend fun deleteExpiredEvents() {
        try {
            store?.deleteExpiredEvents()
        } catch (e: Exception) {
            Log.w("LocalRelayStore") { "deleteExpiredEvents failed: ${e.message}" }
        }
    }

    suspend fun pruneOldEvents(maxAgeDays: Int = 30) {
        try {
            val cutoff = (System.currentTimeMillis() / 1000) - (maxAgeDays.toLong() * 24 * 3600)
            store?.delete(Filter(until = cutoff))
            refreshStats()
        } catch (e: Exception) {
            _lastError.value = "Prune failed: ${e.message}"
        }
    }

    suspend fun vacuum() {
        try {
            store?.store?.vacuum()
            refreshStats()
        } catch (e: Exception) {
            _lastError.value = "Vacuum failed: ${e.message}"
        }
    }

    suspend fun clearAll() {
        val pk = currentPubKey ?: return
        close()
        val path = dbFile(pk).absolutePath
        deleteDbFiles(path)
        openForAccount(pk)
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    fun disableWrites() {
        _writesDisabled.value = true
    }

    fun enableWrites() {
        _writesDisabled.value = false
    }

    fun clearError() {
        _lastError.value = null
    }

    fun refreshStats() {
        val pk = currentPubKey ?: return
        scope.launch(Dispatchers.IO) {
            try {
                _eventCount.value = store?.count(Filter())?.toLong() ?: 0
                _dbSizeBytes.value = dbFile(pk).length()
            } catch (e: Exception) {
                Log.w("LocalRelayStore") { "Stats refresh failed: ${e.message}" }
            }
        }
    }

    fun checkDiskSpace(): Boolean {
        val pk = currentPubKey ?: return true
        val usable = dbFile(pk).parentFile?.usableSpace ?: return true
        val low = usable < 100 * 1024 * 1024 // < 100MB
        if (low && !_writesDisabled.value) {
            disableWrites()
            _lastError.value = "Disk space low (${usable / 1024 / 1024}MB). Writes disabled."
        }
        return !low
    }

    suspend fun exportEvents(outputFile: File) {
        val s = store ?: return
        outputFile.bufferedWriter().use { writer ->
            s.query<Event>(Filter()) { event ->
                writer.write(event.toJson())
                writer.newLine()
            }
        }
    }

    suspend fun importEvents(inputFile: File) {
        val s = store ?: return
        val lines = inputFile.readLines()
        s.transaction {
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val event = Event.fromJson(line)
                        insert(event)
                    } catch (e: Exception) {
                        Log.w("LocalRelayStore") { "Import skip: ${e.message}" }
                    }
                }
            }
        }
        refreshStats()
    }

    override fun close() {
        synchronized(lock) {
            try {
                store?.close()
            } catch (e: Exception) {
                Log.w("LocalRelayStore") { "Close error: ${e.message}" }
            }
            store = null
        }
    }

    private fun deleteDbFiles(path: String) {
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(path + suffix).delete()
        }
    }
}
