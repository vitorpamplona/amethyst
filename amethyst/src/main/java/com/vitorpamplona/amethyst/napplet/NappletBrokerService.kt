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
import android.util.Base64
import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentityGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletResource
import com.vitorpamplona.amethyst.commons.napplet.NappletResourceGateway
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
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder

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

        val requestType = runCatching { NappletProtocolJson.readType(payload) }.getOrNull() ?: "napplet"
        scope.launch {
            // Unsubscribe is fire-and-forget: snapshot subscriptions have no live tail to cancel.
            if (requestType == "relay.close") {
                reply(replyTo, requestId, NappletProtocolJson.encodeResponse(requestType, NappletResponse.Done))
                return@launch
            }

            val response = process(identity, declared, payload)

            // A subscription is answered with relay.event/relay.eose pushes (keyed by subId), not a
            // .result — matching @napplet/shim. Today it delivers the initial matches then EOSE; a
            // live tail is a follow-up.
            val subId = if (requestType == "relay.subscribe") runCatching { NappletProtocolJson.readSubId(payload) }.getOrNull() else null
            if (subId != null) {
                if (response is NappletResponse.Events) {
                    response.events.forEach { push(replyTo, NappletProtocolJson.encodeRelayEvent(subId, it)) }
                }
                push(replyTo, NappletProtocolJson.encodeRelayEose(subId))
            } else {
                reply(replyTo, requestId, NappletProtocolJson.encodeResponse(requestType, response))
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

        val resource = NappletResourceGateway { url -> fetchResource(account, url) }

        val identityReads = NappletIdentityGateway { method, argument -> readIdentity(account, method, argument) }

        // upload is intentionally not provided yet: a correct Blossom upload needs a content Uri,
        // a signed authorization event, and server selection — wired end-to-end (protocol/shim) but
        // the Android gateway is a follow-up that needs on-device verification.
        return NappletBroker(account.signer, ledger, consent, relay, storage, wallet, resource, upload = null, identityReads = identityReads)
    }

    /**
     * Reads a non-key identity datum from the active account as a JSON value string. Returns the
     * literal `"null"` for an absent value, or `null` for a method this shell does not implement
     * (the broker then answers `Unsupported`). All reads are public data — never key material.
     */
    private fun readIdentity(
        account: Account,
        method: String,
        argument: String?,
    ): String? =
        when (method) {
            "getProfile" -> profileJson(account)
            "getFollows" -> jsonStringArray(account.kind3FollowList.flow.value.authors)
            "getMutes" ->
                jsonStringArray(
                    account.muteList.flow.value
                        .filterIsInstance<UserTag>()
                        .map { it.pubKey },
                )
            "getBlocked" ->
                jsonStringArray(
                    account.blockPeopleList.flow.value
                        .filterIsInstance<UserTag>()
                        .map { it.pubKey },
                )
            "getRelays" -> relaysJson(account)
            // getList/getZaps/getBadges and any other read are not implemented yet → Unsupported.
            else -> null
        }

    private fun jsonStringArray(items: Iterable<String>): String = buildJsonArray { items.forEach { add(it) } }.toString()

    /** Builds a `@napplet/nap` `ProfileData` object (note `displayName`, not `display_name`) from kind-0. */
    private fun profileJson(account: Account): String {
        val md = account.userMetadata.getUserMetadataEvent()?.contactMetaData() ?: return "null"
        return buildJsonObject {
            md.name?.let { put("name", it) }
            md.displayName?.let { put("displayName", it) }
            md.about?.let { put("about", it) }
            md.picture?.let { put("picture", it) }
            md.banner?.let { put("banner", it) }
            md.nip05?.let { put("nip05", it) }
            md.lud16?.let { put("lud16", it) }
            md.website?.let { put("website", it) }
        }.toString()
    }

    /** Builds `{ "<relay url>": { "read": bool, "write": bool }, ... }` from the user's NIP-65 list. */
    private fun relaysJson(account: Account): String {
        val relays = account.nip65RelayList.getNIP65RelayList()?.relays() ?: return "null"
        return buildJsonObject {
            relays.forEach { info ->
                putJsonObject(info.relayUrl.url) {
                    put("read", info.type.isRead())
                    put("write", info.type.isWrite())
                }
            }
        }.toString()
    }

    /** Fetches an https/data resource on the applet's behalf (it has no direct network). */
    private suspend fun fetchResource(
        account: Account,
        url: String,
    ): NappletResource? =
        withContext(Dispatchers.IO) {
            when {
                url.startsWith("data:") -> decodeDataUrl(url)
                url.startsWith("https://") -> {
                    val port = account.let { Amethyst.instance.torManager.activePortOrNull.value } ?: -1
                    val client =
                        if (port > 0) {
                            OkHttpClient.Builder().proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).build()
                        } else {
                            OkHttpClient()
                        }
                    runCatching {
                        client
                            .newCall(
                                Request
                                    .Builder()
                                    .url(url)
                                    .get()
                                    .build(),
                            ).execute()
                            .use { r ->
                                if (!r.isSuccessful) return@withContext null
                                val body = r.body.bytes()
                                val type = r.header("Content-Type") ?: "application/octet-stream"
                                NappletResource(body, type)
                            }
                    }.getOrNull()
                }
                // blossom: / nostr: schemes are a follow-up.
                else -> null
            }
        }

    /** Parses a `data:[<mediatype>][;base64],<data>` URL into bytes + content type. */
    private fun decodeDataUrl(url: String): NappletResource? {
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val meta = url.substring("data:".length, comma)
        val data = url.substring(comma + 1)
        val isBase64 = meta.endsWith(";base64")
        val contentType = meta.removeSuffix(";base64").ifEmpty { "text/plain" }
        val bytes =
            if (isBase64) {
                runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull() ?: return null
            } else {
                URLDecoder.decode(data, "UTF-8").encodeToByteArray()
            }
        return NappletResource(bytes, contentType)
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
            is NappletRequest.IdentityRead -> getString(R.string.napplet_consent_identity_read)
            is NappletRequest.Publish -> {
                val preview = request.content.take(160).trim()
                if (preview.isEmpty()) {
                    getString(R.string.napplet_consent_publish, request.kind)
                } else {
                    getString(R.string.napplet_consent_publish_preview, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.PublishEncrypted -> getString(R.string.napplet_consent_publish_encrypted)
            is NappletRequest.QueryEvents, is NappletRequest.Subscribe -> getString(R.string.napplet_consent_query)
            is NappletRequest.StorageGet, is NappletRequest.StorageSet, is NappletRequest.StorageRemove, is NappletRequest.StorageKeys ->
                getString(R.string.napplet_consent_storage)
            is NappletRequest.PayInvoice -> {
                val sats = runCatching { LnInvoiceUtil.getAmountInSats(request.invoice).toLong() }.getOrNull()
                if (sats != null) {
                    pluralStringRes(this, R.plurals.napplet_consent_pay_amount, sats.toInt(), sats)
                } else {
                    getString(R.string.napplet_consent_pay)
                }
            }
            is NappletRequest.ResourceBytes -> getString(R.string.napplet_consent_resource)
            is NappletRequest.UploadBlob -> getString(R.string.napplet_consent_upload)
            // Negotiation is resolved in the broker before consent; never shown to the user.
            is NappletRequest.ShellSupports -> ""
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
        private const val QUERY_TIMEOUT_MS = 8_000L
        private const val WALLET_TIMEOUT_MS = 60_000L
    }
}
