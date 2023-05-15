package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.hexToByteArray
import nostr.postr.Utils

abstract class GeneralListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {
    override fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun category() = dTag()
    fun bookmarkedPosts() = taggedEvents()
    fun bookmarkedPeople() = taggedUsers()

    fun plainContent(privKey: ByteArray): String? {
        if (content.isBlank()) return null

        return try {
            val sharedSecret = Utils.getSharedSecret(privKey, pubKey.hexToByteArray())

            return Utils.decrypt(content, sharedSecret)
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
            gson.fromJson(plainContent(privKey), object : TypeToken<List<List<String>>>() {}.type)
        } catch (e: Throwable) {
            Log.w("GeneralList", "Error parsing the JSON ${e.message}")
            null
        }
        return privateTagsCache
    }

    fun privateTaggedUsers(privKey: ByteArray) = privateTags(privKey)?.filter { it.size > 1 && it[0] == "p" }?.map { it[1] }
    fun privateTaggedEvents(privKey: ByteArray) = privateTags(privKey)?.filter { it.size > 1 && it[0] == "e" }?.map { it[1] }
    fun privateTaggedAddresses(privKey: ByteArray) = privateTags(privKey)?.filter { it.firstOrNull() == "a" }?.mapNotNull {
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
            val msg = gson.toJson(privTags)

            return Utils.encrypt(
                msg,
                privateKey,
                pubKey
            )
        }
    }
}
