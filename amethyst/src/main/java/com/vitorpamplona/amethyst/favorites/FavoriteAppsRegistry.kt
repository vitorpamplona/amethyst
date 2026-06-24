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
package com.vitorpamplona.amethyst.favorites

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

private val Context.favoriteAppsDataStore by preferencesDataStore(name = "favorite_apps")

/**
 * The user's device-local list of [FavoriteApp]s — the single source of truth shared by the bottom
 * bar, the Favorite Apps grid, and the browser launcher. Ordered (the user can reorder); de-duplicated
 * by [FavoriteApp.id].
 *
 * Lives only in the **main process** (the launcher/UI consume it); the keyless `:napplet` sandbox never
 * touches it. An in-memory [StateFlow] is authoritative for the session so Compose can observe it
 * synchronously, with write-through persistence to a DataStore on a background scope. The list is
 * stored as a single JSON array under one key (small, bounded, hand-curated data — no need for one key
 * per entry).
 */
object FavoriteAppsRegistry {
    private val KEY = stringPreferencesKey("favorites")

    private val _favorites = MutableStateFlow<List<FavoriteApp>>(emptyList())
    val favorites: StateFlow<List<FavoriteApp>> = _favorites.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    // Hydration runs async on a background scope, so the user can add/remove before the disk list
    // merges in. [removedBeforeHydration] tombstones any id removed in that window, so the merge can't
    // resurrect a just-deleted favorite from disk.
    @Volatile private var hydrated = false
    private val removedBeforeHydration = ConcurrentHashMap.newKeySet<String>()

    /** Binds the app context and hydrates the on-disk list into [favorites]. Idempotent. */
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            val json = ctx.favoriteAppsDataStore.data.first()[KEY]
            val loaded = if (json != null) decode(json) else emptyList()
            // Don't clobber adds made in this session before hydration finished, and don't resurrect
            // anything the user removed in that same window.
            update { current -> (loaded.filterNot { it.id in removedBeforeHydration } + current).distinctBy { it.id } }
            hydrated = true
            removedBeforeHydration.clear()
        }
    }

    fun isFavorite(id: String): Boolean = _favorites.value.any { it.id == id }

    /** Adds [app] to the end if not already present (by [FavoriteApp.id]). */
    fun add(app: FavoriteApp) = update { current -> if (current.any { it.id == app.id }) current else current + app }

    fun remove(id: String) {
        if (!hydrated) removedBeforeHydration.add(id)
        update { current -> current.filterNot { it.id == id } }
    }

    /** Replaces the whole list, e.g. after a drag-reorder. */
    fun setOrder(newOrder: List<FavoriteApp>) = update { newOrder }

    private inline fun update(transform: (List<FavoriteApp>) -> List<FavoriteApp>) {
        val next = transform(_favorites.value)
        if (next == _favorites.value) return
        _favorites.value = next
        persist(encode(next))
    }

    private fun persist(json: String) {
        val ctx = appContext ?: return
        scope.launch {
            ctx.favoriteAppsDataStore.edit { it[KEY] = json }
        }
    }

    // --- Persistence DTO ------------------------------------------------------------------------
    // A flat, type-tagged record so we serialize one concrete shape instead of relying on
    // polymorphic (sealed) (de)serialization. Mapping to/from the sealed model lives here.

    @Serializable
    private data class Entry(
        val type: String,
        val ref: String,
        val label: String,
        val addedAt: Long,
        val iconUrl: String? = null,
    )

    private fun encode(list: List<FavoriteApp>): String =
        JsonMapper.toJson(
            list.map {
                when (it) {
                    is FavoriteApp.NostrApp -> Entry(TYPE_NOSTR, it.coordinate, it.label, it.addedAt, it.iconUrl)
                    is FavoriteApp.WebUrl -> Entry(TYPE_URL, it.url, it.label, it.addedAt, it.iconUrl)
                }
            },
        )

    private fun decode(json: String): List<FavoriteApp> =
        try {
            JsonMapper.fromJson<List<Entry>>(json).mapNotNull { entry ->
                when (entry.type) {
                    TYPE_NOSTR -> FavoriteApp.NostrApp(entry.ref, entry.label, entry.addedAt, entry.iconUrl)
                    TYPE_URL -> FavoriteApp.WebUrl(entry.ref, entry.label, entry.addedAt, entry.iconUrl)
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.w("FavoriteAppsRegistry", "Failed to decode favorites", e)
            emptyList()
        }

    private const val TYPE_NOSTR = "nostr"
    private const val TYPE_URL = "url"
}
