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
package com.vitorpamplona.quartz.nip01Core

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip39ExtIdentities.updateClaims
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayInputStream
import java.io.StringWriter

class MetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun contactMetaData() =
        try {
            EventMapper.mapper.readValue(content, UserMetadata::class.java)
        } catch (e: Exception) {
            // e.printStackTrace()
            Log.w("MetadataEvent", "Content Parse Error: ${toNostrUri()} ${e.localizedMessage}")
            null
        }

    companion object {
        const val KIND = 0

        fun newUser(
            name: String?,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): MetadataEvent? {
            // Tries to not delete any existing attribute that we do not work with.
            val currentJson = ObjectMapper().createObjectNode()

            name?.let { addIfNotBlank(currentJson, "name", it.trim()) }
            val writer = StringWriter()
            ObjectMapper().writeValue(writer, currentJson)

            val tags = mutableListOf<Array<String>>()

            tags.add(
                arrayOf("alt", "User profile for ${name ?: currentJson.get("name").asText() ?: ""}"),
            )

            return signer.sign(createdAt, KIND, tags.toTypedArray(), writer.buffer.toString())
        }

        fun updateFromPast(
            latest: MetadataEvent?,
            name: String?,
            picture: String?,
            banner: String?,
            website: String?,
            about: String?,
            nip05: String?,
            lnAddress: String?,
            lnURL: String?,
            pronouns: String?,
            twitter: String?,
            mastodon: String?,
            github: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MetadataEvent) -> Unit,
        ) {
            // Tries to not delete any existing attribute that we do not work with.
            val currentJson =
                if (latest != null) {
                    ObjectMapper()
                        .readTree(
                            ByteArrayInputStream(latest.content.toByteArray(Charsets.UTF_8)),
                        ) as ObjectNode
                } else {
                    ObjectMapper().createObjectNode()
                }

            name?.let { addIfNotBlank(currentJson, "name", it.trim()) }
            name?.let { addIfNotBlank(currentJson, "display_name", it.trim()) }
            picture?.let { addIfNotBlank(currentJson, "picture", it.trim()) }
            banner?.let { addIfNotBlank(currentJson, "banner", it.trim()) }
            website?.let { addIfNotBlank(currentJson, "website", it.trim()) }
            pronouns?.let { addIfNotBlank(currentJson, "pronouns", it.trim()) }
            about?.let { addIfNotBlank(currentJson, "about", it.trim()) }
            nip05?.let { addIfNotBlank(currentJson, "nip05", it.trim()) }
            lnAddress?.let { addIfNotBlank(currentJson, "lud16", it.trim()) }
            lnURL?.let { addIfNotBlank(currentJson, "lud06", it.trim()) }

            val writer = StringWriter()
            ObjectMapper().writeValue(writer, currentJson)

            val tags = mutableListOf<Array<String>>()
            tags.add(arrayOf("alt", "User profile for ${name ?: currentJson.get("name").asText() ?: ""}"))

            latest?.updateClaims(twitter, github, mastodon)?.forEach {
                tags.add(it)
            }

            signer.sign(createdAt, KIND, tags.toTypedArray(), writer.buffer.toString(), onReady)
        }

        private fun addIfNotBlank(
            currentJson: ObjectNode,
            key: String,
            value: String,
        ) {
            if (value.isBlank() || value == "null") {
                currentJson.remove(key)
            } else {
                currentJson.put(key, value.trim())
            }
        }
    }
}
