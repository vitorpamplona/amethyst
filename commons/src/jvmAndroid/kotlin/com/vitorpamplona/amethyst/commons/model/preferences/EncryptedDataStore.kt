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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import okio.IOException
import kotlin.io.encoding.Base64

/**
 * A DataStore whose values are encrypted with [SecretEncryption] and stored
 * Base64-encoded, for anything that must not sit in plaintext on disk.
 *
 * Keys stay in the clear — only values are encrypted — so the set of keys an
 * account has is visible even though their contents are not.
 */
class EncryptedDataStore(
    private val store: DataStore<Preferences>,
    private val encryption: SecretEncryption = SecretEncryption(),
    private val scope: CoroutineScope,
) {
    private fun encrypt(value: String): String = Base64.encode(encryption.encrypt(value.encodeToByteArray()))

    private fun decrypt(value: String): String? = encryption.decrypt(Base64.decode(value))?.decodeToString()

    suspend fun remove(key: Preferences.Key<String>) {
        store.edit { prefs -> prefs.remove(key) }
    }

    suspend fun save(
        key: Preferences.Key<String>,
        value: String,
    ) {
        store.edit { prefs -> prefs[key] = encrypt(value) }
    }

    suspend fun get(key: Preferences.Key<String>): String? =
        store.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.firstOrNull()
            ?.get(key)
            ?.let { decrypt(it) }

    fun <T> getProperty(
        key: Preferences.Key<String>,
        parser: (String) -> T,
        serializer: (T) -> String,
    ): UpdatablePropertyFlow<T> =
        UpdatablePropertyFlow(
            flow =
                store.data
                    .catch { e ->
                        if (e is IOException) emit(emptyPreferences()) else throw e
                    }.map { prefs ->
                        prefs[key]?.let { decrypt(it) }?.takeIf { it.isNotBlank() }?.let(parser)
                    },
            update = { newValue ->
                val serialized = newValue?.let(serializer)
                if (serialized != null && serialized.isNotBlank()) {
                    save(key, serialized)
                } else {
                    remove(key)
                }
            },
            scope = scope,
        )
}
