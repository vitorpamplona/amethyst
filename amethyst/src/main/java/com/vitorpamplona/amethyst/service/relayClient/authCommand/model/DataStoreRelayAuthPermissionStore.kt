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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
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
        store.edit {
            it.remove(decisionKey(relayUrl))
            it.remove(urlKey(relayUrl))
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

    private fun decisionKey(relayUrl: String) = stringPreferencesKey("$DECISION_PREFIX${hash(relayUrl)}")

    private fun urlKey(relayUrl: String) = stringPreferencesKey("$URL_PREFIX${hash(relayUrl)}")

    companion object {
        private const val DECISION_PREFIX = "allow:"
        private const val URL_PREFIX = "url:"

        private fun hash(relayUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(relayUrl.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
