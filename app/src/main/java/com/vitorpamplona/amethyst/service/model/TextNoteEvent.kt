package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 1

        fun create(msg: String, replyTos: List<String>?, mentions: List<String>?, addresses: List<ATag>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): TextNoteEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
            }
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = Utils.sign(id, privateKey)
            return TextNoteEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }
}
