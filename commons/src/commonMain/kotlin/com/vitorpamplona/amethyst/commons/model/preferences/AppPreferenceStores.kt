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
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path

/**
 * The app-wide DataStore files — the ones that belong to the install rather
 * than to an account.
 *
 * [AccountPreferenceStores] is the same idea keyed by npub; this is keyed by
 * file name, because these stores are one-per-app and several of them share a
 * single file under different key prefixes (see [SHARED_SETTINGS]).
 *
 * # Why a holder rather than a `Context` delegate
 *
 * Android's `Context.preferencesDataStore(name)` delegate does this job, but
 * only on Android and only from a `Context`. Taking the root directory as a
 * parameter is what lets the stores themselves live in `commonMain` — every
 * front end says where its data lives: `filesDir` on Android, the app data
 * directory on desktop, a temp folder in tests.
 *
 * # The paths are the delegate's paths
 *
 * `preferencesDataStore(name = "x")` resolves to
 * `filesDir/datastore/x.preferences_pb`, and [file] reproduces that exactly.
 * Wired with `filesDir` as the root, a store moved off the delegate onto this
 * holder opens the file it was already using, so nothing has to be migrated
 * and a rollback finds its data where it left it. Changing [file]'s shape
 * would silently orphan every existing install's settings.
 */
class AppPreferenceStores(
    val rootFilesDir: () -> Path,
) {
    companion object {
        /**
         * The file that UI, Tor, OTS, Namecoin and several smaller settings
         * groups all share, each under its own key prefix (`ui.`, `tor.`, …).
         *
         * One file rather than one per group, which is how it has always been
         * on Android: these are read together at startup, and a DataStore is
         * a whole-file read.
         */
        const val SHARED_SETTINGS = "shared_settings"
    }

    /**
     * One store per file, each on a scope this class owns.
     *
     * DataStore keeps a process-wide registry keyed by file path and refuses a
     * second store on a path that already has a live one. Handing out a new
     * store per call is therefore a crash rather than a waste — it has already
     * happened twice in this codebase — so going through the cache is the
     * point of the class, not an optimisation.
     */
    private class Entry(
        val scope: CoroutineScope,
        val store: DataStore<Preferences>,
    )

    private val storeCache = LargeCache<String, Entry>()

    fun file(name: String): Path = rootFilesDir() / "datastore" / "$name.preferences_pb"

    fun getDataStore(name: String): DataStore<Preferences> =
        storeCache
            .getOrCreate(name) {
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                Entry(
                    scope,
                    PreferenceDataStoreFactory.createWithPath(
                        scope = scope,
                        produceFile = { file(name) },
                    ),
                )
            }.store

    /** The file UI, Tor, OTS, Namecoin and friends share. */
    fun sharedSettings(): DataStore<Preferences> = getDataStore(SHARED_SETTINGS)
}
