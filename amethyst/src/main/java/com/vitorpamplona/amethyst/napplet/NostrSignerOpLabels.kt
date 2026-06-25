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

/** Human-readable label for a [NostrSignerOp]. */
fun NostrSignerOp.label(context: Context): String =
    when (this) {
        is NostrSignerOp.SignKind -> context.getString(R.string.napplet_op_sign_kind, kind)
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
    val title = identity.identifier.ifBlank { context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8)) }
    val summary = op.label(context)
    val preview =
        when (request) {
            is NappletRequest.Publish -> request.content.take(160).trim()
            is NappletRequest.SignEvent -> request.content.take(160).trim()
            else -> ""
        }
    return NappletSignerConsentInfo(
        appletTitle = title,
        coordinate = identity.coordinate,
        op = op,
        operationSummary = summary,
        contentPreview = preview,
    )
}

/** Creates a [NappletConnectInfo] for the first-connect dialog. */
fun buildConnectInfo(
    context: Context,
    identity: NappletIdentity,
): NappletConnectInfo {
    val title = identity.identifier.ifBlank { context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8)) }
    val domain = identity.coordinate.substringBefore(":")
    return NappletConnectInfo(appletTitle = title, coordinate = identity.coordinate, domain = domain)
}
