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
package com.vitorpamplona.amethyst.commons.browser

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile

/**
 * One device-local visited site, keyed by full [url]. [visitCount]/[lastVisitedAt] drive frecency ranking
 * in the omnibox suggestions.
 */
@Serializable
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val host: String,
    val lastVisitedAt: Long,
    val visitCount: Int,
)

/**
 * The browser's visit history — the data behind the omnibox suggestions, alongside the user's favorites.
 *
 * **Only pages that actually loaded land here.** [record] is meant to be called on a *successful*
 * main-frame page-finish — never from the address bar as the user types — so misspelled/never-resolved
 * hosts never pollute the list. Bounded to [MAX_ENTRIES] most-recent entries. On Android the call is
 * relayed from the `:napplet` browser host over IPC through `NappletBrokerService`.
 *
 * Same shape as [com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry]: an authoritative
 * in-memory [StateFlow] for synchronous Compose reads, write-through persistence to [store] on [scope],
 * and the [DataStore] handed in rather than reached for, so the caller's store holder stays the single
 * registry and nothing here depends on a front end.
 *
 * One instance per process. On Android the launcher/omnibox in the **main** process own it; the keyless
 * `:napplet` sandbox never builds one.
 */
class BrowserHistoryRegistry(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val _history = MutableStateFlow<List<BrowserHistoryEntry>>(emptyList())
    val history: StateFlow<List<BrowserHistoryEntry>> = _history.asStateFlow()

    // Gates persistence until init() has been called: writing before hydration has been scheduled
    // would flush a partial list over the stored one. Set synchronously in init(), so the merge it
    // launches still persists whatever the session recorded in the meantime.
    @Volatile private var started = false

    /** Hydrates the on-disk list into [history]. Idempotent. */
    fun init() {
        if (started) return
        started = true
        scope.launch {
            val json = store.data.first()[KEY]
            val loaded = if (json != null) decode(json) else emptyList()
            // Merge disk under anything already recorded this session (session wins, newest-first).
            update { current -> dedupeNewestFirst(current + loaded) }
        }
    }

    /**
     * Records a successful visit to [url], moving it to the front. An existing entry for the same URL is
     * bumped (visit count +1, title refreshed if non-blank); otherwise a new entry is prepended.
     */
    fun record(
        url: String,
        title: String,
    ) {
        val host = OmniboxInput.hostOf(url) ?: url
        val now = TimeUtils.nowMillis()
        update { current ->
            val existing = current.firstOrNull { it.url == url }
            val entry =
                if (existing != null) {
                    existing.copy(
                        title = title.ifBlank { existing.title },
                        host = host,
                        lastVisitedAt = now,
                        visitCount = existing.visitCount + 1,
                    )
                } else {
                    BrowserHistoryEntry(url = url, title = title, host = host, lastVisitedAt = now, visitCount = 1)
                }
            (listOf(entry) + current.filterNot { it.url == url }).take(MAX_ENTRIES)
        }
    }

    fun remove(url: String) = update { current -> current.filterNot { it.url == url } }

    fun clear() = update { emptyList() }

    private fun dedupeNewestFirst(list: List<BrowserHistoryEntry>): List<BrowserHistoryEntry> =
        list
            .sortedByDescending { it.lastVisitedAt }
            .distinctBy { it.url }
            .take(MAX_ENTRIES)

    private inline fun update(transform: (List<BrowserHistoryEntry>) -> List<BrowserHistoryEntry>) {
        val next = transform(_history.value)
        if (next == _history.value) return
        _history.value = next
        persist(encode(next))
    }

    private fun persist(json: String) {
        if (!started) return
        scope.launch {
            store.edit { it[KEY] = json }
        }
    }

    private fun encode(list: List<BrowserHistoryEntry>): String = JsonMapper.toJson(list)

    private fun decode(json: String): List<BrowserHistoryEntry> =
        try {
            JsonMapper.fromJson<List<BrowserHistoryEntry>>(json)
        } catch (e: Exception) {
            Log.w("BrowserHistoryRegistry", "Failed to decode history", e)
            emptyList()
        }

    companion object {
        /** Same file the `Context.preferencesDataStore("browser_history")` delegate resolved to. */
        const val FILE_NAME = "browser_history"

        private val KEY = stringPreferencesKey("history")
        private const val MAX_ENTRIES = 500
    }
}
