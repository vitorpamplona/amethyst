package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event

class TextNoteEvent(
    id: ByteArray,
    pubKey: ByteArray,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient val replyTos: List<String>
    @Transient val mentions: List<String>
    @Transient val longFormAddress: List<String>

    init {
        longFormAddress = tags.filter { it.firstOrNull() == "a" }.mapNotNull { it.getOrNull(1) }
        replyTos = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
        mentions = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
    }

    companion object {
        const val kind = 1

        fun create(msg: String, replyTos: List<String>?, mentions: List<String>?, addresses: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): TextNoteEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it))
            }
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = Utils.sign(id, privateKey)
            return TextNoteEvent(id, pubKey, createdAt, tags, msg, sig)
        }
    }
}