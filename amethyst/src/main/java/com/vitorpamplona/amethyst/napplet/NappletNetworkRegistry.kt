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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.nappletNetworkDataStore by preferencesDataStore(name = "napplet_network")

/**
 * Per-nSite network-routing preference: whether a site's traffic goes through **Tor** (the default)
 * or over the **open web**. Keyed by the site's coordinate (`author:identifier`), like the permission
 * ledger, so the choice survives across launches and routine code updates.
 *
 * When Tor is active, nSites route through it by default; a user can opt a specific site out (e.g. a
 * site that breaks or is unusably slow over Tor) from the sandbox top bar. "Open web" makes
 * *everything* for that site direct — both its live web traffic and its blob fetches.
 *
 * An in-memory map is authoritative for the session ([NappletLauncher] reads it synchronously while
 * building the launch intent) with write-through persistence. Absent = Tor (the safe default), so a
 * site never silently starts on the open web before the on-disk preference has hydrated.
 *
 * Lives only in the **main process**: the launcher reads it, and [NappletBrokerService] writes it
 * when the key-free `:napplet` sandbox relays a toggle over IPC. The sandbox never touches it.
 */
object NappletNetworkRegistry {
    private const val OPEN_WEB = "OPEN"
    private const val TOR = "TOR"

    // coordinate -> useTor. Absent means "never chosen" -> Tor.
    private val modes = ConcurrentHashMap<String, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    @Volatile private var hydration: Job? = null

    /** Binds the app context and hydrates the on-disk preferences into memory. Idempotent. */
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        hydration =
            scope.launch {
                ctx.nappletNetworkDataStore.data.first().asMap().forEach { (key, value) ->
                    // putIfAbsent: never clobber a choice made in this session before hydration finished.
                    modes.putIfAbsent(key.name, value != OPEN_WEB)
                }
            }
    }

    /**
     * Suspends until [init]'s on-disk hydration has finished, so [useTor] reflects the user's remembered
     * per-site choices instead of the bare Tor default. The startup preloader awaits this before making
     * any routing decision, so a site the user pinned to the open web isn't wrongly treated as Tor on a
     * cold start. Returns at once once hydrated, or immediately if [init] was never called.
     */
    suspend fun awaitReady() {
        hydration?.join()
    }

    /** Whether [coordinate] routes through Tor. Defaults to true (Tor) for any site never set. */
    fun useTor(coordinate: String): Boolean = modes[coordinate] ?: true

    /** Records [useTor] for [coordinate] in memory and persists it. */
    fun set(
        coordinate: String,
        useTor: Boolean,
    ) {
        modes[coordinate] = useTor
        val ctx = appContext ?: return
        scope.launch {
            ctx.nappletNetworkDataStore.edit { it[stringPreferencesKey(coordinate)] = if (useTor) TOR else OPEN_WEB }
        }
    }
}
