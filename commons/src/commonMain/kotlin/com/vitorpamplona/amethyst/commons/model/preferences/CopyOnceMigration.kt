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

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Copies values from an older store into this one, once, the first time the
 * DataStore is read.
 *
 * Deliberately a copy and not a move. `SharedPreferencesMigration`, the stock
 * implementation, deletes each key it migrates — which is how it knows not to
 * run twice, and which makes the migration a one-way door: a build that rolls
 * back to reading the old store finds the user's settings gone. Here a marker
 * key in the *destination* records that the copy happened, so the source is
 * left untouched and a rollback still works. Deleting the legacy data is a
 * separate decision, taken once the migration has shipped and held.
 *
 * [read] is only called when the migration actually runs, so the cost of
 * opening the legacy store is not paid on every launch.
 *
 * @param markerName key recording, in this store, that the copy has run.
 *   Distinct per migration, so several can run against the same store.
 * @param read the legacy values, already mapped onto this store's keys. A key
 *   absent here is left absent rather than written blank, so it keeps reading
 *   as "unset" and falls back to its default.
 */
class CopyOnceMigration(
    markerName: String,
    private val read: suspend () -> Map<Preferences.Key<String>, String>,
) : DataMigration<Preferences> {
    private val marker = booleanPreferencesKey(markerName)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[marker] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val updated = currentData.toMutablePreferences()

        read().forEach { (key, value) -> updated[key] = value }
        updated[marker] = true

        return updated.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
