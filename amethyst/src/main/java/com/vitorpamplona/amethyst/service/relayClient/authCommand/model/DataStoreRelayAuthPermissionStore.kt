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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest

/**
 * Single-file DataStore-backed [RelayAuthPermissionStore]. All per-relay ALLOW/DENY overrides
 * live in one `datastore/relay_auth.preferences_pb` file; a SHA-256 prefix of the URL is the
 * key so the URL itself is safe in the file (stored separately for reverse-lookup in [allDecisions]).
 */
class DataStoreRelayAuthPermissionStore(
    private val filesDir: File,
) : RelayAuthPermissionStore {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val store: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { File(filesDir, "datastore/relay_auth.preferences_pb") },
        )
    }

    override suspend fun loadDecision(relayUrl: String): RelayAuthDecision? {
        val raw = store.data.first()[decisionKey(relayUrl)] ?: return null
        return runCatching { RelayAuthDecision.valueOf(raw) }.getOrNull()
    }

    override suspend fun storeDecision(
        relayUrl: String,
        decision: RelayAuthDecision,
    ) {
        store.edit {
            it[urlKey(relayUrl)] = relayUrl
            it[decisionKey(relayUrl)] = decision.name
        }
    }

    override suspend fun clearDecision(relayUrl: String) {
        store.edit { prefs ->
            prefs.remove(decisionKey(relayUrl))
            pruneUrlIfEmpty(prefs, relayUrl)
        }
    }

    override suspend fun allDecisions(): Map<String, RelayAuthDecision> {
        val prefs = store.data.first()
        val result = mutableMapOf<String, RelayAuthDecision>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (!name.startsWith(DECISION_PREFIX)) continue
            val hash = name.removePrefix(DECISION_PREFIX)
            val url = prefs[stringPreferencesKey("$URL_PREFIX$hash")] ?: continue
            val decision = runCatching { RelayAuthDecision.valueOf(value as String) }.getOrNull() ?: continue
            result[url] = decision
        }
        return result
    }

    override suspend fun recordUse(
        relayUrl: String,
        additions: Map<AuthPurposeKind, Set<String>>,
    ) {
        if (additions.isEmpty()) return

        // Auth is granted again on every reconnect, so avoid a disk write when nothing changed:
        // only persist if a purpose gained a counterparty we haven't stored, or the last-used
        // timestamp is stale enough to be worth refreshing.
        val prefs = store.data.first()
        val now = TimeUtils.now()
        val lastUsed = prefs[lastUsedKey(relayUrl)]?.toLongOrNull() ?: 0L
        val hasNewCounterparty =
            additions.any { (kind, pubkeys) ->
                val existing = prefs[rationaleKey(relayUrl, kind)].toPubkeySet()
                !existing.containsAll(pubkeys) && existing.size < MAX_COUNTERPARTIES_STORED
            }
        if (!hasNewCounterparty && now - lastUsed < LAST_USED_REFRESH_SECS) return

        store.edit { edit ->
            edit[urlKey(relayUrl)] = relayUrl
            edit[lastUsedKey(relayUrl)] = now.toString()
            for ((kind, pubkeys) in additions) {
                if (pubkeys.isEmpty()) continue
                val key = rationaleKey(relayUrl, kind)
                val existing = edit[key].toPubkeySet()
                if (existing.size >= MAX_COUNTERPARTIES_STORED || existing.containsAll(pubkeys)) continue
                // Cap the stored set: an outbox relay can serve a large slice of the follow list and
                // we only need a representative sample to explain the grant in settings.
                edit[key] = (existing + pubkeys).take(MAX_COUNTERPARTIES_STORED).joinToString(SEPARATOR)
            }
        }
    }

    override suspend fun clearRationale(relayUrl: String) {
        store.edit { prefs ->
            AuthPurposeKind.entries.forEach { prefs.remove(rationaleKey(relayUrl, it)) }
            pruneUrlIfEmpty(prefs, relayUrl)
        }
    }

    override suspend fun allLastUsed(): Map<String, Long> {
        val prefs = store.data.first()
        val result = mutableMapOf<String, Long>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (!name.startsWith(LAST_USED_PREFIX)) continue
            val hash = name.removePrefix(LAST_USED_PREFIX)
            val url = prefs[stringPreferencesKey("$URL_PREFIX$hash")] ?: continue
            val ts = (value as? String)?.toLongOrNull() ?: continue
            result[url] = ts
        }
        return result
    }

    /** Removes the shared url + last-used keys once a relay has neither an override nor rationale,
     *  so a partial clear never orphans the reverse-lookup other queries depend on. */
    private fun pruneUrlIfEmpty(
        prefs: MutablePreferences,
        relayUrl: String,
    ) {
        val hasDecision = prefs[decisionKey(relayUrl)] != null
        val hasRationale = AuthPurposeKind.entries.any { prefs[rationaleKey(relayUrl, it)] != null }
        if (!hasDecision && !hasRationale) {
            prefs.remove(urlKey(relayUrl))
            prefs.remove(lastUsedKey(relayUrl))
        }
    }

    override suspend fun loadRationale(relayUrl: String): Map<AuthPurposeKind, Set<String>> {
        val prefs = store.data.first()
        return buildMap {
            for (kind in AuthPurposeKind.entries) {
                val pubkeys = prefs[rationaleKey(relayUrl, kind)].toPubkeySet()
                if (pubkeys.isNotEmpty()) put(kind, pubkeys)
            }
        }
    }

    override suspend fun allRationales(): Map<String, Map<AuthPurposeKind, Set<String>>> {
        val prefs = store.data.first()
        val result = mutableMapOf<String, MutableMap<AuthPurposeKind, Set<String>>>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (!name.startsWith(RATIONALE_PREFIX)) continue
            val rest = name.removePrefix(RATIONALE_PREFIX) // "<hash>:<KIND>"
            val hash = rest.substringBefore(':')
            val kind = runCatching { AuthPurposeKind.valueOf(rest.substringAfter(':')) }.getOrNull() ?: continue
            val url = prefs[stringPreferencesKey("$URL_PREFIX$hash")] ?: continue
            val pubkeys = (value as? String).toPubkeySet()
            if (pubkeys.isNotEmpty()) {
                result.getOrPut(url) { mutableMapOf() }[kind] = pubkeys
            }
        }
        return result
    }

    private fun String?.toPubkeySet(): Set<String> = this?.split(SEPARATOR)?.filterTo(mutableSetOf()) { it.isNotEmpty() } ?: emptySet()

    private fun decisionKey(relayUrl: String) = stringPreferencesKey("$DECISION_PREFIX${hash(relayUrl)}")

    private fun urlKey(relayUrl: String) = stringPreferencesKey("$URL_PREFIX${hash(relayUrl)}")

    private fun rationaleKey(
        relayUrl: String,
        kind: AuthPurposeKind,
    ) = stringPreferencesKey("$RATIONALE_PREFIX${hash(relayUrl)}:${kind.name}")

    private fun lastUsedKey(relayUrl: String) = stringPreferencesKey("$LAST_USED_PREFIX${hash(relayUrl)}")

    companion object {
        private const val DECISION_PREFIX = "allow:"
        private const val URL_PREFIX = "url:"
        private const val RATIONALE_PREFIX = "rat:"
        private const val LAST_USED_PREFIX = "used:"
        private const val SEPARATOR = ","

        /** Cap on counterparties remembered per relay+purpose, so a large outbox author set can't
         *  bloat the DataStore file or the settings screen. */
        private const val MAX_COUNTERPARTIES_STORED = 64

        /** Minimum seconds between last-used refreshes when no new counterparty appears. */
        private const val LAST_USED_REFRESH_SECS = 300L

        private fun hash(relayUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(relayUrl.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
