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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

suspend fun <T> DataStore<Preferences>.getProperty(
    key: Preferences.Key<String>,
    parser: (String) -> T,
    serializer: (T) -> String,
    scope: CoroutineScope,
): UpdatablePropertyFlow<T> =
    UpdatablePropertyFlow<T>(
        flow =
            data
                .catch { e ->
                    if (e is IOException) emit(emptyPreferences()) else throw e
                }.map { prefs ->
                    val value = prefs[key]
                    if (value != null) {
                        parser(value)
                    } else {
                        null
                    }
                },
        update = { newValue ->
            if (newValue != null) {
                val serialized = serializer(newValue)
                if (serialized.isNotBlank()) {
                    edit { prefs ->
                        prefs[key] = serialized
                    }
                } else {
                    edit { prefs ->
                        prefs.remove(key)
                    }
                }
            } else {
                edit { prefs ->
                    prefs.remove(key)
                }
            }
        },
        scope = scope,
    )
