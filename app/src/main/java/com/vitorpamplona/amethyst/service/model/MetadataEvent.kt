package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.google.gson.Gson
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

    companion object {
        const val kind = 0
        val gson = Gson()

        fun create(contactMetaData: ContactMetaData, privateKey: ByteArray, createdAt: Long = Date().time / 1000): MetadataEvent {
            return create(gson.toJson(contactMetaData), privateKey, createdAt = createdAt)
        }

        fun create(contactMetaData: String, privateKey: ByteArray, createdAt: Long = Date().time / 1000): MetadataEvent {
            val content = contactMetaData
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return MetadataEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
