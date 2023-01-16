package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.toHex

class ChannelHideMessageEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  @Transient val eventsToHide: List<String>

  init {
    eventsToHide = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
  }

  companion object {
    const val kind = 43

    fun create(reason: String, messagesToHide: List<String>?, mentions: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelHideMessageEvent {
      val content = reason
      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags =
        messagesToHide?.map {
          listOf("e", it)
        } ?: emptyList()

      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ChannelHideMessageEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }
}