/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip89AppHandlers

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.UserMetadata
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayInputStream

@Immutable
class AppDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun countMemory(): Long = super.countMemory() + (cachedMetadata?.countMemory() ?: 8L)

    @Transient private var cachedMetadata: AppMetadata? = null

    fun appMetaData() =
        if (cachedMetadata != null) {
            cachedMetadata
        } else {
            try {
                val newMetadata =
                    EventMapper.mapper.readValue(
                        ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
                        AppMetadata::class.java,
                    )

                cachedMetadata = newMetadata

                newMetadata
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w("AppDefinitionEvent", "Content Parse Error: ${toNostrUri()} ${e.localizedMessage}")
                null
            }
        }

    fun supportedKinds() =
        tags
            .filter { it.size > 1 && it[0] == "k" }
            .mapNotNull { runCatching { it[1].toInt() }.getOrNull() }

    fun includeKind(kind: String) = tags.any { it.size > 1 && it[0] == "k" && it[1] == kind }

    fun publishedAt() = tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)

    companion object {
        const val KIND = 31990

        fun create(
            details: UserMetadata,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AppDefinitionEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    AltTagSerializer.toTagArray("App definition event for ${details.name}"),
                )
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
