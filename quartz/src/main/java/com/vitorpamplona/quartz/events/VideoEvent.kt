package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
abstract class VideoEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun url() = tags.firstOrNull { it.size > 1 && it[0] == URL }?.get(1)
    fun urls() = tags.filter { it.size > 1 && it[0] == URL }.map { it[1] }

    fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == MIME_TYPE }?.get(1)
    fun hash() = tags.firstOrNull { it.size > 1 && it[0] == HASH }?.get(1)
    fun size() = tags.firstOrNull { it.size > 1 && it[0] == FILE_SIZE }?.get(1)
    fun alt() = tags.firstOrNull { it.size > 1 && it[0] == ALT }?.get(1)
    fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == DIMENSION }?.get(1)
    fun magnetURI() = tags.firstOrNull { it.size > 1 && it[0] == MAGNET_URI }?.get(1)
    fun torrentInfoHash() = tags.firstOrNull { it.size > 1 && it[0] == TORRENT_INFOHASH }?.get(1)
    fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == BLUR_HASH }?.get(1)

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == TITLE }?.get(1)
    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == SUMMARY }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == IMAGE }?.get(1)
    fun thumb() = tags.firstOrNull { it.size > 1 && it[0] == THUMB }?.get(1)

    fun hasUrl() = tags.any { it.size > 1 && it[0] == URL }

    companion object {
        private const val URL = "url"
        private const val ENCRYPTION_KEY = "aes-256-gcm"
        private const val MIME_TYPE = "m"
        private const val FILE_SIZE = "size"
        private const val DIMENSION = "dim"
        private const val HASH = "x"
        private const val MAGNET_URI = "magnet"
        private const val TORRENT_INFOHASH = "i"
        private const val BLUR_HASH = "blurhash"
        private const val ORIGINAL_HASH = "ox"
        private const val ALT = "alt"
        private const val TITLE = "title"
        private const val PUBLISHED_AT = "published_at"
        private const val SUMMARY = "summary"
        private const val DURATION = "duration"
        private const val IMAGE = "image"
        private const val THUMB = "thumb"

        fun create(
            kind: Int,
            url: String,
            magnetUri: String? = null,
            mimeType: String? = null,
            alt: String? = null,
            hash: String? = null,
            size: String? = null,
            dimensions: String? = null,
            blurhash: String? = null,
            originalHash: String? = null,
            magnetURI: String? = null,
            torrentInfoHash: String? = null,
            encryptionKey: AESGCM? = null,
            sensitiveContent: Boolean? = null,
            altDescription: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileHeaderEvent) -> Unit
        ) {
            val tags = listOfNotNull(
                arrayOf(URL, url),
                magnetUri?.let { arrayOf(MAGNET_URI, it) },
                mimeType?.let { arrayOf(MIME_TYPE, it) },
                alt?.ifBlank { null }?.let { arrayOf(ALT, it) } ?: arrayOf("alt", altDescription),
                hash?.let { arrayOf(HASH, it) },
                size?.let { arrayOf(FILE_SIZE, it) },
                dimensions?.let { arrayOf(DIMENSION, it) },
                blurhash?.let { arrayOf(BLUR_HASH, it) },
                originalHash?.let { arrayOf(ORIGINAL_HASH, it) },
                magnetURI?.let { arrayOf(MAGNET_URI, it) },

                torrentInfoHash?.let { arrayOf(TORRENT_INFOHASH, it) },
                encryptionKey?.let { arrayOf(ENCRYPTION_KEY, it.key, it.nonce) },
                sensitiveContent?.let {
                    if (it) {
                        arrayOf("content-warning", "")
                    } else {
                        null
                    }
                }
            )

            val content = alt ?: ""
            signer.sign(createdAt, kind, tags.toTypedArray(), content, onReady)
        }
    }
}
