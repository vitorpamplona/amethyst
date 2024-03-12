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
import androidx.compose.runtime.Stable
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayInputStream
import java.io.StringWriter

@Stable
abstract class IdentityClaim(
    val identity: String,
    val proof: String,
) {
    abstract fun toProofUrl(): String

    abstract fun platform(): String

    fun platformIdentity() = "${platform()}:$identity"

    companion object {
        fun create(
            platformIdentity: String,
            proof: String,
        ): IdentityClaim? {
            val (platform, identity) = platformIdentity.split(':')

            return when (platform.lowercase()) {
                GitHubIdentity.platform -> GitHubIdentity(identity, proof)
                TwitterIdentity.platform -> TwitterIdentity(identity, proof)
                TelegramIdentity.platform -> TelegramIdentity(identity, proof)
                MastodonIdentity.platform -> MastodonIdentity(identity, proof)
                else -> throw IllegalArgumentException("Platform $platform not supported")
            }
        }
    }
}

class GitHubIdentity(
    identity: String,
    proof: String,
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://gist.github.com/$identity/$proof"

    override fun platform() = platform

    companion object {
        val platform = "github"

        fun parseProofUrl(proofUrl: String): GitHubIdentity? {
            return try {
                if (proofUrl.isBlank()) return null
                val path = proofUrl.removePrefix("https://gist.github.com/").split("?")[0].split("/")

                GitHubIdentity(path[0], path[1])
            } catch (e: Exception) {
                null
            }
        }
    }
}

class TwitterIdentity(
    identity: String,
    proof: String,
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://twitter.com/$identity/status/$proof"

    override fun platform() = platform

    companion object {
        val platform = "twitter"

        fun parseProofUrl(proofUrl: String): TwitterIdentity? {
            return try {
                if (proofUrl.isBlank()) return null
                val path = proofUrl.removePrefix("https://twitter.com/").split("?")[0].split("/")

                TwitterIdentity(path[0], path[2])
            } catch (e: Exception) {
                null
            }
        }
    }
}

class TelegramIdentity(
    identity: String,
    proof: String,
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://t.me/$proof"

    override fun platform() = platform

    companion object {
        val platform = "telegram"
    }
}

class MastodonIdentity(
    identity: String,
    proof: String,
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://$identity/$proof"

    override fun platform() = platform

    companion object {
        val platform = "mastodon"

        fun parseProofUrl(proofUrl: String): MastodonIdentity? {
            return try {
                if (proofUrl.isBlank()) return null
                val path = proofUrl.removePrefix("https://").split("?")[0].split("/")

                return MastodonIdentity("${path[0]}/${path[1]}", path[2])
            } catch (e: Exception) {
                null
            }
        }
    }
}

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
            mapper.readValue(content, UserMetadata::class.java)
        } catch (e: Exception) {
            // e.printStackTrace()
            Log.w("MT", "Content Parse Error: ${e.localizedMessage} $content")
            null
        }

    fun identityClaims() =
        tags
            .filter { it.firstOrNull() == "i" }
            .mapNotNull {
                try {
                    IdentityClaim.create(it.get(1), it.get(2))
                } catch (e: Exception) {
                    Log.e("MetadataEvent", "Can't parse identity [${it.joinToString { "," }}]", e)
                    null
                }
            }

    companion object {
        const val KIND = 0

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
            about?.let { addIfNotBlank(currentJson, "about", it.trim()) }
            nip05?.let { addIfNotBlank(currentJson, "nip05", it.trim()) }
            lnAddress?.let { addIfNotBlank(currentJson, "lud16", it.trim()) }
            lnURL?.let { addIfNotBlank(currentJson, "lud06", it.trim()) }

            var claims = latest?.identityClaims() ?: emptyList()

            if (twitter?.isBlank() == true) {
                // delete twitter
                claims = claims.filter { it !is TwitterIdentity }
            }

            if (github?.isBlank() == true) {
                // delete github
                claims = claims.filter { it !is GitHubIdentity }
            }

            if (mastodon?.isBlank() == true) {
                // delete mastodon
                claims = claims.filter { it !is MastodonIdentity }
            }

            // Updates while keeping other identities intact
            val newClaims =
                listOfNotNull(
                    twitter?.let { TwitterIdentity.parseProofUrl(it) },
                    github?.let { GitHubIdentity.parseProofUrl(it) },
                    mastodon?.let { MastodonIdentity.parseProofUrl(it) },
                ) +
                    claims.filter { it !is TwitterIdentity && it !is GitHubIdentity && it !is MastodonIdentity }

            val writer = StringWriter()
            ObjectMapper().writeValue(writer, currentJson)

            val tags = mutableListOf<Array<String>>()

            tags.add(
                arrayOf("alt", "User profile for ${name ?: currentJson.get("name").asText() ?: ""}"),
            )

            newClaims.forEach { tags.add(arrayOf("i", it.platformIdentity(), it.proof)) }

            signer.sign(createdAt, KIND, tags.toTypedArray(), writer.buffer.toString(), onReady)
        }

        private fun addIfNotBlank(
            currentJson: ObjectNode,
            key: String,
            value: String,
        ) {
            if (value.isBlank()) {
                currentJson.remove(key)
            } else {
                currentJson.put(key, value.trim())
            }
        }

        fun createFromScratch(
            newName: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MetadataEvent) -> Unit,
        ) {
            val prop = ObjectMapper().createObjectNode()
            prop.put("name", newName.trim())

            val writer = StringWriter()
            ObjectMapper().writeValue(writer, prop)

            val tags = mutableListOf<Array<String>>()

            tags.add(
                arrayOf("alt", "User profile for $newName"),
            )

            signer.sign(createdAt, KIND, tags.toTypedArray(), writer.buffer.toString(), onReady)
        }
    }
}
