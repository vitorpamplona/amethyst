package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.service.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.relays.Client
import java.math.BigDecimal
import nostr.postr.events.Event

class LnZapEvent (
  id: ByteArray,
  pubKey: ByteArray,
  createdAt: Long,
  tags: List<List<String>>,
  content: String,
  sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {

  @Transient val zappedPost: List<String>
  @Transient val zappedAuthor: List<String>
  @Transient val containedPost: Event?
  @Transient val lnInvoice: String?
  @Transient val preimage: String?
  @Transient val amount: BigDecimal?

  init {
    zappedPost = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    zappedAuthor = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    lnInvoice = tags.filter { it.firstOrNull() == "bolt11" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    amount = lnInvoice?.let { LnInvoiceUtil.getAmountInSats(lnInvoice) }
    preimage = tags.filter { it.firstOrNull() == "preimage" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    val description = tags.filter { it.firstOrNull() == "description" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    containedPost = try {
      if (description == null)
        null
      else
        fromJson(description, Client.lenient)
    } catch (e: Exception) {
      null
    }
  }

  companion object {
    const val kind = 9735
  }
}