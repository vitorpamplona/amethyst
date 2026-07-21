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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vitorpamplona.amethyst.commons.napplet.NappletStorage
import kotlinx.coroutines.flow.first

private val Context.nappletStorageDataStore by preferencesDataStore(name = "napplet_storage")

/**
 * DataStore-backed [NappletStorage]. Every key is prefixed with the **active account** and then the
 * applet's coordinate, so one napplet's keys can never collide with another's, one account's data is
 * never visible to another, and this store is entirely separate from the app's own preferences.
 *
 * [accountPubKey] is read at call time rather than captured, so switching accounts moves reads and
 * writes to the new namespace with no rebuild — an embedded applet always sees the current account's
 * data and never the previous one's.
 */
class DataStoreNappletStorage(
    private val dataStore: DataStore<Preferences>,
    private val accountPubKey: () -> String,
) : NappletStorage {
    constructor(context: Context, accountPubKey: () -> String) :
        this(context.applicationContext.nappletStorageDataStore, accountPubKey)

    override suspend fun get(
        coordinate: String,
        key: String,
    ): String? = dataStore.data.first()[keyOf(coordinate, key)]

    override suspend fun set(
        coordinate: String,
        key: String,
        value: String,
    ) {
        dataStore.edit { it[keyOf(coordinate, key)] = value }
    }

    override suspend fun remove(
        coordinate: String,
        key: String,
    ) {
        dataStore.edit { it.remove(keyOf(coordinate, key)) }
    }

    override suspend fun keys(coordinate: String): List<String> {
        // Must match keyOf's separator exactly. This filtered on a space while keys are written
        // with NUL, so no key could ever match and keys() always returned an empty list.
        val prefix = prefixOf(coordinate)
        return dataStore.data
            .first()
            .asMap()
            .keys
            .map { it.name }
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }

    /** Account first, then applet: isolates accounts from each other, and applets within an account. */
    private fun prefixOf(coordinate: String) = "${accountPubKey()}\u0000$coordinate\u0000"

    private fun keyOf(
        coordinate: String,
        key: String,
    ) = stringPreferencesKey(prefixOf(coordinate) + key)
}
