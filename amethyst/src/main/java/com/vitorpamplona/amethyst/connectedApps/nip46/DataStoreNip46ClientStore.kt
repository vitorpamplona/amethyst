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
package com.vitorpamplona.amethyst.connectedApps.nip46

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-file DataStore-backed [Nip46ClientStore]. Every connected client's
 * display + relay info lives in one `datastore/nip46_clients.preferences_pb`
 * file; a SHA-256 prefix of the coordinate is the key so the (already public)
 * coordinate is kept alongside for [all]'s reverse lookup. Fields are stored
 * individually so no serialization library is needed; [relays] is newline-joined.
 */
class DataStoreNip46ClientStore(
    private val filesDir: File,
) : Nip46ClientStore {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val store: DataStore<Preferences> get() = dataStoreFor(File(filesDir, "datastore/nip46_clients.preferences_pb"))

    override suspend fun load(coordinate: String): Nip46ClientInfo? {
        val prefs = store.data.first()
        if (prefs[coordKey(coordinate)] == null) return null
        return Nip46ClientInfo(
            name = prefs[nameKey(coordinate)],
            url = prefs[urlKey(coordinate)],
            image = prefs[imageKey(coordinate)],
            relays = prefs[relaysKey(coordinate)].toRelaySet(),
        )
    }

    override suspend fun store(
        coordinate: String,
        info: Nip46ClientInfo,
    ) {
        store.edit { prefs ->
            prefs[coordKey(coordinate)] = coordinate
            info.name?.let { prefs[nameKey(coordinate)] = it } ?: prefs.remove(nameKey(coordinate))
            info.url?.let { prefs[urlKey(coordinate)] = it } ?: prefs.remove(urlKey(coordinate))
            info.image?.let { prefs[imageKey(coordinate)] = it } ?: prefs.remove(imageKey(coordinate))
            if (info.relays.isNotEmpty()) prefs[relaysKey(coordinate)] = info.relays.joinToString("\n") else prefs.remove(relaysKey(coordinate))
        }
    }

    override suspend fun remove(coordinate: String) {
        store.edit { prefs ->
            prefs.remove(coordKey(coordinate))
            prefs.remove(nameKey(coordinate))
            prefs.remove(urlKey(coordinate))
            prefs.remove(imageKey(coordinate))
            prefs.remove(relaysKey(coordinate))
        }
    }

    override suspend fun all(): Map<String, Nip46ClientInfo> {
        val prefs = store.data.first()
        val result = mutableMapOf<String, Nip46ClientInfo>()
        for ((key, value) in prefs.asMap()) {
            if (!key.name.startsWith(COORD_PREFIX)) continue
            val coordinate = value as? String ?: continue
            result[coordinate] =
                Nip46ClientInfo(
                    name = prefs[nameKey(coordinate)],
                    url = prefs[urlKey(coordinate)],
                    image = prefs[imageKey(coordinate)],
                    relays = prefs[relaysKey(coordinate)].toRelaySet(),
                )
        }
        return result
    }

    private fun String?.toRelaySet(): Set<String> = this?.split("\n")?.filterTo(mutableSetOf()) { it.isNotEmpty() } ?: emptySet()

    private fun coordKey(coordinate: String) = stringPreferencesKey("$COORD_PREFIX${hash(coordinate)}")

    private fun nameKey(coordinate: String) = stringPreferencesKey("name:${hash(coordinate)}")

    private fun urlKey(coordinate: String) = stringPreferencesKey("url:${hash(coordinate)}")

    private fun imageKey(coordinate: String) = stringPreferencesKey("img:${hash(coordinate)}")

    private fun relaysKey(coordinate: String) = stringPreferencesKey("relays:${hash(coordinate)}")

    companion object {
        private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

        private fun dataStoreFor(file: File): DataStore<Preferences> =
            stores.computeIfAbsent(file.absolutePath) {
                PreferenceDataStoreFactory.create(produceFile = { file })
            }

        private const val COORD_PREFIX = "coord:"

        private fun hash(coordinate: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(coordinate.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
