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
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.webUrlNetworkDataStore by preferencesDataStore(name = "weburl_network")

/**
 * Per-web-client network-routing preference: whether a favorited URL / browsed site routes through
 * **Tor** (the default when Tor is active) or over the **open web**. The web-client sibling of
 * [NappletNetworkRegistry] — same model, but keyed by the site's **host** (so every page of a site
 * shares the choice and it survives across launches).
 *
 * This exists because some sites' servers reject Tor exit connections; the user toggles such a site to
 * the open web once from its control puck, and we must remember that so it doesn't break again next
 * time. Absent = Tor (the safe default).
 *
 * Lives only in the **main process** (where the browser chrome runs); an in-memory map is authoritative
 * for the session with write-through persistence.
 */
object WebUrlNetworkRegistry {
    private const val OPEN_WEB = "OPEN"
    private const val TOR = "TOR"

    // host -> useTor. Absent means "never chosen" -> Tor.
    private val modes = ConcurrentHashMap<String, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    /** Binds the app context and hydrates the on-disk preferences into memory. Idempotent. */
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            ctx.webUrlNetworkDataStore.data.first().asMap().forEach { (key, value) ->
                // putIfAbsent: never clobber a choice made in this session before hydration finished.
                modes.putIfAbsent(key.name, value != OPEN_WEB)
            }
        }
    }

    /** The host key for [url] (e.g. `vitorpamplona.com`), or the raw string if it has no host. */
    fun hostKeyOf(url: String): String = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

    /** Whether the site behind [url] routes through Tor. Defaults to true (Tor) for any site never set. */
    fun useTor(url: String): Boolean = modes[hostKeyOf(url)] ?: true

    /** Records [useTor] for the host behind [url] in memory and persists it. */
    fun set(
        url: String,
        useTor: Boolean,
    ) {
        val host = hostKeyOf(url)
        modes[host] = useTor
        val ctx = appContext ?: return
        scope.launch {
            ctx.webUrlNetworkDataStore.edit { it[stringPreferencesKey(host)] = if (useTor) TOR else OPEN_WEB }
        }
    }
}
