package com.vitorpamplona.amethyst.service.relays

import fr.acinq.secp256k1.Secp256k1
import nostr.postr.events.Event
import nostr.postr.events.generateId

fun Event.hasValidSignature(): Boolean {
  if (!id.contentEquals(generateId())) {
    return false
  }
  if (!Secp256k1.get().verifySchnorr(sig, id, pubKey)) {
    return false
  }

  return true
}