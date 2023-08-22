package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class MuteListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun plainContent(privKey: ByteArray): String? {
        return try {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(privKey, pubKey.hexToByteArray())

            return CryptoUtils.decryptNIP04(content, sharedSecret)
        } catch (e: Exception) {
            Log.w("BookmarkList", "Error decrypting the message ${e.message}")
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
            Log.w("BookmarkList", "Error parsing the JSON ${e.message}")
            null
        }
        return privateTagsCache
    }

    fun privateTaggedUsers(privKey: ByteArray) = privateTags(privKey)?.filter { it.firstOrNull() == "p" }?.mapNotNull { it.getOrNull(1) }
    fun privateTaggedEvents(privKey: ByteArray) = privateTags(privKey)?.filter { it.firstOrNull() == "e" }?.mapNotNull { it.getOrNull(1) }
    fun privateTaggedAddresses(privKey: ByteArray) = privateTags(privKey)?.filter { it.firstOrNull() == "a" }?.mapNotNull {
        val aTagValue = it.getOrNull(1)
        val relay = it.getOrNull(2)

        if (aTagValue != null) ATag.parse(aTagValue, relay) else null
    }

    companion object {
        const val kind = 10000

        fun create(
            events: List<String>? = null,
            users: List<String>? = null,
            addresses: List<ATag>? = null,

            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,

            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): MuteListEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey)

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

            val content = CryptoUtils.encryptNIP04(
                msg,
                privateKey,
                pubKey
            )

            val tags = mutableListOf<List<String>>()
            events?.forEach {
                tags.add(listOf("e", it))
            }
            users?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return MuteListEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}
