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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.utils.cache.LargeCache
import okio.Path

/**
 * The per-account, non-secret preference store: one DataStore file per npub,
 * at `<root>/datastore/<npub>.preferences_pb`.
 *
 * The root directory is injected rather than discovered so every front end can
 * say where its data lives — `filesDir` on Android, the app data directory on
 * desktop, a temp folder in tests — and so this class needs no platform API of
 * its own.
 *
 * Built on [PreferenceDataStoreFactory.createWithPath], the okio-based factory,
 * because the `java.io.File` overloads are absent on Apple targets.
 */
class AccountPreferenceStores(
    val rootFilesDir: () -> Path,
) {
    private val storeCache = LargeCache<String, DataStore<Preferences>>()

    fun file(npub: String): Path = rootFilesDir() / "datastore" / "$npub.preferences_pb"

    fun getDataStore(npub: String): DataStore<Preferences> =
        storeCache.getOrCreate(npub) {
            PreferenceDataStoreFactory.createWithPath(produceFile = { file(npub) })
        }

    /**
     * Drops the account's stored preferences.
     *
     * The cached handle goes first: deleting the file under a live DataStore
     * would leave that instance writing the account's settings back out on the
     * next edit, re-creating what this call is meant to erase.
     */
    fun removeAccount(npub: String): Boolean {
        storeCache.remove(npub)
        val path = file(npub)
        if (!platformFileSystem.exists(path)) return false
        platformFileSystem.delete(path)
        return true
    }
}
