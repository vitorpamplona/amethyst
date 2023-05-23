package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Base64
import java.util.Date

@Immutable
class FileStorageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun type() = tags.firstOrNull { it.size > 1 && it[0] == TYPE }?.get(1)
    fun decryptKey() = tags.firstOrNull { it.size > 2 && it[0] == DECRYPT }?.let { AESGCM(it[1], it[2]) }

    fun decode(): ByteArray? {
        return try {
            Base64.getDecoder().decode(content)
        } catch (e: Exception) {
            Log.e("FileStorageEvent", "Unable to decode base 64 ${e.message} $content")
            null
        }
    }

    companion object {
        const val kind = 1064

        private const val TYPE = "type"
        private const val DECRYPT = "decrypt"

        fun encode(bytes: ByteArray): String {
            return Base64.getEncoder().encodeToString(bytes)
        }

        fun create(
            mimeType: String,
            data: ByteArray,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): FileStorageEvent {
            val tags = listOfNotNull(
                listOf(TYPE, mimeType)
            )

            val content = encode(data)
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return FileStorageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
