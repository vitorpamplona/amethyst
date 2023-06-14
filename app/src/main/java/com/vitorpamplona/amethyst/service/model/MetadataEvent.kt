package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.UserMetadata
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.io.ByteArrayInputStream
import java.util.Date

abstract class IdentityClaim(
    var identity: String,
    var proof: String
) {
    abstract fun toProofUrl(): String
    abstract fun toIcon(): Int
    abstract fun toDescriptor(): Int
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
    override fun toIcon() = R.drawable.github
    override fun toDescriptor() = R.string.github

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
    override fun toIcon() = R.drawable.twitter
    override fun toDescriptor() = R.string.twitter

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
    override fun toIcon() = R.drawable.telegram
    override fun toDescriptor() = R.string.telegram

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
    override fun toIcon() = R.drawable.mastodon
    override fun toDescriptor() = R.string.mastodon

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
        metadataParser.readValue(
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
            UserMetadata::class.java
        )
    } catch (e: Exception) {
        e.printStackTrace()
        Log.w("MT", "Content Parse Error ${e.localizedMessage} $content")
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
        val gson = Gson()

        val metadataParser by lazy {
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readerFor(UserMetadata::class.java)
        }

        fun create(contactMetaData: String, identities: List<IdentityClaim>, privateKey: ByteArray, createdAt: Long = Date().time / 1000): MetadataEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()

            identities.forEach {
                tags.add(listOf("i", it.platformIdentity(), it.proof))
            }

            val id = generateId(pubKey, createdAt, kind, tags, contactMetaData)
            val sig = Utils.sign(id, privateKey)
            return MetadataEvent(id.toHexKey(), pubKey, createdAt, tags, contactMetaData, sig.toHexKey())
        }
    }
}
