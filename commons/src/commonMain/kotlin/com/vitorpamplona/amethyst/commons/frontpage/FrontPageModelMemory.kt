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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.flow.first

/** The model the reader picked for each AI endpoint: the kind 12000 event stores none. */
interface FrontPageModelMemory {
    suspend fun get(url: String): String?

    suspend fun set(
        url: String,
        model: String,
    )
}

/** In the app-wide shared settings file, under a `frontpage.model.` prefix per endpoint URL. */
class DataStoreFrontPageModelMemory(
    private val stores: AppPreferenceStores,
) : FrontPageModelMemory {
    private val store get() = stores.getDataStore(AppPreferenceStores.SHARED_SETTINGS)

    private fun keyFor(url: String) = stringPreferencesKey("frontpage.model." + url.trim().trimEnd('/').lowercase())

    override suspend fun get(url: String): String? = store.data.first()[keyFor(url)]

    override suspend fun set(
        url: String,
        model: String,
    ) {
        store.edit { it[keyFor(url)] = model }
    }
}
