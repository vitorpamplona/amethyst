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
package com.vitorpamplona.amethyst.commons.model.accountLifecycle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.IOException

const val ACCOUNT_LIFECYCLE_STORE = "account_lifecycle"

@Serializable
data class LifecycleAssociationRecord(
    val viewerNamespace: String,
    val identifier: String,
    val subjectPubkey: String,
    val subjectOnly: Boolean = false,
    val selectedRecord: String? = null,
    val result: AccountLifecycleResult,
    val backlinks: List<LifecycleBacklink> = emptyList(),
    val observedAt: Long,
)

interface LifecycleAssociationStore {
    suspend fun load(
        viewerNamespace: String,
        identifier: String,
        subjectPubkey: String,
        subjectOnly: Boolean,
    ): LifecycleAssociationRecord?

    suspend fun forSubject(
        viewerNamespace: String,
        subjectPubkey: String,
    ): List<LifecycleAssociationRecord>

    suspend fun save(record: LifecycleAssociationRecord)
}

/**
 * Bounded, viewer-namespaced storage for signed name associations and the complete record that
 * produced a warning. Values contain public Nostr and Namecoin data only. Backend credentials never
 * enter this model. Compromise warnings are not evicted to make room for ordinary viewing history.
 */
class DataStoreLifecycleAssociationStore(
    private val store: DataStore<Preferences>,
    private val maxRecords: Int = MAX_RECORDS,
) : LifecycleAssociationStore {
    init {
        require(maxRecords > 0) { "maxRecords must be positive" }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(
        viewerNamespace: String,
        identifier: String,
        subjectPubkey: String,
        subjectOnly: Boolean,
    ): LifecycleAssociationRecord? {
        val expectedKey = key(viewerNamespace, identifier, subjectPubkey, subjectOnly)
        val raw = preferences()[stringPreferencesKey(expectedKey)] ?: return null
        return decode(raw)?.takeIf {
            it.viewerNamespace == viewerNamespace &&
                it.identifier == identifier &&
                it.subjectPubkey == subjectPubkey &&
                it.subjectOnly == subjectOnly
        }
    }

    override suspend fun forSubject(
        viewerNamespace: String,
        subjectPubkey: String,
    ): List<LifecycleAssociationRecord> =
        preferences()
            .asMap()
            .values
            .mapNotNull { (it as? String)?.let(::decode) }
            .filter { it.viewerNamespace == viewerNamespace && it.subjectPubkey == subjectPubkey }
            .sortedBy { it.identifier }

    override suspend fun save(record: LifecycleAssociationRecord) {
        val recordKey = key(record.viewerNamespace, record.identifier, record.subjectPubkey, record.subjectOnly)
        store.edit { prefs ->
            val existing =
                prefs.asMap().mapNotNull { (key, value) ->
                    val decoded = (value as? String)?.let(::decode)
                    if (decoded != null && key.name.startsWith(PREFIX)) key.name to decoded else null
                }
            if (existing.none { it.first == recordKey } && existing.size >= maxRecords) {
                val evictable =
                    existing
                        .filter { !it.second.result.isCompromiseWarning }
                        .minByOrNull { it.second.observedAt }
                        ?.first
                if (evictable != null) {
                    prefs.remove(stringPreferencesKey(evictable))
                } else {
                    // A full warning-only store remains bounded. Never discard an older compromise
                    // merely to cache another viewed profile.
                    return@edit
                }
            }
            prefs[stringPreferencesKey(recordKey)] = json.encodeToString(LifecycleAssociationRecord.serializer(), record)
        }
    }

    private suspend fun preferences(): Preferences =
        store.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()

    private fun decode(raw: String): LifecycleAssociationRecord? =
        try {
            json.decodeFromString(LifecycleAssociationRecord.serializer(), raw)
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val PREFIX = "lifecycle.v1."
        const val MAX_RECORDS = 256

        internal fun key(
            viewerNamespace: String,
            identifier: String,
            subjectPubkey: String,
            subjectOnly: Boolean,
        ): String {
            val material = "$viewerNamespace\n$identifier\n$subjectPubkey\n$subjectOnly"
            return PREFIX + sha256(material.encodeToByteArray()).copyOfRange(0, 16).toHexKey()
        }
    }
}

class InMemoryLifecycleAssociationStore(
    private val maxRecords: Int = DataStoreLifecycleAssociationStore.MAX_RECORDS,
) : LifecycleAssociationStore {
    init {
        require(maxRecords > 0) { "maxRecords must be positive" }
    }

    private val mutex = Mutex()
    private val records = mutableMapOf<String, LifecycleAssociationRecord>()

    override suspend fun load(
        viewerNamespace: String,
        identifier: String,
        subjectPubkey: String,
        subjectOnly: Boolean,
    ): LifecycleAssociationRecord? = mutex.withLock { records[DataStoreLifecycleAssociationStore.key(viewerNamespace, identifier, subjectPubkey, subjectOnly)] }

    override suspend fun forSubject(
        viewerNamespace: String,
        subjectPubkey: String,
    ): List<LifecycleAssociationRecord> =
        mutex.withLock {
            records.values
                .filter { it.viewerNamespace == viewerNamespace && it.subjectPubkey == subjectPubkey }
                .sortedBy { it.identifier }
        }

    override suspend fun save(record: LifecycleAssociationRecord) {
        mutex.withLock {
            val recordKey =
                DataStoreLifecycleAssociationStore.key(
                    record.viewerNamespace,
                    record.identifier,
                    record.subjectPubkey,
                    record.subjectOnly,
                )
            if (recordKey !in records && records.size >= maxRecords) {
                val evictable =
                    records.entries
                        .filter { !it.value.result.isCompromiseWarning }
                        .minByOrNull { it.value.observedAt }
                        ?.key
                        ?: return@withLock
                records.remove(evictable)
            }
            records[recordKey] = record
        }
    }
}
