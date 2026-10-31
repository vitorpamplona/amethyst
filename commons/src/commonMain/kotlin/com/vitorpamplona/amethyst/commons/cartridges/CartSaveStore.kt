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
package com.vitorpamplona.amethyst.commons.cartridges

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.kiko.player.KikoSaveStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * What games save with `cartdata(id)`: 64 numbers per id (high scores, progress, settings). The
 * id is the game's own name for its save, so two posts of the same game share it, as they do on
 * the original console. Kept for the install, not per account.
 */
class CartSaveStore(
    private val store: DataStore<Preferences>,
) : KikoSaveStore {
    /** Every save, by id. They are 512 characters each, and few games use one. */
    override suspend fun loadAll(): Map<String, IntArray> {
        val prefs = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.first()
        val saves = HashMap<String, IntArray>()
        for ((key, value) in prefs.asMap()) {
            if (!key.name.startsWith(PREFIX) || value !is String) continue
            decode(value)?.let { saves[key.name.removePrefix(PREFIX)] = it }
        }
        return saves
    }

    override suspend fun save(saves: Map<String, IntArray>) {
        store.edit { prefs ->
            for ((id, values) in saves) prefs[stringPreferencesKey(PREFIX + id)] = encode(values)
        }
    }

    companion object {
        private const val PREFIX = "cart_"
        const val VALUES = KikoSaveStore.VALUES

        /** Eight hex digits per number, the raw 16:16 bits. */
        internal fun encode(values: IntArray): String = values.joinToString("") { it.toUInt().toString(16).padStart(8, '0') }

        internal fun decode(text: String): IntArray? {
            if (text.length != VALUES * 8) return null
            val values = IntArray(VALUES)
            for (i in 0 until VALUES) values[i] = text.substring(i * 8, i * 8 + 8).toUIntOrNull(16)?.toInt() ?: return null
            return values
        }
    }
}

/** Where the app keeps what games save. */
fun AppPreferenceStores.cartSaveStore(): CartSaveStore = CartSaveStore(getDataStore("cart_saves"))
