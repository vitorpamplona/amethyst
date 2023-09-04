package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import java.io.ByteArrayInputStream

@Stable
abstract class IdentityClaim(
    val identity: String,
    val proof: String
) {
    abstract fun toProofUrl(): String
    abstract fun platform(): String

    fun platformIdentity() = "${platform()}:$identity"

    companion object {
        fun create(platformIdentity: String, proof: String): IdentityClaim? {
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
    proof: String
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
    proof: String
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
    proof: String
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://t.me/$proof"

    override fun platform() = platform

    companion object {
        val platform = "telegram"
    }
}

class MastodonIdentity(
    identity: String,
    proof: String
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
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun contactMetaData() = try {
        mapper.readValue(
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
            UserMetadata::class.java
        )
    } catch (e: Exception) {
        // e.printStackTrace()
        Log.w("MT", "Content Parse Error: ${e.localizedMessage} $content")
        null
    }

    fun identityClaims() = tags.filter { it.firstOrNull() == "i" }.mapNotNull {
        try {
            IdentityClaim.create(it.get(1), it.get(2))
        } catch (e: Exception) {
            Log.e("MetadataEvent", "Can't parse identity [${it.joinToString { "," }}]", e)
            null
        }
    }

    companion object {
        const val kind = 0

        fun create(contactMetaData: String, identities: List<IdentityClaim>, pubKey: HexKey, privateKey: ByteArray?, createdAt: Long = TimeUtils.now()): MetadataEvent {
            val tags = mutableListOf<List<String>>()

            identities.forEach {
                tags.add(listOf("i", it.platformIdentity(), it.proof))
            }

            val id = generateId(pubKey, createdAt, kind, tags, contactMetaData)
            val sig = if (privateKey == null) null else CryptoUtils.sign(id, privateKey)
            return MetadataEvent(id.toHexKey(), pubKey, createdAt, tags, contactMetaData, sig?.toHexKey() ?: "")
        }

        fun create(unsignedEvent: MetadataEvent, signature: String): MetadataEvent {
            return MetadataEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
