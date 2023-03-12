package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.google.gson.Gson
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

data class ContactMetaData(
    val name: String,
    val picture: String,
    val about: String,
    val nip05: String?
)

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
            val platformIdentity = platformIdentity.split(':')
            val platform = platformIdentity[0]
            val identity = platformIdentity[1]

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
        gson.fromJson(content, ContactMetaData::class.java)
    } catch (e: Exception) {
        Log.e("MetadataEvent", "Can't parse $content", e)
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

        fun create(contactMetaData: ContactMetaData, identities: List<IdentityClaim>, privateKey: ByteArray, createdAt: Long = Date().time / 1000): MetadataEvent {
            return create(gson.toJson(contactMetaData), identities, privateKey, createdAt = createdAt)
        }

        fun create(contactMetaData: String, identities: List<IdentityClaim>, privateKey: ByteArray, createdAt: Long = Date().time / 1000): MetadataEvent {
            val content = contactMetaData
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            identities?.forEach {
                tags.add(listOf("i", it.platformIdentity(), it.proof))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return MetadataEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
