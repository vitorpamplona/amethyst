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
package com.vitorpamplona.amethyst.connectedApps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Per-coordinate DataStore-backed [NostrSignerPermissionStore]. One small `.preferences_pb`
 * file per app (keyed by a SHA-256 prefix of the coordinate) so loading or saving one app's
 * permissions never touches another app's data — essential at scale with 1000s of apps.
 *
 * The coordinate is stored inside each file under [KEY_COORDINATE] so [allPolicies] can
 * reverse-map file → coordinate without scanning the filesystem.
 */
class DataStoreNostrSignerPermissionStore(
    private val filesDir: File,
) : NostrSignerPermissionStore {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val cache = LargeCache<String, DataStore<Preferences>>()

    private fun storeFor(coordinate: String): DataStore<Preferences> {
        val file = File(filesDir, "datastore/nsp_${hash(coordinate)}.preferences_pb")
        return cache.getOrCreate(file.absolutePath) {
            PreferenceDataStoreFactory.create(produceFile = { file })
        }
    }

    override suspend fun loadPolicy(coordinate: String): AppSignerPolicy? {
        val raw = storeFor(coordinate).data.first()[KEY_POLICY] ?: return null
        return runCatching { AppSignerPolicy.valueOf(raw) }.getOrNull()
    }

    override suspend fun storePolicy(
        coordinate: String,
        policy: AppSignerPolicy,
    ) {
        storeFor(coordinate).edit {
            it[KEY_COORDINATE] = coordinate
            it[KEY_POLICY] = policy.name
        }
    }

    override suspend fun clearPolicy(coordinate: String) {
        storeFor(coordinate).edit { it.remove(KEY_POLICY) }
    }

    override suspend fun loadOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    ): NostrOpDecision? {
        val raw = storeFor(coordinate).data.first()[opKey(op)] ?: return null
        return runCatching { NostrOpDecision.valueOf(raw) }.getOrNull()
    }

    override suspend fun storeOpDecision(
        coordinate: String,
        op: NostrSignerOp,
        decision: NostrOpDecision,
    ) {
        storeFor(coordinate).edit {
            it[KEY_COORDINATE] = coordinate
            it[opKey(op)] = decision.name
        }
    }

    override suspend fun clearOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    ) {
        storeFor(coordinate).edit { it.remove(opKey(op)) }
    }

    override suspend fun allPolicies(): Map<String, AppSignerPolicy> =
        // Enumerates the datastore directory + reads each file — blocking disk IO, so keep it off the
        // caller's thread (callers invoke this from Compose LaunchedEffects on the main dispatcher).
        withContext(Dispatchers.IO) {
            val dir = File(filesDir, "datastore")
            if (!dir.exists()) return@withContext emptyMap()
            val result = mutableMapOf<String, AppSignerPolicy>()
            for (file in dir.listFiles { f -> f.name.startsWith("nsp_") } ?: emptyArray()) {
                val ds =
                    cache.getOrCreate(file.absolutePath) {
                        PreferenceDataStoreFactory.create(produceFile = { file })
                    }
                val coordinate = ds.data.first()[KEY_COORDINATE] ?: continue
                val policy = loadPolicy(coordinate) ?: continue
                result[coordinate] = policy
            }
            result
        }

    override suspend fun allOpDecisions(coordinate: String): Map<String, NostrOpDecision> {
        val prefs = storeFor(coordinate).data.first()
        val result = mutableMapOf<String, NostrOpDecision>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (!name.startsWith(OP_PREFIX)) continue
            // Skip expiry metadata keys — they end with the expiry suffix
            if (name.endsWith(OP_EXPIRY_SUFFIX)) continue
            val opKey = name.removePrefix(OP_PREFIX)
            val decision = runCatching { NostrOpDecision.valueOf(value as String) }.getOrNull() ?: continue
            result[opKey] = decision
        }
        return result
    }

    override suspend fun loadOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
    ): Long? {
        val raw = storeFor(coordinate).data.first()[opExpiryKey(op)] ?: return null
        return raw.toLongOrNull()
    }

    override suspend fun storeOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
        expiresAt: Long,
    ) {
        storeFor(coordinate).edit { it[opExpiryKey(op)] = expiresAt.toString() }
    }

    override suspend fun clearOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
    ) {
        storeFor(coordinate).edit { it.remove(opExpiryKey(op)) }
    }

    override suspend fun loadLastUsed(coordinate: String): Long? {
        val raw = storeFor(coordinate).data.first()[KEY_LAST_USED] ?: return null
        return raw.toLongOrNull()
    }

    override suspend fun storeLastUsed(
        coordinate: String,
        epochSeconds: Long,
    ) {
        storeFor(coordinate).edit { it[KEY_LAST_USED] = epochSeconds.toString() }
    }

    override suspend fun clearAll(coordinate: String) {
        storeFor(coordinate).edit { it.clear() }
    }

    private fun opKey(op: NostrSignerOp) = stringPreferencesKey("$OP_PREFIX${op.key}")

    private fun opExpiryKey(op: NostrSignerOp) = stringPreferencesKey("$OP_PREFIX${op.key}$OP_EXPIRY_SUFFIX")

    companion object {
        private val KEY_COORDINATE = stringPreferencesKey("coordinate")
        private val KEY_POLICY = stringPreferencesKey("policy")
        private val KEY_LAST_USED = stringPreferencesKey("lastused")
        private const val OP_PREFIX = "op:"
        private const val OP_EXPIRY_SUFFIX = ":exp"

        private fun hash(coordinate: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(coordinate.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
