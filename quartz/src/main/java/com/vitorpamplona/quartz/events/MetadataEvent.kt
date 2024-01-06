/**
 * Copyright (c) 2023 Vitor Pamplona
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
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayInputStream

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
            mapper.readValue(
                ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
                UserMetadata::class.java,
            )
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

        fun create(
            contactMetaData: String,
            newName: String,
            identities: List<IdentityClaim>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MetadataEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()

            tags.add(
                arrayOf("alt", "User profile for $newName"),
            )

            identities.forEach { tags.add(arrayOf("i", it.platformIdentity(), it.proof)) }

            signer.sign(createdAt, KIND, tags.toTypedArray(), contactMetaData, onReady)
        }
    }
}
