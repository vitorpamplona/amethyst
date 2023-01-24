package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.toHex

class ReactionEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {

  @Transient val originalPost: List<String>
  @Transient val originalAuthor: List<String>

  init {
    originalPost = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    originalAuthor = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
  }

  companion object {
    const val kind = 7

    fun createWarning(originalNote: Event, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReactionEvent {
      return create("\u26A0\uFE0F", originalNote, privateKey, createdAt)
    }

    fun createLike(originalNote: Event, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReactionEvent {
      return create("+", originalNote, privateKey, createdAt)
    }

    fun create(content: String, originalNote: Event, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReactionEvent {
      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags = listOf( listOf("e", originalNote.id.toHex()), listOf("p", originalNote.pubKey.toHex()))
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ReactionEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }
}