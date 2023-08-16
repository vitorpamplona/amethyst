package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class HighlightEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun inUrl() = taggedUrls().firstOrNull()
    fun author() = taggedUsers().firstOrNull()
    fun quote() = content

    fun inPost() = taggedAddresses().firstOrNull()

    companion object {
        const val kind = 9802

        fun create(
            msg: String,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): HighlightEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = CryptoUtils.sign(id, privateKey)
            return HighlightEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }
}
