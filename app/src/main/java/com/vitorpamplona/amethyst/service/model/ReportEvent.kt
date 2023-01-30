package com.vitorpamplona.amethyst.service.model

import androidx.compose.ui.text.toUpperCase
import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.toHex

// NIP 56 event.
class ReportEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {

  @Transient val reportType: List<ReportType>
  @Transient val reportedPost: List<String>
  @Transient val reportedAuthor: List<String>

  init {
    reportType = tags.filter { it.firstOrNull() == "report" }.mapNotNull { it.getOrNull(1) }.map { ReportType.valueOf(it.toUpperCase()) }
    reportedPost = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    reportedAuthor = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
  }

  companion object {
    const val kind = 1984

    fun create(reportedPost: Event, type: ReportType, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReportEvent {
      val content = ""

      val reportTypeTag = listOf("report", type.name.toLowerCase())
      val reportPostTag = listOf("e", reportedPost.id.toHex())
      val reportAuthorTag = listOf("p", reportedPost.pubKey.toHex())

      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags:List<List<String>> = listOf(reportTypeTag, reportPostTag, reportAuthorTag)
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ReportEvent(id, pubKey, createdAt, tags, content, sig)
    }

    fun create(reportedUser: String, type: ReportType, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReportEvent {
      val content = ""

      val reportTypeTag = listOf("report", type.name.toLowerCase())
      val reportAuthorTag = listOf("p", reportedUser)

      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags:List<List<String>> = listOf(reportTypeTag, reportAuthorTag)
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ReportEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }

  enum class ReportType() {
    EXPLICIT,
    ILLEGAL,
    SPAM,
    IMPERSONATION
  }
}