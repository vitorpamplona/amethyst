package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class FileStorageHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun dataEventId() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun encryptionKey() = tags.firstOrNull { it.size > 2 && it[0] == ENCRYPTION_KEY }?.let { AESGCM(it[1], it[2]) }
    fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == MIME_TYPE }?.get(1)
    fun hash() = tags.firstOrNull { it.size > 1 && it[0] == HASH }?.get(1)
    fun size() = tags.firstOrNull { it.size > 1 && it[0] == FILE_SIZE }?.get(1)
    fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == DIMENSION }?.get(1)
    fun magnetURI() = tags.firstOrNull { it.size > 1 && it[0] == MAGNET_URI }?.get(1)
    fun torrentInfoHash() = tags.firstOrNull { it.size > 1 && it[0] == TORRENT_INFOHASH }?.get(1)
    fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == BLUR_HASH }?.get(1)

    companion object {
        const val kind = 1065

        private const val ENCRYPTION_KEY = "aes-256-gcm"
        private const val MIME_TYPE = "m"
        private const val FILE_SIZE = "size"
        private const val DIMENSION = "dim"
        private const val HASH = "x"
        private const val MAGNET_URI = "magnet"
        private const val TORRENT_INFOHASH = "i"
        private const val BLUR_HASH = "blurhash"

        fun create(
            storageEvent: FileStorageEvent,
            mimeType: String? = null,
            description: String? = null,
            hash: String? = null,
            size: String? = null,
            dimensions: String? = null,
            blurhash: String? = null,
            magnetURI: String? = null,
            torrentInfoHash: String? = null,
            encryptionKey: AESGCM? = null,
            sensitiveContent: Boolean? = null,
            pubKey: HexKey,
            createdAt: Long = TimeUtils.now()
        ): FileStorageHeaderEvent {
            val tags = listOfNotNull(
                listOf("e", storageEvent.id),
                mimeType?.let { listOf(MIME_TYPE, mimeType) },
                hash?.let { listOf(HASH, it) },
                size?.let { listOf(FILE_SIZE, it) },
                dimensions?.let { listOf(DIMENSION, it) },
                blurhash?.let { listOf(BLUR_HASH, it) },
                magnetURI?.let { listOf(MAGNET_URI, it) },
                torrentInfoHash?.let { listOf(TORRENT_INFOHASH, it) },
                encryptionKey?.let { listOf(ENCRYPTION_KEY, it.key, it.nonce) },
                sensitiveContent?.let {
                    if (it) {
                        listOf("content-warning", "")
                    } else {
                        null
                    }
                }
            )

            val content = description ?: ""
            val id = generateId(pubKey, createdAt, kind, tags, content)
            return FileStorageHeaderEvent(id.toHexKey(), pubKey, createdAt, tags, content, "")
        }

        fun create(
            storageEvent: FileStorageEvent,
            mimeType: String? = null,
            description: String? = null,
            hash: String? = null,
            size: String? = null,
            dimensions: String? = null,
            blurhash: String? = null,
            magnetURI: String? = null,
            torrentInfoHash: String? = null,
            encryptionKey: AESGCM? = null,
            sensitiveContent: Boolean? = null,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): FileStorageHeaderEvent {
            val tags = listOfNotNull(
                listOf("e", storageEvent.id),
                mimeType?.let { listOf(MIME_TYPE, mimeType) },
                hash?.let { listOf(HASH, it) },
                size?.let { listOf(FILE_SIZE, it) },
                dimensions?.let { listOf(DIMENSION, it) },
                blurhash?.let { listOf(BLUR_HASH, it) },
                magnetURI?.let { listOf(MAGNET_URI, it) },
                torrentInfoHash?.let { listOf(TORRENT_INFOHASH, it) },
                encryptionKey?.let { listOf(ENCRYPTION_KEY, it.key, it.nonce) },
                sensitiveContent?.let {
                    if (it) {
                        listOf("content-warning", "")
                    } else {
                        null
                    }
                }
            )

            val content = description ?: ""
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return FileStorageHeaderEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
