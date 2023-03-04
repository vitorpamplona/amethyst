package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import java.util.Date
import nostr.postr.Utils

class ChannelHideMessageEvent (
  id: HexKey,
  pubKey: HexKey,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: HexKey
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  fun eventsToHide() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }

  companion object {
    const val kind = 43

    fun create(reason: String, messagesToHide: List<String>?, mentions: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelHideMessageEvent {
      val content = reason
      val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
      val tags =
        messagesToHide?.map {
          listOf("e", it)
        } ?: emptyList()

      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ChannelHideMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
    }
  }
}