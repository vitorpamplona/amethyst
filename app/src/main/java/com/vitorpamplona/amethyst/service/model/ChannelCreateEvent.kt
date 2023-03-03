package com.vitorpamplona.amethyst.service.model

import android.util.Log
import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent

class ChannelCreateEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  fun channelInfo() = try {
    MetadataEvent.gson.fromJson(content, ChannelData::class.java)
  } catch (e: Exception) {
    Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
    ChannelData(null, null, null)
  }

  companion object {
    const val kind = 40

    fun create(channelInfo: ChannelData?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelCreateEvent {
      val content = try {
        if (channelInfo != null)
          gson.toJson(channelInfo)
        else
          ""
      } catch (t: Throwable) {
        Log.e("ChannelCreateEvent", "Couldn't parse channel information", t)
        ""
      }

      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags = emptyList<List<String>>()
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }

  data class ChannelData(var name: String?, var about: String?, var picture: String?)
}