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
package com.vitorpamplona.quartz.nip51Lists.originlessServers

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.encryption.signNip51List
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip96FileStorage.config.tags.ServerTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 10062 — the user's Originless node list.
 *
 * Originless nodes are often LAN/Umbrel URLs with an unauthenticated
 * `POST /upload`, so the `server` tags live only in the NIP-51 private
 * (NIP-44 encrypted) content. Public tags stay empty.
 */
@Immutable
class OriginlessServersEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun publicServers() = tags.mapNotNull(ServerTag::parse)

    suspend fun privateServers(signer: NostrSigner) = privateTags(signer)?.mapNotNull(ServerTag::parse)

    /** Private `server` tags only. Public tags are never a source of nodes. */
    suspend fun servers(signer: NostrSigner): List<String> = privateServers(signer) ?: emptyList()

    companion object {
        const val KIND = 10062
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        suspend fun updateServerList(
            earlierVersion: OriginlessServersEvent,
            servers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): OriginlessServersEvent {
            val newServerList = servers.map { ServerTag.assemble(it) }
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()

            val publicTags = earlierVersion.tags.remove(ServerTag::isTag)
            val newPrivateTags = privateTags.remove(ServerTag::isTag).plus(newServerList)

            return signer.signNip51List(createdAt, KIND, publicTags, newPrivateTags)
        }

        suspend fun create(
            servers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): OriginlessServersEvent {
            val privateTagArray = servers.map { ServerTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, emptyArray(), privateTagArray)
        }

        fun create(
            servers: List<String>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): OriginlessServersEvent {
            val privateTagArray = servers.map { ServerTag.assemble(it) }.toTypedArray()
            return signer.signNip51List(createdAt, KIND, emptyArray(), privateTagArray)
        }

        suspend fun build(
            servers: List<String> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<OriginlessServersEvent>.() -> Unit = {},
        ) = eventTemplate<OriginlessServersEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(servers.map { ServerTag.assemble(it) }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            initializer()
        }
    }
}
