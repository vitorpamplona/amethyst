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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil

/**
 * Turns a pending [NappletRequest] into the human-readable [NappletConsentInfo] the consent dialog
 * shows — the applet's title, the capability label, and a per-operation summary (e.g. a note preview
 * or a sat amount). Localized via app resources; holds only a [Context], no account state.
 */
class NappletConsentSummary(
    private val context: Context,
) {
    fun info(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): NappletConsentInfo {
        val untitled = context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8))
        val (title, iconUrl) = resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
        return NappletConsentInfo(
            appletTitle = title,
            coordinate = identity.coordinate,
            capabilityLabel = context.getString(capability.labelRes()),
            operationSummary = summaryFor(request),
            allowAlways = capability.canGrantAlways,
            iconUrl = iconUrl,
        )
    }

    private fun summaryFor(request: NappletRequest): String =
        when (request) {
            is NappletRequest.GetPublicKey -> context.getString(R.string.napplet_consent_get_pubkey)
            is NappletRequest.IdentityRead -> context.getString(R.string.napplet_consent_identity_read)
            is NappletRequest.Publish -> {
                val preview = request.content.take(160).trim()
                if (preview.isEmpty()) {
                    context.getString(R.string.napplet_consent_publish, request.kind)
                } else {
                    context.getString(R.string.napplet_consent_publish_preview, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.SignEvent -> {
                val preview = request.content.take(160).trim()
                if (preview.isEmpty()) {
                    context.getString(R.string.napplet_consent_sign, request.kind)
                } else {
                    context.getString(R.string.napplet_consent_sign, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.PublishEncrypted -> context.getString(R.string.napplet_consent_publish_encrypted)
            is NappletRequest.QueryEvents, is NappletRequest.Subscribe -> context.getString(R.string.napplet_consent_query)
            is NappletRequest.StorageGet, is NappletRequest.StorageSet, is NappletRequest.StorageRemove, is NappletRequest.StorageKeys ->
                context.getString(R.string.napplet_consent_storage)
            is NappletRequest.NotifyCreate -> {
                val preview = request.title.take(80).trim()
                if (preview.isEmpty()) context.getString(R.string.napplet_consent_notify) else context.getString(R.string.napplet_consent_notify) + "\n“$preview”"
            }
            is NappletRequest.NotifyList, is NappletRequest.NotifyDismiss -> context.getString(R.string.napplet_consent_notify)
            is NappletRequest.PayInvoice -> {
                val sats = runCatching { LnInvoiceUtil.getAmountInSats(request.invoice).toLong() }.getOrNull()
                if (sats != null) {
                    pluralStringRes(context, R.plurals.napplet_consent_pay_amount, sats.toInt(), sats)
                } else {
                    context.getString(R.string.napplet_consent_pay)
                }
            }
            is NappletRequest.ResourceBytes -> context.getString(R.string.napplet_consent_resource)
            is NappletRequest.UploadBlob -> context.getString(R.string.napplet_consent_upload)
            // Resolved in the broker before consent (negotiation / shell-mediated / cosmetic); never shown.
            is NappletRequest.ShellSupports, is NappletRequest.RegisterAction, is NappletRequest.UnregisterAction, is NappletRequest.ThemeGet -> ""
        }
}
