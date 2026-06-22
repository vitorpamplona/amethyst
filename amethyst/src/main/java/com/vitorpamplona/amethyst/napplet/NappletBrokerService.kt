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
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.napplet.gateways.AccountNappletGateways
import com.vitorpamplona.amethyst.ui.screen.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    // Streams identity.changed pushes (account switch / connect / disconnect) to a watching applet.
    private val identityWatch =
        NappletIdentityWatch(scope) {
            Amethyst.instance.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.signer?.pubKey ?: "" }
        }

    override fun onBind(intent: Intent?): IBinder? {
        // Defense in depth on top of exported=false: only our own UID may bind.
        if (Binder.getCallingUid() != Process.myUid()) return null
        return incoming.binder
    }

    override fun onDestroy() {
        liveSubscriptions.closeAll()
        identityWatch.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun handleMessage(msg: Message): Boolean {
        if (msg.what != NappletIpc.MSG_REQUEST) return false

        val data = msg.data ?: return true
        val replyTo = msg.replyTo ?: return true
        val requestId = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
        val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true

        val identity =
            NappletIdentity(
                authorPubKey = data.getString(NappletIpc.KEY_AUTHOR).orEmpty(),
                identifier = data.getString(NappletIpc.KEY_IDENTIFIER).orEmpty(),
                aggregateHash = data.getString(NappletIpc.KEY_AGGREGATE_HASH),
            )
        val declared = parseDeclared(data.getString(NappletIpc.KEY_DECLARED))

        val requestType = runCatching { NappletProtocolJson.readType(payload) }.getOrNull() ?: "napplet"
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
            }
        }
        return true
    }

    private fun parseDeclared(value: String?): Set<NappletCapability> =
        value
            ?.split(',')
            ?.mapNotNull { name -> runCatching { NappletCapability.valueOf(name.trim()) }.getOrNull() }
            ?.toSet()
            ?: emptySet()

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
                torPort = { Amethyst.instance.torManager.activePortOrNull.value ?: -1 },
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
}
