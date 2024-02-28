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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
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
    @Transient private var cachedMetadata: UserMetadata? = null

    fun appMetaData() =
        if (cachedMetadata != null) {
            cachedMetadata
        } else {
            try {
                val newMetadata =
                    mapper.readValue(
                        ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
                        UserMetadata::class.java,
                    )

                cachedMetadata = newMetadata

                newMetadata
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w("MT", "Content Parse Error ${e.localizedMessage} $content")
                null
            }
        }

    fun supportedKinds() =
        tags
            .filter { it.size > 1 && it[0] == "k" }
            .mapNotNull { runCatching { it[1].toInt() }.getOrNull() }

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
                    arrayOf("alt", "App definition event for ${details.name}"),
                )
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
