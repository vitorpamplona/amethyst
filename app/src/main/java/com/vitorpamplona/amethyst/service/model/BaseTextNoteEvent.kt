package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.tagSearch

open class BaseTextNoteEvent(
  id: HexKey,
  pubKey: HexKey,
  createdAt: Long,
  kind: Int,
  tags: List<List<String>>,
  content: String,
  sig: HexKey
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  fun mentions() = taggedUsers()
  fun replyTos() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }

  fun findCitations(): Set<String> {
    var citations = mutableSetOf<String>()
    // Removes citations from replies:
    val matcher = tagSearch.matcher(content)
    while (matcher.find()) {
      try {
        val tag = matcher.group(1)?.let { tags[it.toInt()] }
        if (tag != null && tag[0] == "e") {
          citations.add(tag[1])
        }
      } catch (e: Exception) {

      }
    }
    return citations
  }

  fun replyToWithoutCitations(): List<String> {
    val repliesTo = replyTos()
    if (repliesTo.isEmpty()) return repliesTo

    val citations = findCitations()

    return if (citations.isEmpty()) {
      repliesTo
    } else {
      repliesTo.filter { it !in citations }
    }
  }

}