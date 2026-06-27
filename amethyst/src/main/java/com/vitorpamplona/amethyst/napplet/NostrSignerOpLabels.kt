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
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerOp
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/** Human-readable label for a [NostrSignerOp]. */
fun NostrSignerOp.label(context: Context): String =
    when (this) {
        is NostrSignerOp.SignKind -> {
            val name = KindNames.nameFor(kind)
            if (name != null) {
                context.getString(R.string.napplet_op_sign_kind_named, name, kind)
            } else {
                context.getString(R.string.napplet_op_sign_kind, kind)
            }
        }
        NostrSignerOp.Encrypt -> context.getString(R.string.napplet_op_encrypt)
        NostrSignerOp.Decrypt -> context.getString(R.string.napplet_op_decrypt)
    }

/** Builds the [NappletSignerConsentInfo] needed by the per-op consent dialog. */
fun buildSignerConsentInfo(
    context: Context,
    identity: NappletIdentity,
    op: NostrSignerOp,
    request: NappletRequest,
): NappletSignerConsentInfo {
    val untitled = context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8))
    val (title, iconUrl) = resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
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
    return NappletSignerConsentInfo(
        appletTitle = title,
        coordinate = identity.coordinate,
        op = op,
        operationSummary = summary,
        contentPreview = preview,
        rawData = rawData,
        iconUrl = iconUrl,
    )
}

/** Creates a [NappletConnectInfo] for the first-connect dialog. */
fun buildConnectInfo(
    context: Context,
    identity: NappletIdentity,
): NappletConnectInfo {
    val untitled = context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8))
    val (title, iconUrl) = resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
    val domain = identity.identifier.ifBlank { identity.authorPubKey.take(12) + "…" }
    return NappletConnectInfo(appletTitle = title, coordinate = identity.coordinate, domain = domain, iconUrl = iconUrl)
}
