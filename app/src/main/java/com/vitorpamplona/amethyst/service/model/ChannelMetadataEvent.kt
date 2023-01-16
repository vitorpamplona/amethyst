package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.ContactMetaData
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent
import nostr.postr.toHex

class ChannelMetadataEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  @Transient val channel: String?
  @Transient val channelInfo: ChannelCreateEvent.ChannelData

  init {
    channel = tags.firstOrNull { it.firstOrNull() == "e" }?.getOrNull(1)
    try {
      channelInfo = MetadataEvent.gson.fromJson(content, ChannelCreateEvent.ChannelData::class.java)
    } catch (e: Exception) {
      throw Error("can't parse $content", e)
    }
  }

  companion object {
    const val kind = 41

    fun create(newChannelInfo: ChannelCreateEvent.ChannelData?, originalChannel: ChannelCreateEvent, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMetadataEvent {
      val content = if (newChannelInfo != null)
        gson.toJson(newChannelInfo)
      else
        ""

      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags = listOf( listOf("e", originalChannel.id.toHex(), "", "root") )
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ChannelMetadataEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }
}