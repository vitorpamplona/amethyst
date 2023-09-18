package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
abstract class GeneralListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun category() = dTag()
    fun bookmarkedPosts() = taggedEvents()
    fun bookmarkedPeople() = taggedUsers()

    fun plainContent(privKey: ByteArray): String? {
        if (content.isBlank()) return null

        return try {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(privKey, pubKey.hexToByteArray())

            return CryptoUtils.decryptNIP04(content, sharedSecret)
        } catch (e: Exception) {
            Log.w("GeneralList", "Error decrypting the message ${e.message} for ${dTag()}")
            null
        }
    }

    @Transient
    private var privateTagsCache: List<List<String>>? = null

    fun privateTags(privKey: ByteArray): List<List<String>>? {
        if (privateTagsCache != null) {
            return privateTagsCache
        }

        privateTagsCache = try {
            plainContent(privKey)?.let { mapper.readValue<List<List<String>>>(it) }
        } catch (e: Throwable) {
            Log.w("GeneralList", "Error parsing the JSON ${e.message}")
            null
        }
        return privateTagsCache
    }

    fun privateTags(content: String): List<List<String>>? {
        if (privateTagsCache != null) {
            return privateTagsCache
        }

        privateTagsCache = try {
            content.let { mapper.readValue<List<List<String>>>(it) }
        } catch (e: Throwable) {
            Log.w("GeneralList", "Error parsing the JSON ${e.message}")
            null
        }
        return privateTagsCache
    }

    fun privateTagsOrEmpty(privKey: ByteArray): List<List<String>> {
        return privateTags(privKey) ?: emptyList()
    }

    fun privateTagsOrEmpty(content: String): List<List<String>> {
        return privateTags(content) ?: emptyList()
    }

    fun privateTaggedUsers(privKey: ByteArray) = privateTags(privKey)?.filter { it.size > 1 && it[0] == "p" }?.map { it[1] }
    fun privateTaggedUsers(content: String) = privateTags(content)?.filter { it.size > 1 && it[0] == "p" }?.map { it[1] }
    fun privateHashtags(privKey: ByteArray) = privateTags(privKey)?.filter { it.size > 1 && it[0] == "t" }?.map { it[1] }
    fun privateHashtags(content: String) = privateTags(content)?.filter { it.size > 1 && it[0] == "t" }?.map { it[1] }
    fun privateGeohashes(privKey: ByteArray) = privateTags(privKey)?.filter { it.size > 1 && it[0] == "g" }?.map { it[1] }
    fun privateGeohashes(content: String) = privateTags(content)?.filter { it.size > 1 && it[0] == "g" }?.map { it[1] }
    fun privateTaggedEvents(privKey: ByteArray) = privateTags(privKey)?.filter { it.size > 1 && it[0] == "e" }?.map { it[1] }
    fun privateTaggedEvents(content: String) = privateTags(content)?.filter { it.size > 1 && it[0] == "e" }?.map { it[1] }

    fun privateTaggedAddresses(privKey: ByteArray) = privateTags(privKey)?.filter { it.firstOrNull() == "a" }?.mapNotNull {
        val aTagValue = it.getOrNull(1)
        val relay = it.getOrNull(2)

        if (aTagValue != null) ATag.parse(aTagValue, relay) else null
    }

    fun privateTaggedAddresses(content: String) = privateTags(content)?.filter { it.firstOrNull() == "a" }?.mapNotNull {
        val aTagValue = it.getOrNull(1)
        val relay = it.getOrNull(2)

        if (aTagValue != null) ATag.parse(aTagValue, relay) else null
    }

    companion object {
        fun createPrivateTags(
            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,

            privateKey: ByteArray,
            pubKey: ByteArray
        ): String {
            val privTags = mutableListOf<List<String>>()
            privEvents?.forEach {
                privTags.add(listOf("e", it))
            }
            privUsers?.forEach {
                privTags.add(listOf("p", it))
            }
            privAddresses?.forEach {
                privTags.add(listOf("a", it.toTag()))
            }
            val msg = mapper.writeValueAsString(privTags)

            return CryptoUtils.encryptNIP04(
                msg,
                privateKey,
                pubKey
            )
        }

        fun encryptTags(
            privateTags: List<List<String>>? = null,
            privateKey: ByteArray
        ): String {
            return CryptoUtils.encryptNIP04(
                msg = mapper.writeValueAsString(privateTags),
                privateKey = privateKey,
                pubKey = CryptoUtils.pubkeyCreate(privateKey)
            )
        }
    }
}
