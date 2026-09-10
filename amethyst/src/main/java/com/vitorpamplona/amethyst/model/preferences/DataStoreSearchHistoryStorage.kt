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
package com.vitorpamplona.amethyst.model.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.search.SearchHistoryStorage
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Where Android keeps the search history: one `datastore/search_history.preferences_pb` file.
 *
 * The history itself — what it holds, how much of it, in what order — is
 * [com.vitorpamplona.amethyst.commons.search.SearchHistory] in commons, shared with Desktop. This
 * is only the two strings and the file they live in.
 *
 * Device-global rather than per-account, like the drawer's collapse state beside it: what you
 * searched for is a property of this phone, and it is never published to a relay.
 */
class DataStoreSearchHistoryStorage(
    private val filesDir: File,
) : SearchHistoryStorage {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    // DataStore v1 throws if two instances are ever active on the same file, and the search screen
    // is rebuilt per seeded query, so the store is shared per absolute path across the process.
    private val store: DataStore<Preferences> get() = dataStoreFor(File(filesDir, "datastore/search_history.preferences_pb"))

    override suspend fun read(key: String): String? = store.data.first()[stringPreferencesKey(key)]

    override suspend fun write(
        key: String,
        value: String?,
    ) {
        store.edit {
            if (value.isNullOrBlank()) it.remove(stringPreferencesKey(key)) else it[stringPreferencesKey(key)] = value
        }
    }

    companion object {
        private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

        private fun dataStoreFor(file: File): DataStore<Preferences> =
            stores.computeIfAbsent(file.absolutePath) {
                PreferenceDataStoreFactory.create(produceFile = { file })
            }
    }
}
