package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.toHex

class RepostEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {

  @Transient val boostedPost: List<String>
  @Transient val originalAuthor: List<String>

  init {
    boostedPost = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    originalAuthor = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
  }

  companion object {
    const val kind = 6

    fun create(boostedPost: Event, privateKey: ByteArray, createdAt: Long = Date().time / 1000): RepostEvent {
      val content = ""

      val replyToPost = listOf("e", boostedPost.id.toHex())
      val replyToAuthor = listOf("p", boostedPost.pubKey.toHex())

      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags:List<List<String>> = boostedPost.tags.plus(listOf(replyToPost, replyToAuthor))
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return RepostEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }
}