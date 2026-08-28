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
package com.vitorpamplona.quartz.experimental.trustedLists.treasureMap

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The Trusted List entries this Map carries, generic and named alike.
 *
 * These are extensions rather than members of [TrustProviderListEvent] on
 * purpose: the entry is a pre-NIP Tapestry extension riding on NIP-85's kind,
 * so the NIP-85 event class stays unaware of it and a consumer opts in by
 * importing this package.
 */
fun TrustProviderListEvent.trustedListProviders() = tags.trustedListProviders()

/** The publisher this Map delegates all its [kind] lists to, if any. */
fun TrustProviderListEvent.trustedListProvider(kind: Int) = tags.trustedListProvider(kind)

/**
 * Republishes the Map with [provider] as the generic entry for its kind.
 *
 * Every other tag survives verbatim, and `content` -- the NIP-44 envelope
 * holding the private entries -- is carried across untouched rather than
 * re-encrypted, so this needs no decryption permission from the signer.
 */
suspend fun TrustProviderListEvent.replaceTrustedListProvider(
    provider: TrustedListProviderTag,
    signer: NostrSigner,
    createdAt: Long = TimeUtils.now(),
): TrustProviderListEvent =
    TrustProviderListEvent.resign(
        content = content,
        tags = tags.replaceTrustedListProvider(provider),
        signer = signer,
        createdAt = createdAt,
    )

suspend fun TrustProviderListEvent.replaceTrustedListProvider(
    kind: Int,
    pubkey: HexKey,
    relayUrl: NormalizedRelayUrl? = null,
    signer: NostrSigner,
    createdAt: Long = TimeUtils.now(),
): TrustProviderListEvent = replaceTrustedListProvider(TrustedListProviderTag(kind, null, pubkey, relayUrl), signer, createdAt)

/** Republishes the Map without its generic entry for [kind]. */
suspend fun TrustProviderListEvent.removeTrustedListProvider(
    kind: Int,
    signer: NostrSigner,
    createdAt: Long = TimeUtils.now(),
): TrustProviderListEvent =
    TrustProviderListEvent.resign(
        content = content,
        tags = tags.removeTrustedListProvider(kind),
        signer = signer,
        createdAt = createdAt,
    )
