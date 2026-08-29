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
package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty

/**
 * JVM stand-in for the `Context.preferencesDataStore` property delegate.
 *
 * DataStore itself is multiplatform and needs no stub — only this delegate is
 * Android-only, because it derives the file location from a Context. The
 * multiplatform factory takes a path instead, so this supplies one from the
 * app's own files directory, which is the same place the Android delegate puts
 * it relative to the app sandbox.
 *
 * One store per name per process, as on Android: opening the same DataStore
 * twice corrupts it.
 */
private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

fun preferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    ReadOnlyProperty { context, _ ->
        stores.getOrPut(name) {
            val file = File(context.filesDir, "datastore/$name.preferences_pb")
            file.parentFile?.mkdirs()
            PreferenceDataStoreFactory.createWithPath { file.absolutePath.toPath() }
        }
    }
