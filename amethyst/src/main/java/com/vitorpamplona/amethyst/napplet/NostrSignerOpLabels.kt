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
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.favorites.BrowserIconRegistry
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.kindNameFor
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.utils.TimeUtils

/** Human-readable label for a [NostrSignerOp]. */
fun NostrSignerOp.label(context: Context): String =
    when (this) {
        is NostrSignerOp.SignKind -> context.getString(R.string.napplet_op_sign_kind_named, kindNameFor(context, kind), kind)
        NostrSignerOp.Encrypt -> context.getString(R.string.napplet_op_encrypt)
        NostrSignerOp.Decrypt -> context.getString(R.string.napplet_op_decrypt)
        is NostrSignerOp.DecryptFrom -> context.getString(R.string.napplet_op_decrypt_from, counterpartyLabel(counterparty))
    }

/**
 * A person's display name for a consent prompt: their profile name when we have it cached, otherwise
 * a shortened npub. Never empty — "read your private messages with <nothing>" would be worse than the
 * broad wording it replaces.
 */
fun counterpartyLabel(pubKeyHex: HexKey): String {
    LocalCache
        .getUserIfExists(pubKeyHex)
        ?.toBestDisplayName()
        ?.ifBlank { null }
        ?.let { return it }
    val npub = runCatching { NPub.create(pubKeyHex) }.getOrNull()
    return if (npub != null) npub.take(12) + "…" else pubKeyHex.take(12) + "…"
}

/** Builds the [SignerConsentInfo] needed by the per-op consent dialog. */
fun buildSignerConsentInfo(
    context: Context,
    identity: NappletIdentity,
    op: NostrSignerOp,
    request: NappletRequest,
): SignerConsentInfo {
    val untitled = context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8))
    val (title, iconUrl) =
        if (identity.authorPubKey == "browser") {
            val host = OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
            host to BrowserIconRegistry.iconModelFor(host)
        } else {
            resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
        }
    val summary = op.label(context)
    val preview =
        when (request) {
            is NappletRequest.Publish -> request.content.take(160).trim()
            is NappletRequest.SignEvent -> request.content.take(160).trim()
            is NappletRequest.PublishEncrypted -> request.content.take(160).trim()
            else -> ""
        }
    val rawData =
        when (request) {
            is NappletRequest.Publish ->
                JacksonMapper.toJsonPretty(EventTemplate<Nothing>(TimeUtils.now(), request.kind, request.tags, request.content))
            is NappletRequest.SignEvent ->
                JacksonMapper.toJsonPretty(EventTemplate<Nothing>(request.createdAt, request.kind, request.tags, request.content))
            is NappletRequest.PublishEncrypted -> {
                val node = JacksonMapper.mapper.createObjectNode()
                node.put("kind", request.kind)
                node.put("recipient", request.recipient)
                node.put("encryption", request.encryption)
                val tagsNode = node.putArray("tags")
                for (tag in request.tags) {
                    val tagNode = tagsNode.addArray()
                    for (item in tag) tagNode.add(item)
                }
                node.put("content", request.content)
                JacksonMapper.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
            }
            else -> ""
        }
    val previewTemplate =
        when (request) {
            is NappletRequest.Publish -> EventTemplate<Event>(TimeUtils.now(), request.kind, request.tags, request.content)
            is NappletRequest.SignEvent -> EventTemplate<Event>(request.createdAt, request.kind, request.tags, request.content)
            else -> null
        }
    return SignerConsentInfo(
        appletTitle = title,
        coordinate = identity.coordinate,
        op = op,
        operationSummary = summary,
        contentPreview = preview,
        rawData = rawData,
        iconUrl = iconUrl,
        previewTemplate = previewTemplate,
    )
}

/**
 * Creates a [SignerConnectInfo] for the first-connect dialog. [declared] is the capability set the
 * connection pre-grants as ALLOW_ALWAYS on accept, so it is surfaced as
 * [SignerConnectInfo.requestedPermissions] — otherwise the dialog would be asking the user to
 * approve a set it never showed them.
 */
fun buildConnectInfo(
    context: Context,
    identity: NappletIdentity,
    declared: Set<NappletCapability> = emptySet(),
): SignerConnectInfo {
    val untitled = context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8))
    val (title, iconUrl) =
        if (identity.authorPubKey == "browser") {
            val host = OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
            host to BrowserIconRegistry.iconModelFor(host)
        } else {
            resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
        }
    val domain =
        if (identity.authorPubKey == "browser") {
            OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
        } else {
            identity.identifier.ifBlank { identity.authorPubKey.take(12) + "…" }
        }
    // Only the capabilities that actually get pre-granted are listed; SHELL/THEME never prompt and
    // VALUE is per-use, so listing them would overstate what accepting hands over.
    val preGranted =
        declared
            .filter { it.requiresConsent && !it.requiresPerUseConsent }
            .map { context.getString(it.labelRes()) }
            .sorted()
    return SignerConnectInfo(
        appletTitle = title,
        coordinate = identity.coordinate,
        domain = domain,
        iconUrl = iconUrl,
        requestedPermissions = preGranted,
    )
}
