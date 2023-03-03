package com.vitorpamplona.amethyst.service.model

import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event
import nostr.postr.toHex

class LnZapRequestEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
  fun zappedPost() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
  fun zappedAuthor() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
  fun taggedAddresses() = tags.filter { it.firstOrNull() == "a" }.mapNotNull { it.getOrNull(1) }.mapNotNull { ATag.parse(it) }

  companion object {
    const val kind = 9734

    fun create(originalNote: Event, relays: Set<String>, privateKey: ByteArray, createdAt: Long = Date().time / 1000): LnZapRequestEvent {
      val content = ""
      val pubKey = Utils.pubkeyCreate(privateKey)
      var tags = listOf(
        listOf("e", originalNote.id.toHex()),
        listOf("p", originalNote.pubKey.toHex()),
        listOf("relays") + relays
      )
      if (originalNote is LongTextNoteEvent) {
        tags = tags + listOf( listOf("a", originalNote.address().toTag()) )
      }

      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
    }

    fun create(userHex: String, relays: Set<String>, privateKey: ByteArray, createdAt: Long = Date().time / 1000): LnZapRequestEvent {
      val content = ""
      val pubKey = Utils.pubkeyCreate(privateKey)
      val tags = listOf(
        listOf("p", userHex),
        listOf("relays") + relays
      )
      val id = generateId(pubKey, createdAt, kind, tags, content)
      val sig = Utils.sign(id, privateKey)
      return LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
    }
  }
}

/*
{
  "pubkey": "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245",
  "content": "",
  "id": "d9cc14d50fcb8c27539aacf776882942c1a11ea4472f8cdec1dea82fab66279d",
  "created_at": 1674164539,
  "sig": "77127f636577e9029276be060332ea565deaf89ff215a494ccff16ae3f757065e2bc59b2e8c113dd407917a010b3abd36c8d7ad84c0e3ab7dab3a0b0caa9835d",
  "kind": 9734,
  "tags": [
  [
    "e",
    "3624762a1274dd9636e0c552b53086d70bc88c165bc4dc0f9e836a1eaf86c3b8"
  ],
  [
    "p",
    "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"
  ],
  [
    "relays",
    "wss://relay.damus.io",
    "wss://nostr-relay.wlvs.space",
    "wss://nostr.fmt.wiz.biz",
    "wss://relay.nostr.bg",
    "wss://nostr.oxtr.dev",
    "wss://nostr.v0l.io",
    "wss://brb.io",
    "wss://nostr.bitcoiner.social",
    "ws://monad.jb55.com:8080",
    "wss://relay.snort.social"
  ]
  ]
}
*/