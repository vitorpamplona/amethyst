/**
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
package com.vitorpamplona.quartz.nip37Drafts

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.core.JsonParseException
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kind
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DraftWrapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun isDeleted() = content == ""

    fun canDecrypt(signer: NostrSigner) = signer.pubKey == pubKey

    suspend fun decryptInnerEvent(signer: NostrSigner): Event {
        if (!canDecrypt(signer)) throw SignerExceptions.UnauthorizedDecryptionException()

        val json = signer.nip44Decrypt(content, pubKey)
        return try {
            fromJson(json)
        } catch (e: JsonParseException) {
            Log.w("DraftEvent", "Unable to parse inner event of a draft: $json")
            throw e
        }
    }

    companion object {
        const val KIND = 31234
        const val ALT_DESCRIPTION = "Draft Event"

        fun createAddress(
            pubKey: HexKey,
            dTag: String,
        ): Address = Address(KIND, pubKey, dTag)

        fun createAddressTag(
            pubKey: HexKey,
            dTag: String,
        ): String = Address.assemble(KIND, pubKey, dTag)

        suspend fun build(
            dTag: String,
            draft: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DraftWrapEvent>.() -> Unit = {},
        ) = eventTemplate(
            kind = KIND,
            description = signer.nip44Encrypt(draft.toJson(), signer.pubKey),
            createdAt = createdAt,
        ) {
            alt(ALT_DESCRIPTION)
            dTag(dTag)
            kind(draft.kind)

            initializer()
        }

        suspend fun buildDeleted(
            dTag: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DraftWrapEvent>.() -> Unit = {},
        ) = eventTemplate(
            kind = KIND,
            description = "",
            createdAt = createdAt,
        ) {
            alt(ALT_DESCRIPTION)
            dTag(dTag)
            initializer()
        }

        suspend fun create(
            dTag: String,
            draft: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = signer.sign(build(dTag, draft, signer, createdAt))

        suspend fun createDeletedEvent(
            dTag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftWrapEvent = signer.sign(buildDeleted(dTag, createdAt))
    }
}
