package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent
import nostr.postr.toHex

class ChannelMuteUserEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  @Transient val usersToMute: List<String>

  init {
    usersToMute = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
  }

  companion object {
    const val kind = 43

    fun create(reason: String, usersToMute: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMuteUserEvent {
      val content = reason
      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags =
        usersToMute?.map {
          listOf("p", it)
        } ?: emptyList()

      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ChannelMuteUserEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }
}