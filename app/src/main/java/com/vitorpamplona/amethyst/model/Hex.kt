package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.ui.note.toDisplayHex
import fr.acinq.secp256k1.Hex
import java.util.regex.Pattern
import nostr.postr.Persona
import nostr.postr.bechToBytes
import nostr.postr.toHex

/** Makes the distinction between String and Hex **/
typealias HexKey = String

fun ByteArray.toHexKey(): HexKey {
  return toHex()
}

fun HexKey.toByteArray(): ByteArray {
  return Hex.decode(this)
}

fun HexKey.toDisplayHexKey(): String {
  return this.toDisplayHex()
}

fun decodePublicKey(key: String): ByteArray {
  val pattern = Pattern.compile(".+@.+\\.[a-z]+")

  return if (key.startsWith("nsec")) {
    Persona(privKey = key.bechToBytes()).pubKey
  } else if (key.startsWith("npub")) {
    key.bechToBytes()
  } else { //if (pattern.matcher(key).matches()) {
  //} else {
    Hex.decode(key)
  }
}