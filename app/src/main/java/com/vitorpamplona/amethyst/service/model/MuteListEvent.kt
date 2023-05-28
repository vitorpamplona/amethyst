package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
class MuteListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {
    override fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun plainContent(privKey: ByteArray): String? {
        return try {
            val sharedSecret = Utils.getSharedSecret(privKey, pubKey.hexToByteArray())

            return Utils.decrypt(content, sharedSecret)
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
            gson.fromJson(plainContent(privKey), object : TypeToken<List<List<String>>>() {}.type)
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
            createdAt: Long = Date().time / 1000
        ): MuteListEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)

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

            val content = Utils.encrypt(
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
            val sig = Utils.sign(id, privateKey)
            return MuteListEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}
