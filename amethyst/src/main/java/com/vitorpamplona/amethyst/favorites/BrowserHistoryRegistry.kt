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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private val Context.browserHistoryDataStore by preferencesDataStore(name = "browser_history")

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
 * **Only pages that actually loaded land here.** [record] is called from the `:napplet` browser host
 * (relayed over IPC through `NappletBrokerService`) on a *successful* main-frame page-finish — never from
 * the address bar as the user types — so misspelled/never-resolved hosts never pollute the list. Bounded
 * to [MAX_ENTRIES] most-recent entries.
 *
 * Lives only in the **main process** (the launcher/omnibox consume it; the keyless `:napplet` sandbox
 * never reads it). Same shape as [FavoriteAppsRegistry]: an authoritative in-memory [StateFlow] for
 * synchronous Compose reads, with write-through persistence to a DataStore on a background scope.
 */
object BrowserHistoryRegistry {
    private val KEY = stringPreferencesKey("history")
    private const val MAX_ENTRIES = 500

    private val _history = MutableStateFlow<List<BrowserHistoryEntry>>(emptyList())
    val history: StateFlow<List<BrowserHistoryEntry>> = _history.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    @Volatile private var hydrated = false

    /** Binds the app context and hydrates the on-disk list into [history]. Idempotent. */
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            val json = ctx.browserHistoryDataStore.data.first()[KEY]
            val loaded = if (json != null) decode(json) else emptyList()
            // Merge disk under anything already recorded this session (session wins, newest-first).
            update { current -> dedupeNewestFirst(current + loaded) }
            hydrated = true
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
        val now = System.currentTimeMillis()
        val key = historyKey(url)
        update { current ->
            val existing = current.firstOrNull { historyKey(it.url) == key }
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
            (listOf(entry) + current.filterNot { historyKey(it.url) == key }).take(MAX_ENTRIES)
        }
    }

    fun remove(url: String) =
        update { current ->
            val key = historyKey(url)
            current.filterNot { historyKey(it.url) == key }
        }

    fun clear() = update { emptyList() }

    private fun dedupeNewestFirst(list: List<BrowserHistoryEntry>): List<BrowserHistoryEntry> =
        list
            .sortedByDescending { it.lastVisitedAt }
            .distinctBy { historyKey(it.url) }
            .take(MAX_ENTRIES)

    private inline fun update(transform: (List<BrowserHistoryEntry>) -> List<BrowserHistoryEntry>) {
        val next = transform(_history.value)
        if (next == _history.value) return
        _history.value = next
        persist(encode(next))
    }

    private fun persist(json: String) {
        val ctx = appContext ?: return
        scope.launch {
            ctx.browserHistoryDataStore.edit { it[KEY] = json }
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
}

/**
 * What counts as "the same page" in the history list.
 *
 * Keying on the raw URL string put `primal.net` in the list twice: a trailing slash, a different case
 * in the host, or a leftover `#fragment` reads as one page and compares as two Strings. So the scheme
 * and host are lowercased, a fragment is dropped, and a trailing slash is dropped when the path is
 * nothing but that slash.
 *
 * The path and the query are kept and compared as-is. Two pages of the same site are two entries, and
 * guessing which query parameters are load-bearing is how you lose one of them.
 */
internal fun historyKey(url: String): String {
    val noFragment = url.substringBefore('#')
    val schemeEnd = noFragment.indexOf("://")
    if (schemeEnd <= 0) return noFragment
    val scheme = noFragment.substring(0, schemeEnd).lowercase()
    val rest = noFragment.substring(schemeEnd + 3)
    val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }
    val authority = (if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)).lowercase()
    val tail = if (authorityEnd < 0) "" else rest.substring(authorityEnd)
    return scheme + "://" + authority + if (tail == "/") "" else tail
}
