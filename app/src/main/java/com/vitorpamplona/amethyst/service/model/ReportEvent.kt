package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.toHex

data class ReportedKey(val key: String, val reportType: ReportEvent.ReportType)

// NIP 56 event.
class ReportEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {

  @Transient val reportedPost: List<ReportedKey>
  @Transient val reportedAuthor: List<ReportedKey>

  init {
    // Works with old and new structures for report.

    var reportType = tags.filter { it.firstOrNull() == "report" }.mapNotNull { it.getOrNull(1) }.map { ReportType.valueOf(it.toUpperCase()) }.firstOrNull()
    if (reportType == null) {
      reportType = tags.mapNotNull { it.getOrNull(2) }.map { ReportType.valueOf(it.toUpperCase()) }.firstOrNull()
    }
    if (reportType == null) {
      reportType = ReportType.SPAM
    }

    reportedPost = tags
      .filter { it.firstOrNull() == "e" && it.getOrNull(1) != null }
      .map {
        ReportedKey(
          it[1],
          it.getOrNull(2)?.toUpperCase()?.let { it1 -> ReportType.valueOf(it1) }?: reportType
        )
      }

    reportedAuthor = tags
      .filter { it.firstOrNull() == "p" && it.getOrNull(1) != null }
      .map {
        ReportedKey(
          it[1],
        it.getOrNull(2)?.toUpperCase()?.let { it1 -> ReportType.valueOf(it1) }?: reportType
        )
      }
  }

  companion object {
    const val kind = 1984

    fun create(reportedPost: Event, type: ReportType, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReportEvent {
      val content = ""

      val reportPostTag = listOf("e", reportedPost.id.toHex(), type.name.toLowerCase())
      val reportAuthorTag = listOf("p", reportedPost.pubKey.toHex(), type.name.toLowerCase())

      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags:List<List<String>> = listOf(reportPostTag, reportAuthorTag)
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ReportEvent(id, pubKey, createdAt, tags, content, sig)
    }

    fun create(reportedUser: String, type: ReportType, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReportEvent {
      val content = ""

      val reportAuthorTag = listOf("p", reportedUser, type.name.toLowerCase())

      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags:List<List<String>> = listOf(reportAuthorTag)
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return ReportEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }

  enum class ReportType() {
    EXPLICIT, // Not used anymore.
    ILLEGAL,
    SPAM,
    IMPERSONATION,
    NUDITY,
    PROFANITY,
  }
}