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
package com.vitorpamplona.amethyst.napplet.gateways

import android.content.Context
import android.content.res.Configuration
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentityGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletNotification
import com.vitorpamplona.amethyst.commons.napplet.NappletNotifyGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletResourceGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadResult
import com.vitorpamplona.amethyst.commons.napplet.NappletWalletGateway
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.napplet.NappletConsentCoordinator
import com.vitorpamplona.amethyst.napplet.NappletConsentSummary
import com.vitorpamplona.amethyst.napplet.NappletNotificationStore
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

/**
 * The account adapter: turns a signed-in [Account] into a configured [NappletBroker] by wiring the
 * platform gateway implementations (relay publish/query, consent, wallet/NWC, resource fetch,
 * identity reads, blob upload). The broker itself owns the trust boundary; this class only supplies
 * the Android/account-backed plumbing. Built once per account and cached by the caller.
 */
class AccountNappletGateways(
    private val account: Account,
    private val context: Context,
    private val ledger: NappletPermissionLedger,
    private val storage: NappletStorage,
    private val torPort: () -> Int,
) {
    private val consentSummary = NappletConsentSummary(context)
    private val resourceFetcher = NappletResourceFetcher(account, torPort)
    private val identityReader = AccountIdentityReader(account)

    fun broker(): NappletBroker {
        val relay =
            object : NappletRelayGateway {
                override suspend fun publish(event: Event): List<String> {
                    val relays = account.computeRelayListToBroadcast(event)
                    account.client.publish(event, relays)
                    return relays.map { it.url }
                }

                override suspend fun query(filters: List<Filter>): List<Event> = queryEvents(filters)
            }

        val consent =
            NappletConsentPrompt { identity, capability, request ->
                NappletConsentCoordinator.requestConsent(
                    context = context,
                    info = consentSummary.info(identity, capability, request),
                )
            }

        val wallet = NappletWalletGateway { invoice -> payInvoiceViaNwc(invoice) }
        val resource = NappletResourceGateway { url -> resourceFetcher.fetch(url) }
        val identityReads = NappletIdentityGateway { method, argument -> identityReader.read(method, argument) }
        val upload = NappletUploadGateway { bytes, contentType, filename -> uploadBlob(bytes, contentType, filename) }
        val theme = NappletThemeGateway { currentThemeColors() }
        val notify =
            object : NappletNotifyGateway {
                override suspend fun create(
                    coordinate: String,
                    title: String,
                    body: String,
                ): String {
                    val id = NappletNotificationStore.create(coordinate, title, body)
                    runCatching { postSystemNotification(id, title, body) }
                    return id
                }

                override suspend fun list(coordinate: String): List<NappletNotification> = NappletNotificationStore.list(coordinate)

                override suspend fun dismiss(
                    coordinate: String,
                    id: String,
                ) {
                    NappletNotificationStore.dismiss(coordinate, id)
                    runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) }
                }
            }

        return NappletBroker(account.signer, ledger, consent, relay, storage, wallet, resource, upload = upload, identityReads = identityReads, theme = theme, notify = notify)
    }

    /**
     * Posts a napplet's notification to the system tray (best-effort: a missing POST_NOTIFICATIONS
     * grant just no-ops). The in-app registry in [NappletNotificationStore] is the source of truth for
     * the napplet's own `notify.list`/`dismiss`; this is the user-visible surface.
     */
    private fun postSystemNotification(
        id: String,
        title: String,
        body: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channel =
            NotificationChannelCompat
                .Builder(NOTIFY_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.napplet_cap_notify))
                .build()
        manager.createNotificationChannel(channel)
        val notification =
            NotificationCompat
                .Builder(context, NOTIFY_CHANNEL_ID)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(title.ifBlank { context.getString(R.string.napplet_cap_notify) })
                .setContentText(body)
                .setAutoCancel(true)
                .build()
        try {
            manager.notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Best effort: if the user revoked permission between the check and here, just no-op.
        }
    }

    /**
     * The host theme colors a napplet maps to its CSS variables (`theme.get`): Amethyst's brand
     * purple plus a background/text pair chosen from the current dark/light mode. Read-only and
     * cheap; the napplet falls back to its own defaults if this is ever unavailable.
     */
    private fun currentThemeColors(): NappletThemeColors {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            NappletThemeColors(background = "#0E0E10", text = "#E6E6E6", primary = AMETHYST_PURPLE)
        } else {
            NappletThemeColors(background = "#FFFFFF", text = "#1A1A1A", primary = AMETHYST_PURPLE)
        }
    }

    /**
     * Uploads [bytes] to the user's first Blossom server (kind:10063) with a signed authorization
     * event, via the app's existing [BlossomUploader]. Returns null when there's no server or the
     * upload fails. Consent is enforced by the broker before this runs.
     */
    private suspend fun uploadBlob(
        bytes: ByteArray,
        contentType: String,
        filename: String?,
    ): NappletUploadResult? {
        val server =
            account.blossomServers
                .getBlossomServersList()
                ?.servers()
                ?.firstOrNull() ?: return null
        val hash = sha256(bytes).toHexKey()
        val result =
            runCatching {
                BlossomUploader().upload(
                    inputStream = ByteArrayInputStream(bytes),
                    hash = hash,
                    length = bytes.size.toLong(),
                    baseFileName = filename,
                    contentType = contentType,
                    alt = null,
                    sensitiveContent = null,
                    serverBaseUrl = server,
                    okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                    httpAuth = { h, size, alt -> account.createBlossomUploadAuth(h, size, alt) },
                    context = context,
                )
            }.getOrNull() ?: return null
        val url = result.url ?: return null
        return NappletUploadResult(url, result.sha256, result.size, result.type)
    }

    /** Bounded live relay fetch (EOSE/timeout) for all [filters], merged with the local cache, newest-first. */
    private suspend fun queryEvents(filters: List<Filter>): List<Event> {
        if (filters.isEmpty()) return emptyList()
        val relays = account.homeRelays.flow.value
        val fromRelays =
            if (relays.isEmpty()) {
                emptyList()
            } else {
                runCatching {
                    account.client.fetchAll(filters = relays.associateWith { filters }, timeoutMs = QUERY_TIMEOUT.inWholeMilliseconds)
                }.getOrDefault(emptyList())
            }
        val fromCache = filters.flatMap { filter -> account.cache.filter(filter).mapNotNull { it.event } }

        val merged =
            (fromRelays + fromCache)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
        val limit = filters.mapNotNull { it.limit }.maxOrNull()
        return limit?.let { merged.take(it) } ?: merged
    }

    /**
     * Pays [invoice] via the user's connected NWC wallet, returning the preimage on success.
     * Throws (→ `Failed`) when no wallet is connected, the wallet reports an error, or it does not
     * respond in time — so the applet never silently believes a payment succeeded.
     */
    private suspend fun payInvoiceViaNwc(invoice: String): String? {
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
        return withTimeout(WALLET_TIMEOUT) { result.await() }
    }

    companion object {
        private val QUERY_TIMEOUT = 8.seconds
        private val WALLET_TIMEOUT = 60.seconds

        /** Amethyst's brand purple (`AmethystPurple`, commons Colors.kt), exposed as the theme primary. */
        private const val AMETHYST_PURPLE = "#9A82DB"

        /** Notification channel for napplet-originated notifications (NAP `notify`). */
        private const val NOTIFY_CHANNEL_ID = "napplet_notifications"
    }
}
