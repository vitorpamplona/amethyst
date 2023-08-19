package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class AudioHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun download() = tags.firstOrNull { it.size > 1 && it[0] == DOWNLOAD_URL }?.get(1)
    fun stream() = tags.firstOrNull { it.size > 1 && it[0] == STREAM_URL }?.get(1)
    fun wavefrom() = tags.firstOrNull { it.size > 1 && it[0] == WAVEFORM }?.get(1)?.let {
        mapper.readValue<List<Int>>(it)
    }

    companion object {
        const val kind = 1808

        private const val DOWNLOAD_URL = "download_url"
        private const val STREAM_URL = "stream_url"
        private const val WAVEFORM = "waveform"

        fun create(
            description: String,
            downloadUrl: String,
            streamUrl: String? = null,
            wavefront: String? = null,
            sensitiveContent: Boolean? = null,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): AudioHeaderEvent {
            val tags = listOfNotNull(
                downloadUrl.let { listOf(DOWNLOAD_URL, it) },
                streamUrl?.let { listOf(STREAM_URL, it) },
                wavefront?.let { listOf(WAVEFORM, it) },
                sensitiveContent?.let {
                    if (it) {
                        listOf("content-warning", "")
                    } else {
                        null
                    }
                }
            )

            val content = description
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return AudioHeaderEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
