package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event

class ChannelMessageEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  @Transient val channel: String?
  @Transient val replyTos: List<String>
  @Transient val mentions: List<String>

  init {
    channel = tags.firstOrNull { it[0] == "e" && it.size > 3 && it[3] == "root" }?.getOrNull(1) ?: tags.firstOrNull { it.firstOrNull() == "e" }?.getOrNull(1)
    replyTos = tags.filter { it.getOrNull(1) != channel }.mapNotNull { it.getOrNull(1) }
    mentions = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
  }

  companion object {
    const val kind = 42

    fun create(message: String, channel: String, replyTos: List<String>? = null, mentions: List<String>? = null, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMessageEvent {
      val content = message
      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags = mutableListOf(
        listOf("e", channel, "", "root")
      )
      replyTos?.forEach {
        tags.add(listOf("e", it))
      }
      mentions?.forEach {
        tags.add(listOf("p", it))
      }

      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ChannelMessageEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }
}