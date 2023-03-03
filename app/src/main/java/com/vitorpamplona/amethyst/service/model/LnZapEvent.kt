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

  fun zappedPost() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
  fun zappedAuthor() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

  fun taggedAddresses() = tags.filter { it.firstOrNull() == "a" }.mapNotNull { it.getOrNull(1) }.mapNotNull { ATag.parse(it) }

  fun lnInvoice() = tags.filter { it.firstOrNull() == "bolt11" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
  fun preimage() = tags.filter { it.firstOrNull() == "preimage" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

  fun description() = tags.filter { it.firstOrNull() == "description" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

  // Keeps this as a field because it's a heavier function used everywhere.
  val amount = lnInvoice()?.let { LnInvoiceUtil.getAmountInSats(it) }

  fun containedPost() = try {
    description()?.let {
      fromJson(it, Client.lenient)
    }
  } catch (e: Exception) {
    null
  }

  companion object {
    const val kind = 9735
  }
}