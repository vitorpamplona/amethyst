package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

class LnZapRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun zappedPost() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    fun zappedAuthor() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 9734

        fun create(
            originalNote: EventInterface,
            relays: Set<String>,
            privateKey: ByteArray,
            pollOption: Int?,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = Date().time / 1000
        ): LnZapRequestEvent {
            val content = message
            var privkey = privateKey
            var pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", originalNote.pubKey()),
                listOf("relays") + relays
            )
            if (originalNote is LongTextNoteEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }
            if (pollOption != null && pollOption >= 0) {
                tags = tags + listOf(listOf(POLL_OPTION, pollOption.toString()))
            }
            if (zapType == LnZapEvent.ZapType.ANONYMOUS) {
                tags = tags + listOf(listOf("anon", ""))
                privkey = Utils.privkeyCreate()
                pubKey = Utils.pubkeyCreate(privkey).toHexKey()
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun create(
            userHex: String,
            relays: Set<String>,
            privateKey: ByteArray,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = Date().time / 1000
        ): LnZapRequestEvent {
            val content = message
            var privkey = privateKey
            var pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("p", userHex),
                listOf("relays") + relays
            )
            if (zapType == LnZapEvent.ZapType.ANONYMOUS) {
                tags = tags + listOf(listOf("anon", ""))
                privkey = Utils.privkeyCreate()
                pubKey = Utils.pubkeyCreate(privkey).toHexKey()
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
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
  ],
  [
    "poll_option", "n"
  ]
  ],
  "ots": <base64-encoded OTS file data> // TODO
}
*/
