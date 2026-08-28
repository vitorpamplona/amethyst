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
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Trusted List entries in a Treasure Map, public and private alike.
 *
 * A 10040 keeps half its delegations in `content`, NIP-44 encrypted to the
 * owner -- who you trust to rank the network is itself sensitive -- so the
 * public tags are only half the Map. Reading both takes the owner's signer;
 * with anyone else's, or a Map whose private half will not decrypt, this sees
 * the public half alone rather than failing, matching
 * [TrustProviderListEvent.privateTags].
 *
 * The parsing itself is [TagArray]-level and half-agnostic: a caller holding
 * an already-merged array (commons' `PrivateTagArrayEventCache`, which caches
 * the decryption) gets private entries out of [TagArray.trustedListProviders]
 * with no extra work. These are the convenience accessors for callers that
 * hold the event and a signer instead.
 *
 * Named entries are reserved, so a caller that intends to *act* on a
 * delegation wants [trustedListProvider] -- this is the display-everything
 * view.
 */
suspend fun TrustProviderListEvent.trustedListProviders(signer: NostrSigner) = mergedTags(signer).trustedListProviders()

/**
 * The publisher this Map delegates all its [kind] lists to, if any, across
 * both halves.
 *
 * Public tags are searched before private ones, so where a Map violates the
 * one-entry-per-kind invariant across the two halves, the public entry is the
 * one that wins.
 */
suspend fun TrustProviderListEvent.trustedListProvider(
    kind: Int,
    signer: NostrSigner,
) = mergedTags(signer).trustedListProvider(kind)

/** The public half alone -- no signer, so no private entries. */
fun TrustProviderListEvent.publicTrustedListProviders() = tags.trustedListProviders()

/** The public half alone -- no signer, so no private entries. */
fun TrustProviderListEvent.publicTrustedListProvider(kind: Int) = tags.trustedListProvider(kind)

private suspend fun TrustProviderListEvent.mergedTags(signer: NostrSigner): TagArray = tags + (privateTags(signer) ?: emptyArray())

/**
 * Republishes the Map with [provider] as the generic entry for its kind, in
 * the half [isPrivate] selects.
 *
 * At most one generic entry per kind is the invariant, and it spans **both**
 * halves -- so this also drops the entry from the other half. That is what
 * makes moving a delegation between public and private a single call rather
 * than a two-step that leaves a stale twin behind, shadowed on read and
 * republished forever after.
 *
 * The cost is that a Map with a private half must be decryptable even for a
 * public write: we cannot drop a private twin we cannot read.
 * [SignerExceptions.UnauthorizedDecryptionException] is thrown rather than
 * silently writing a Map that breaks the invariant. A Map with no private half
 * (blank `content`) needs no decryption either way.
 *
 * Every other tag survives verbatim in both halves. 10040 is replaceable, so
 * the update republishes the whole event and anything dropped here is gone
 * from the Map for good.
 */
suspend fun TrustProviderListEvent.replaceTrustedListProvider(
    provider: TrustedListProviderTag,
    isPrivate: Boolean = false,
    signer: NostrSigner,
    createdAt: Long = TimeUtils.now(),
): TrustProviderListEvent {
    val privateTags = readPrivateTagsForWrite(signer)

    return if (isPrivate) {
        resignBothHalves(
            publicTags = tags.removeTrustedListProvider(provider.kind),
            privateTags = privateTags.replaceTrustedListProvider(provider),
            signer = signer,
            createdAt = createdAt,
        )
    } else {
        resignBothHalves(
            publicTags = tags.replaceTrustedListProvider(provider),
            privateTags = privateTags.removeTrustedListProvider(provider.kind),
            signer = signer,
            createdAt = createdAt,
        )
    }
}

suspend fun TrustProviderListEvent.replaceTrustedListProvider(
    kind: Int,
    pubkey: HexKey,
    relayUrl: NormalizedRelayUrl? = null,
    isPrivate: Boolean = false,
    signer: NostrSigner,
    createdAt: Long = TimeUtils.now(),
): TrustProviderListEvent = replaceTrustedListProvider(TrustedListProviderTag(kind, null, pubkey, relayUrl), isPrivate, signer, createdAt)

/** Republishes the Map without its generic entry for [kind], in either half. */
suspend fun TrustProviderListEvent.removeTrustedListProvider(
    kind: Int,
    signer: NostrSigner,
    createdAt: Long = TimeUtils.now(),
): TrustProviderListEvent =
    resignBothHalves(
        publicTags = tags.removeTrustedListProvider(kind),
        privateTags = readPrivateTagsForWrite(signer).removeTrustedListProvider(kind),
        signer = signer,
        createdAt = createdAt,
    )

/**
 * The private half as a writer must see it: empty when the Map has none, and
 * an error rather than a guess when it has one we cannot open.
 */
private suspend fun TrustProviderListEvent.readPrivateTagsForWrite(signer: NostrSigner): TagArray {
    if (content.isBlank()) return emptyArray()
    return privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
}

private suspend fun resignBothHalves(
    publicTags: TagArray,
    privateTags: TagArray,
    signer: NostrSigner,
    createdAt: Long,
): TrustProviderListEvent =
    TrustProviderListEvent.resign(
        content = if (privateTags.isEmpty()) "" else PrivateTagsInContent.encryptNip44(privateTags, signer),
        tags = publicTags,
        signer = signer,
        createdAt = createdAt,
    )
