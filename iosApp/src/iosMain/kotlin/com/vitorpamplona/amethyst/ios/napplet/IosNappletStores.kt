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
package com.vitorpamplona.amethyst.ios.napplet

import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoute
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRouteArbiter
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoutePort
import com.vitorpamplona.amethyst.commons.napplet.session.NappletStorageProfile
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.NSThread
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_proxy_config_create_socksv5
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

/**
 * The web storage and network route of each account's napplets and nSites: one persistent
 * `WKWebsiteDataStore` per account, named as Android names the account's WebView profile
 * ([NappletStorageProfile]), and that store's proxy.
 *
 * WebKit proxies per data store, and every app of one account shares the account's store, so the
 * store's sites share one route, decided as Android decides its process-wide WebView proxy and Desktop
 * its SOCKS router ([NappletRouteArbiter], Tor always wins): a site on Tor puts the store on Tor's
 * SOCKS listener, an open-web site alone leaves it direct, and a Tor site while Tor has no port points
 * it at a port nothing listens on, so its loads fail (`-1004`) and never go direct. Napplets and webxdc
 * apps claim nothing: they have no network (their request filter blocks it all).
 *
 * Main thread only: WebKit's stores are.
 */
@OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)
class IosNappletStores(
    /** Tor's SOCKS port now, or null when it has none. */
    private val torPort: () -> Int?,
) {
    private class Entry(
        val store: WKWebsiteDataStore,
    ) {
        val arbiter = NappletRouteArbiter()
        var applied: NappletRoute? = null
    }

    private val entries = HashMap<String, Entry>()

    private val mutableChanges = MutableStateFlow(0)

    /** Ticks whenever a store's route changes, for a screen that says where its site goes. */
    val changes: StateFlow<Int> = mutableChanges.asStateFlow()

    /** [pubKey]'s store: the same object every time, so its proxy is the one its web views use. */
    fun storeFor(pubKey: HexKey): WKWebsiteDataStore = entryFor(pubKey)?.store ?: WKWebsiteDataStore.nonPersistentDataStore()

    // Nobody logged in can't open an app; a store that keeps nothing would be the safe answer anyway.
    private fun entryFor(pubKey: HexKey): Entry? {
        val id = NappletStorageProfile.uuidFor(pubKey) ?: return null
        return entries.getOrPut(id) { Entry(WKWebsiteDataStore.dataStoreForIdentifier(NSUUID(uUIDString = id))) }
    }

    /** [owner], a site of [pubKey]'s, wants Tor or the open web: the store's route follows, and is returned. */
    fun claim(
        pubKey: HexKey,
        owner: Any,
        useTor: Boolean,
    ): NappletRoute {
        val entry = entryFor(pubKey) ?: return NappletRoute.Blocked
        return apply(entry, entry.arbiter.want(owner, useTor, torPort()))
    }

    /** [owner] closed: the store's remaining sites decide its route. */
    fun release(
        pubKey: HexKey,
        owner: Any,
    ) {
        val entry = entryFor(pubKey) ?: return
        apply(entry, entry.arbiter.release(owner, torPort()))
    }

    /** The route [pubKey]'s store is on, for a screen that says when its site goes through Tor. */
    fun routeOf(pubKey: HexKey): NappletRoute? = entryFor(pubKey)?.applied

    /** Whether a site of [pubKey]'s other than [owner] keeps the store on Tor. */
    fun torWantedByOthers(
        pubKey: HexKey,
        owner: Any,
    ): Boolean = entryFor(pubKey)?.arbiter?.torWantedByOthers(owner) == true

    /** Tor's port changed (it restarted elsewhere, came up or went away): every store follows. */
    fun reapplyAll() {
        val port = torPort()
        entries.values.forEach { apply(it, it.arbiter.route(port)) }
    }

    private fun apply(
        entry: Entry,
        route: NappletRoute,
    ): NappletRoute {
        // A store no site claims has no route of its own: nothing on it has a network.
        if (entry.arbiter.isEmpty && entry.applied == null) return route
        if (route == entry.applied) return route
        entry.store.proxyConfigurations =
            when (route) {
                is NappletRoute.Tor -> listOf(socks(route.port))
                NappletRoute.Direct -> null
                // Wanted on Tor and Tor can't be reached: nothing may go out, not even directly.
                NappletRoute.Blocked -> listOf(socks(DEAD_PORT))
            }
        entry.applied = route
        mutableChanges.update { it + 1 }
        Log.d(TAG) { "store route -> $route" }
        return route
    }

    private fun socks(port: Int) = nw_proxy_config_create_socksv5(nw_endpoint_create_host(LOOPBACK, port.toString()))

    /**
     * Deletes [pubKey]'s store: its storage, cookies and caches. The account's web views must be closing
     * first ([IosNappletHost.closeAccount]): WebKit refuses to remove a store a web view still uses, so
     * this tries again while they go. True when it is gone.
     */
    suspend fun remove(pubKey: HexKey): Boolean {
        NappletStorageProfile.uuidFor(pubKey)?.let(entries::remove)
        return removeStore(pubKey)
    }

    companion object {
        private const val TAG = "IosNappletStores"
        private const val LOOPBACK = "127.0.0.1"

        /** TCP port 1 (tcpmux): nothing listens there, so a blocked store's every load fails at once. */
        const val DEAD_PORT = 1

        private const val REMOVE_ATTEMPTS = 6
        private const val REMOVE_RETRY_MS = 500L
        private const val WIPE_TIMEOUT_MS = 10_000L
        private const val PENDING_KEY = "amethyst.napplet.storesToRemove"

        /**
         * Deletes [pubKey]'s store, whether or not anything here holds it (the app may not have opened
         * an app this run). Main thread. True when its data is gone.
         *
         * WebKit refuses to remove a store while any web view that used it is still alive, and a closed
         * one lingers until WebKit lets it go (measured: still alive seconds after close and a full
         * collection). So after a few tries the store's data is wiped in place, which WebKit allows
         * while it is in use, and the empty store itself is removed at the next start
         * ([removePendingStores]), before anything opens it.
         */
        suspend fun removeStore(pubKey: HexKey): Boolean {
            val id = NappletStorageProfile.uuidFor(pubKey) ?: return true
            if (tryRemove(id)) return true
            val wiped = wipe(id)
            addPending(id)
            Log.w(TAG) { "The napplet store of ${pubKey.take(8)} is still in use: ${if (wiped) "wiped" else "NOT wiped"}, removed at the next start" }
            return wiped
        }

        /** Removes the stores [removeStore] could only wipe. At start, before any store opens. Main thread. */
        suspend fun removePendingStores() {
            val defaults = NSUserDefaults.standardUserDefaults
            val pending = defaults.stringArrayForKey(PENDING_KEY)?.filterIsInstance<String>().orEmpty()
            if (pending.isEmpty()) return
            val left = pending.filterNot { tryRemove(it, attempts = 1) }
            defaults.setObject(left, PENDING_KEY)
        }

        private fun addPending(id: String) {
            val defaults = NSUserDefaults.standardUserDefaults
            val pending = defaults.stringArrayForKey(PENDING_KEY)?.filterIsInstance<String>().orEmpty()
            if (id !in pending) defaults.setObject(pending + id, PENDING_KEY)
        }

        private suspend fun tryRemove(
            id: String,
            attempts: Int = REMOVE_ATTEMPTS,
        ): Boolean {
            val uuid = NSUUID(uUIDString = id)
            repeat(attempts) { attempt ->
                val error =
                    suspendCancellableCoroutine { cont ->
                        WKWebsiteDataStore.removeDataStoreForIdentifier(uuid) { error -> cont.resume(error) }
                    }
                if (error == null) {
                    Log.d(TAG) { "removed the napplet store $id" }
                    return true
                }
                Log.d(TAG) { "store $id not removed yet (${error.localizedDescription}), attempt ${attempt + 1}" }
                // A closed web view (and the store it held) can only go once Kotlin's collector frees its wrapper.
                GC.collect()
                if (attempt < attempts - 1) delay(REMOVE_RETRY_MS)
            }
            return false
        }

        // Every kind of website data the store holds (storage, cookies, caches, service workers), all time.
        private suspend fun wipe(id: String): Boolean {
            val store = WKWebsiteDataStore.dataStoreForIdentifier(NSUUID(uUIDString = id))
            return withTimeoutOrNull(WIPE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    store.removeDataOfTypes(WKWebsiteDataStore.allWebsiteDataTypes(), NSDate.dateWithTimeIntervalSince1970(0.0)) { cont.resume(true) }
                }
            } == true
        }
    }
}

