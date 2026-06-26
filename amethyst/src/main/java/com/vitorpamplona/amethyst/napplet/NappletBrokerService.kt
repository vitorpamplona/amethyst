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

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.favorites.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.favorites.BrowserIconRegistry
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.napplet.gateways.AccountNappletGateways
import com.vitorpamplona.amethyst.napplethost.NappletIpc
import com.vitorpamplona.amethyst.ui.screen.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The trust boundary's main-process endpoint. The untrusted `:napplet` process binds this
 * service and sends requests as JSON over a [Messenger]; this is the only side that holds the
 * signer, the relays, and the permission ledger. It runs each request through the shared
 * [NappletBroker] (built per account by [AccountNappletGateways]) via the host-agnostic
 * [NappletRequestRouter], which gates everything on consent and never returns key material.
 *
 * This class is intentionally thin: it owns the Messenger transport, the per-account broker cache,
 * and the live-subscription registry, and delegates all decode/policy/execution to the shared core.
 *
 * `exported=false` in the manifest restricts binding to this app's own UID, so no other
 * installed app can reach the broker. A renderer escape that reaches the `:napplet` process
 * could still bind here — but it gains only what the user explicitly consents to, per
 * capability, and never the private key (the broker's contract).
 */
class NappletBrokerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One ledger for the whole service lifetime: persistent grants on disk, session grants in RAM.
    private val ledger by lazy { NappletPermissionLedger(DataStoreNappletPermissionStore(applicationContext)) }

    // Per-applet sandboxed key-value store (namespaced by coordinate inside the impl).
    private val storage by lazy { DataStoreNappletStorage(applicationContext) }

    private val incoming by lazy { Messenger(Handler(Looper.getMainLooper(), ::handleMessage)) }

    // The broker for the current account, rebuilt only on account switch (see broker()).
    private var cachedBroker: Pair<Account, NappletBroker>? = null

    // Live relay subscriptions, keyed by the applet's subId; reads the current account live.
    private val liveSubscriptions = NappletLiveSubscriptions { Amethyst.instance.sessionManager.loggedInAccount() }

    // The app-wide inc pub/sub bus: routes inc.emit between live napplet sessions as inc.event pushes.
    private val incBus = NappletIncBus { replyTo, payload -> push(replyTo, payload) }

    // Streams identity.changed pushes (account switch / connect / disconnect) to a watching applet.
    private val identityWatch =
        NappletIdentityWatch(scope) {
            Amethyst.instance.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.signer?.pubKey ?: "" }
        }

    // Binding is restricted to our own UID by exported=false in the manifest, enforced by the OS.
    // A UID check here would be useless: onBind() runs once (the binder is cached and reused for all
    // later clients) and outside any binder transaction, so getCallingUid() returns our own UID anyway.
    override fun onBind(intent: Intent?): IBinder? = incoming.binder

    override fun onDestroy() {
        liveSubscriptions.closeAll()
        identityWatch.stop()
        // Drop any foreground holds this broker still owns so they don't leak past the service.
        synchronized(foregroundLeases) {
            repeat(foregroundLeases.size) { SandboxForegroundHold.release() }
            foregroundLeases.clear()
        }
        scope.cancel()
        super.onDestroy()
    }

    // Sandbox surfaces (full-screen :napplet hosts) currently reporting themselves foreground, mapped to
    // the last time each renewed its lease (monotonic elapsedRealtime). While this map is non-empty the
    // main process is held resumed (Tor/relays/AUTH up) via SandboxForegroundHold — the napplet host
    // lives in :napplet and can't touch that lifecycle itself, so it signals over IPC.
    //
    // The host re-sends its foreground report on a heartbeat while genuinely resumed. We key on the
    // launch token, so a repeated report just refreshes the timestamp (idempotent, no double-acquire).
    // If a host's process dies while foreground it can't send its onPause "false", so the heartbeats
    // simply stop and [foregroundLeaseWatchdog] reaps the stale lease — bounding any such leak to one
    // [FOREGROUND_LEASE_TTL_MS] window instead of holding the network up forever.
    private val foregroundLeases = HashMap<String, Long>()
    private var foregroundLeaseWatchdog: Job? = null

    private fun handleMessage(msg: Message): Boolean {
        // A sandbox surface (full-screen :napplet host) entered, renewed, or left the foreground. Hold the
        // main process resumed while at least one is foreground, so opening it doesn't tear down Tor/relays.
        if (msg.what == NappletIpc.MSG_SET_FOREGROUND) {
            val data = msg.data ?: return true
            val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN) ?: return true
            val foreground = data.getBoolean(NappletIpc.KEY_FOREGROUND, false)
            synchronized(foregroundLeases) {
                if (foreground) {
                    val firstReport = !foregroundLeases.containsKey(token)
                    // A foreground lease just pins Tor/relays (no key access), but a misbehaving sandbox
                    // could still spam distinct keys to keep the network up. Bound the damage: refuse new
                    // lease keys past the cap. Real usage holds only a handful of foreground surfaces.
                    if (firstReport && foregroundLeases.size >= MAX_FOREGROUND_LEASES) {
                        Log.w("NappletBrokerService", "Foreground lease cap reached; ignoring new lease $token")
                        return true
                    }
                    foregroundLeases[token] = SystemClock.elapsedRealtime()
                    if (firstReport) {
                        SandboxForegroundHold.acquire()
                        ensureForegroundLeaseWatchdog()
                    }
                } else if (foregroundLeases.remove(token) != null) {
                    SandboxForegroundHold.release()
                }
            }
            return true
        }

        // The direct-WebView browser relays a successfully loaded page; record it in the visit history
        // (main process only). Only clean page-finishes reach here, so misspellings never get recorded.
        if (msg.what == NappletIpc.MSG_RECORD_HISTORY) {
            val data = msg.data ?: return true
            val url = data.getString(NappletIpc.KEY_HISTORY_URL)?.takeIf { it.isNotBlank() } ?: return true
            BrowserHistoryRegistry.init(applicationContext)
            BrowserHistoryRegistry.record(url, data.getString(NappletIpc.KEY_HISTORY_TITLE).orEmpty())
            return true
        }

        // The direct-WebView browser relays a favicon captured from the loaded page; store it by host.
        if (msg.what == NappletIpc.MSG_RECORD_ICON) {
            val data = msg.data ?: return true
            val host = data.getString(NappletIpc.KEY_ICON_HOST)?.takeIf { it.isNotBlank() } ?: return true
            val bytes = data.getByteArray(NappletIpc.KEY_ICON_BYTES) ?: return true
            BrowserIconRegistry.init(applicationContext)
            BrowserIconRegistry.record(host, bytes)
            return true
        }

        // The direct-WebView browser requests a favorite toggle for the current URL (main process only).
        if (msg.what == NappletIpc.MSG_TOGGLE_WEB_FAVORITE) {
            val data = msg.data ?: return true
            val url = data.getString(NappletIpc.KEY_FAVORITE_URL)?.takeIf { it.isNotBlank() } ?: return true
            val label = data.getString(NappletIpc.KEY_FAVORITE_LABEL).orEmpty().ifBlank { url }
            FavoriteAppsRegistry.init(applicationContext)
            val id = "url:$url"
            if (FavoriteAppsRegistry.isFavorite(id)) {
                FavoriteAppsRegistry.remove(id)
            } else {
                FavoriteAppsRegistry.add(FavoriteApp.WebApp(url, label, System.currentTimeMillis()))
            }
            return true
        }

        // The direct-WebView browser relays its per-host Tor choice; persist it (main process only).
        if (msg.what == NappletIpc.MSG_SET_WEB_TOR) {
            val data = msg.data ?: return true
            val host = data.getString(NappletIpc.KEY_WEB_HOST)?.takeIf { it.isNotBlank() } ?: return true
            WebAppNetworkRegistry.init(applicationContext)
            WebAppNetworkRegistry.set(host, data.getBoolean(NappletIpc.KEY_NETWORK_USE_TOR, true))
            return true
        }

        // The sandbox relays the user's per-site network choice; persist it against the trusted
        // coordinate the launch token resolves to (the sandbox can't state its own coordinate).
        if (msg.what == NappletIpc.MSG_SET_NETWORK_MODE) {
            val data = msg.data ?: return true
            val session = NappletLaunchRegistry.resolve(data.getString(NappletIpc.KEY_LAUNCH_TOKEN)) ?: return true
            NappletNetworkRegistry.init(applicationContext)
            NappletNetworkRegistry.set(session.identity.coordinate, data.getBoolean(NappletIpc.KEY_NETWORK_USE_TOR, true))
            return true
        }

        // Browser mode mints a fresh launch token per visited origin, so NIP-07 consent is scoped to the
        // one site the request came from. The origin is the trusted source origin the WebView reported
        // (the sandbox can't forge it), and the synthetic identity keys the permission ledger per host.
        if (msg.what == NappletIpc.MSG_MINT_BROWSER_TOKEN) {
            val data = msg.data ?: return true
            val replyTo = msg.replyTo ?: return true
            val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN)?.takeIf { it.isNotBlank() } ?: return true
            val identity = NappletIdentity(authorPubKey = BROWSER_IDENTITY_AUTHOR, identifier = origin)
            val token = NappletLaunchRegistry.register(identity, setOf(NappletCapability.IDENTITY, NappletCapability.RELAY))
            val response =
                Message.obtain(null, NappletIpc.MSG_BROWSER_TOKEN).apply {
                    this.data =
                        Bundle().apply {
                            putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                            putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
                        }
                }
            runCatching { replyTo.send(response) }
            return true
        }

        if (msg.what != NappletIpc.MSG_REQUEST) return false

        val data = msg.data ?: return true
        val replyTo = msg.replyTo ?: return true
        val requestId = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
        val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true

        val requestType = runCatching { NappletProtocolJson.readType(payload) }.getOrNull() ?: "napplet"

        // Resolve the launch token to the trusted identity + declared set. The sandbox never states
        // its own coordinate, so a compromised :napplet process can only ever act as the napplet it
        // was launched as (it holds only its own token). An unknown token = no session; refuse.
        val session = NappletLaunchRegistry.resolve(data.getString(NappletIpc.KEY_LAUNCH_TOKEN))
        if (session == null) {
            reply(replyTo, requestId, NappletProtocolJson.encodeResponse(requestType, NappletResponse.Failed("Unknown napplet session.")))
            return true
        }
        val identity = session.identity
        val declared = session.declared

        scope.launch {
            // The shared, host-agnostic router owns decode → broker → encode and the subscribe-vs-reply
            // decision (it stays wire-identical with the future desktop host). This service only supplies
            // the broker, the Messenger transport, and the live relay subscription each Outcome implies.
            val broker = broker()
            if (broker == null) {
                reply(replyTo, requestId, NappletProtocolJson.encodeResponse(requestType, NappletResponse.Failed("No account is signed in.")))
                return@launch
            }
            when (val outcome = NappletRequestRouter.route(broker, identity, declared, payload)) {
                is NappletRequestRouter.Outcome.Ignore -> {}
                is NappletRequestRouter.Outcome.Reply -> reply(replyTo, requestId, outcome.payload)
                is NappletRequestRouter.Outcome.OpenSubscription -> liveSubscriptions.open(outcome.subId, outcome.filters) { push(replyTo, it) }
                is NappletRequestRouter.Outcome.CloseSubscription -> liveSubscriptions.close(outcome.subId)
                is NappletRequestRouter.Outcome.WatchIdentity -> identityWatch.start { push(replyTo, it) }
                is NappletRequestRouter.Outcome.UnwatchIdentity -> identityWatch.stop()
                is NappletRequestRouter.Outcome.Push -> outcome.payloads.forEach { push(replyTo, it) }
                is NappletRequestRouter.Outcome.SubscribeInc -> incBus.subscribe(replyTo, outcome.topic)
                is NappletRequestRouter.Outcome.UnsubscribeInc -> incBus.unsubscribe(replyTo, outcome.topic)
                is NappletRequestRouter.Outcome.EmitInc -> incBus.emit(replyTo, identity.coordinate, outcome.topic, outcome.payloadRaw)
            }
        }
        return true
    }

    /**
     * Periodically reaps foreground leases that stopped renewing — the signature of a `:napplet` host
     * process that died (or was killed) while foreground, so it never sent its onPause "false". Each
     * reaped lease releases its [SandboxForegroundHold] so the network isn't held up forever by a host
     * that's already gone. Runs only while at least one lease exists; cancelled with [scope] on destroy.
     */
    private fun ensureForegroundLeaseWatchdog() {
        if (foregroundLeaseWatchdog != null) return
        foregroundLeaseWatchdog =
            scope.launch {
                while (true) {
                    delay(FOREGROUND_LEASE_CHECK_MS)
                    synchronized(foregroundLeases) {
                        val now = SystemClock.elapsedRealtime()
                        val iterator = foregroundLeases.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (now - entry.value > FOREGROUND_LEASE_TTL_MS) {
                                Log.w("NappletBrokerService", "Foreground lease ${entry.key} expired (host process gone?); releasing hold")
                                iterator.remove()
                                SandboxForegroundHold.release()
                            }
                        }
                    }
                }
            }
    }

    /**
     * The broker for the *currently* signed-in account, cached and rebuilt only when the account
     * changes (reference identity). The gateways capture the account and read its flows live, so a
     * cached broker stays correct across requests without per-request allocation.
     */
    @Synchronized
    private fun broker(): NappletBroker? {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return null
        cachedBroker?.let { (acc, broker) -> if (acc === account) return broker }
        val broker =
            AccountNappletGateways(
                account = account,
                context = applicationContext,
                ledger = ledger,
                storage = storage,
                // Prefer Tor when active; the shared manager falls back to clearnet when it isn't.
                httpClient = { Amethyst.instance.okHttpClients.getHttpClient(useProxy = true) },
            ).broker()
        cachedBroker = account to broker
        return broker
    }

    private fun reply(
        replyTo: Messenger,
        requestId: String,
        payload: String,
    ) {
        val response =
            Message.obtain(null, NappletIpc.MSG_RESPONSE).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, requestId)
                        putString(NappletIpc.KEY_PAYLOAD, payload)
                    }
            }
        try {
            replyTo.send(response)
        } catch (e: RemoteException) {
            Log.w("NappletBrokerService", "Applet host went away before reply could be delivered", e)
        }
    }

    /** Sends an unsolicited push (a `relay.event`/`relay.eose` envelope) for the host to forward verbatim. */
    private fun push(
        replyTo: Messenger,
        payload: String,
    ) {
        val message =
            Message.obtain(null, NappletIpc.MSG_PUSH).apply {
                data = Bundle().apply { putString(NappletIpc.KEY_PAYLOAD, payload) }
            }
        try {
            replyTo.send(message)
        } catch (e: RemoteException) {
            Log.w("NappletBrokerService", "Applet host went away before push could be delivered", e)
        }
    }

    companion object {
        /**
         * Sentinel "author" for a browser-mode per-origin identity. The real key is the visited origin,
         * carried in the identity's identifier (which the consent dialog shows); this constant only fills
         * the coordinate's author slot so each origin keys the permission ledger separately as
         * `browser:<origin>`. It is never treated as a real pubkey.
         */
        private const val BROWSER_IDENTITY_AUTHOR = "browser"

        /**
         * How long a foreground lease stays valid without a renewing heartbeat. A live host re-reports
         * every [NappletHostActivity.FOREGROUND_HEARTBEAT_MS][com.vitorpamplona.amethyst.napplethost.NappletHostActivity]
         * (well under this), so only a host that's actually gone lets its lease age past it. Sized to
         * tolerate a couple of missed/delayed heartbeats while still reaping a dead host's hold promptly.
         */
        private const val FOREGROUND_LEASE_TTL_MS = 90_000L

        /** How often [ensureForegroundLeaseWatchdog] sweeps for stale leases. */
        private const val FOREGROUND_LEASE_CHECK_MS = 20_000L

        /**
         * Cap on concurrent foreground leases. A foreground lease only keeps Tor/relays up (no key
         * access), but this bounds how long a misbehaving sandbox can pin the network by spamming
         * distinct lease keys. Comfortably above any realistic number of foreground sandbox surfaces.
         */
        private const val MAX_FOREGROUND_LEASES = 8
    }
}
