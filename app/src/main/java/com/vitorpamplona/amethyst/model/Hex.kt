package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import java.util.regex.Pattern
import nostr.postr.Bech32
import nostr.postr.Persona
import nostr.postr.bechToBytes
import nostr.postr.toHex

/** Makes the distinction between String and Hex **/
typealias HexKey = String

fun ByteArray.toNote() = Bech32.encodeBytes(hrp = "note", this, Bech32.Encoding.Bech32)

fun ByteArray.toHexKey(): HexKey {
  return toHex()
}

fun HexKey.toByteArray(): ByteArray {
  return Hex.decode(this)
}

fun HexKey.toDisplayHexKey(): String {
  return this.toShortenHex()
}

fun decodePublicKey(key: String): ByteArray {
  return if (key.startsWith("nsec")) {
    Persona(privKey = key.bechToBytes()).pubKey
  } else if (key.startsWith("npub")) {
    key.bechToBytes()
  } else { //if (pattern.matcher(key).matches()) {
  //} else {
    Hex.decode(key)
  }
}