/**
 * One launch's [NappletRoutePort] on iOS. A site claims its account's store ([IosNappletStores], Tor
 * always wins among the store's sites); on Tor and with Tor down, the store points nowhere and the
 * launch waits for a port ([awaitTorPort]) before failing closed, so no web view is made meanwhile. A
 * napplet or webxdc app claims nothing and only waits for Tor when its files go through it.
 */
class IosNappletStoreRoute(
    private val stores: IosNappletStores,
    private val pubKey: HexKey,
    /** Whether the launch has a network of its own (an nSite). */
    private val usesNetwork: Boolean,
    private val torPort: () -> Int?,
    private val awaitTorPort: suspend () -> Boolean,
    /** For a launch whose files alone go through Tor: Tor runs, or the user skipped it this session. */
    private val torRunningForFiles: () -> Boolean,
    private val awaitTorForFiles: suspend () -> Boolean,
) : NappletRoutePort {
    override val torRunning: Boolean get() = if (usesNetwork) torPort() != null else torRunningForFiles()

    override suspend fun claim(useTor: Boolean): Boolean {
        withContext(Dispatchers.Main) { stores.claim(pubKey, this@IosNappletStoreRoute, useTor) }
        if (!useTor) return true
        val running = awaitTorPort()
        withContext(Dispatchers.Main) { stores.reapplyAll() }
        return running
    }

    override suspend fun awaitTor(): Boolean = awaitTorForFiles()

    override fun release() {
        if (!usesNetwork) return
        if (NSThread.isMainThread) {
            stores.release(pubKey, this)
        } else {
            dispatch_async(dispatch_get_main_queue()) { stores.release(pubKey, this) }
        }
    }
}
