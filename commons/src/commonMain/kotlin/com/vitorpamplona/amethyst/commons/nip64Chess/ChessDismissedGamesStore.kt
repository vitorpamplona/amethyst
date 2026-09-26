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
package com.vitorpamplona.amethyst.commons.nip64Chess

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * The chess games a user has dismissed from their completed list, per pubkey.
 *
 * Replaces the previous expect/actual trio. DataStore is multiplatform, so one
 * implementation now serves every target — and iOS gains real persistence,
 * where its actual had been an in-memory map standing in until an iosApp
 * module existed.
 *
 * Nothing is carried over from the old per-platform stores: the dismissed list
 * is a convenience, losing it costs a user one re-dismissal, and chess has few
 * enough users that a migration is not worth the code that would carry it.
 */
class ChessDismissedGamesStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        internal fun keyFor(userPubkey: String) = stringSetPreferencesKey("dismissed_$userPubkey")
    }

    suspend fun load(userPubkey: String): Set<String> =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()[keyFor(userPubkey)]
            ?: emptySet()

    /** An empty set removes the key rather than storing an empty one. */
    suspend fun save(
        userPubkey: String,
        ids: Set<String>,
    ) {
        store.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(keyFor(userPubkey)) else prefs[keyFor(userPubkey)] = ids
        }
    }
}
