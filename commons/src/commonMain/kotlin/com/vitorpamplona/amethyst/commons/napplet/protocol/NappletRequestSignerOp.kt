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
package com.vitorpamplona.amethyst.commons.napplet.protocol

import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp

/**
 * Maps a [NappletRequest] to the [NostrSignerOp] it represents, or `null` if the request
 * does not involve signing or encryption.
 *
 * This lives on the napplet side (not in the generic signer-permission layer) because it
 * is the napplet protocol's own translation into the shared [NostrSignerOp] vocabulary.
 */
fun NappletRequest.toSignerOp(): NostrSignerOp? =
    when (this) {
        is NappletRequest.Publish -> NostrSignerOp.SignKind(kind)
        is NappletRequest.SignEvent -> NostrSignerOp.SignKind(kind)
        is NappletRequest.PublishEncrypted -> NostrSignerOp.Encrypt
        is NappletRequest.Nip44Encrypt -> NostrSignerOp.Encrypt
        // The broad grant, matching what NIP-46's nip44_decrypt maps to. The narrower
        // DecryptFrom(peer) exists, but only the NIP-46 authorizer honours it today; recording one
        // here would be a grant this broker never reads back, so the user would re-prompt forever.
        is NappletRequest.Nip44Decrypt -> NostrSignerOp.Decrypt
        else -> null
    }
