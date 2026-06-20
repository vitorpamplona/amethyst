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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletWalletGateway
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The trust boundary's main-process endpoint. The untrusted `:napplet` process binds this
 * service and sends [NappletRequest]s as JSON over a [Messenger]; this is the only side that
 * holds the signer, the relays, and the permission ledger. It runs each request through the
 * shared [NappletBroker], which gates everything on consent and never returns key material.
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

    override fun onBind(intent: Intent?): IBinder? {
        // Defense in depth on top of exported=false: only our own UID may bind.
        if (Binder.getCallingUid() != Process.myUid()) return null
        return incoming.binder
    }

    override fun onDestroy() {
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

        scope.launch {
            val response = process(identity, declared, payload)
            reply(replyTo, requestId, NappletProtocolJson.encodeResponse(response))
        }
        return true
    }

    private fun parseDeclared(value: String?): Set<NappletCapability> =
        value
            ?.split(',')
            ?.mapNotNull { name -> runCatching { NappletCapability.valueOf(name.trim()) }.getOrNull() }
            ?.toSet()
            ?: emptySet()

    private suspend fun process(
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
        payload: String,
    ): NappletResponse {
        val request =
            runCatching { NappletProtocolJson.decodeRequest(payload) }.getOrNull()
                ?: return NappletResponse.Failed("Malformed or unsupported request.")

        val broker = buildBroker() ?: return NappletResponse.Failed("No account is signed in.")
        return broker.handle(identity, request, declared)
    }

    /** Builds a broker bound to the *currently* signed-in account, so account switches are honored. */
    private fun buildBroker(): NappletBroker? {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return null

        val relay =
            object : NappletRelayGateway {
                override suspend fun publish(event: Event): List<String> {
                    val relays = account.computeRelayListToBroadcast(event)
                    account.client.publish(event, relays)
                    return relays.map { it.url }
                }

                override suspend fun query(filter: Filter): List<Event> = queryEvents(account, filter)
            }

        val consent =
            NappletConsentPrompt { id, capability, request ->
                NappletConsentCoordinator.requestConsent(
                    context = applicationContext,
                    info = consentInfo(id, capability, request),
                )
            }

        val wallet = NappletWalletGateway { invoice -> payInvoiceViaNwc(account, invoice) }

        return NappletBroker(account.signer, ledger, consent, relay, storage, wallet)
    }

    /** Bounded live relay fetch (EOSE/timeout) merged with the local cache, newest-first. */
    private suspend fun queryEvents(
        account: Account,
        filter: Filter,
    ): List<Event> {
        val relays = account.homeRelays.flow.value
        val fromRelays =
            if (relays.isEmpty()) {
                emptyList()
            } else {
                runCatching {
                    account.client.fetchAll(filters = relays.associateWith { listOf(filter) }, timeoutMs = QUERY_TIMEOUT_MS)
                }.getOrDefault(emptyList())
            }
        val fromCache = account.cache.filter(filter).mapNotNull { it.event }

        val merged =
            (fromRelays + fromCache)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
        return filter.limit?.let { merged.take(it) } ?: merged
    }

    /**
     * Pays [invoice] via the user's connected NWC wallet, returning the preimage on success.
     * Throws (→ `Failed`) when no wallet is connected, the wallet reports an error, or it does not
     * respond in time — so the applet never silently believes a payment succeeded.
     */
    private suspend fun payInvoiceViaNwc(
        account: Account,
        invoice: String,
    ): String? {
        if (account.nip47SignerState.defaultWalletUri.value == null) {
            throw IllegalStateException("No Lightning wallet is connected.")
        }

        val result = CompletableDeferred<String?>()
        account.sendZapPaymentRequestFor(invoice, null) { response ->
            when (response) {
                is PayInvoiceSuccessResponse -> result.complete(response.result?.preimage)
                is PayInvoiceErrorResponse -> result.completeExceptionally(RuntimeException(response.error?.message ?: "Payment failed."))
                is NwcErrorResponse -> result.completeExceptionally(RuntimeException(response.error?.message ?: "Wallet error."))
                else -> result.completeExceptionally(RuntimeException("Unexpected wallet response."))
            }
        }
        return withTimeout(WALLET_TIMEOUT_MS) { result.await() }
    }

    private fun consentInfo(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): NappletConsentInfo {
        val title = identity.identifier.ifBlank { getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8)) }
        return NappletConsentInfo(
            appletTitle = title,
            coordinate = identity.coordinate,
            capabilityLabel = getString(capability.labelRes()),
            operationSummary = summaryFor(request),
            allowAlways = capability.canGrantAlways,
        )
    }

    private fun summaryFor(request: NappletRequest): String =
        when (request) {
            is NappletRequest.GetPublicKey -> getString(R.string.napplet_consent_get_pubkey)
            is NappletRequest.SignEvent -> {
                val preview = request.content.take(160).trim()
                if (preview.isEmpty()) {
                    getString(R.string.napplet_consent_sign, request.kind)
                } else {
                    getString(R.string.napplet_consent_sign_preview, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.Nip04Encrypt, is NappletRequest.Nip44Encrypt -> getString(R.string.napplet_consent_encrypt)
            is NappletRequest.Nip04Decrypt, is NappletRequest.Nip44Decrypt -> getString(R.string.napplet_consent_decrypt)
            is NappletRequest.Publish -> getString(R.string.napplet_consent_publish)
            is NappletRequest.QueryEvents -> getString(R.string.napplet_consent_query)
            is NappletRequest.StorageGet, is NappletRequest.StorageSet, is NappletRequest.StorageRemove ->
                getString(R.string.napplet_consent_storage)
            is NappletRequest.PayInvoice -> {
                val sats = runCatching { LnInvoiceUtil.getAmountInSats(request.invoice).toLong() }.getOrNull()
                if (sats != null) {
                    pluralStringRes(this, R.plurals.napplet_consent_pay_amount, sats.toInt(), sats)
                } else {
                    getString(R.string.napplet_consent_pay)
                }
            }
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

    companion object {
        private const val QUERY_TIMEOUT_MS = 8_000L
        private const val WALLET_TIMEOUT_MS = 60_000L
    }
}
