package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import java.util.Base64

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
            pubKey: HexKey,
            createdAt: Long = TimeUtils.now()
        ): FileStorageEvent {
            val tags = listOfNotNull(
                listOf(TYPE, mimeType)
            )

            val content = encode(data)
            val id = generateId(pubKey, createdAt, kind, tags, content)
            return FileStorageEvent(id.toHexKey(), pubKey, createdAt, tags, content, "")
        }

        fun create(
            mimeType: String,
            data: ByteArray,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): FileStorageEvent {
            val tags = listOfNotNull(
                listOf(TYPE, mimeType)
            )

            val content = encode(data)
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return FileStorageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
