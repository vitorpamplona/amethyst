package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import nostr.postr.Bech32
import nostr.postr.Persona
import nostr.postr.bechToBytes
import nostr.postr.toHex

/** Makes the distinction between String and Hex **/
typealias HexKey = String

typealias NPubKey = String
typealias NoteId = String

fun NPubKey.toDisplayKey(): String {
    return this.toShortenHex()
}

fun NoteId.toDisplayId(): String {
    return this.toShortenHex()
}

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
    } else if (key.startsWith("note")) {
        key.bechToBytes()
    } else { // if (pattern.matcher(key).matches()) {
        // } else {
        Hex.decode(key)
    }
}

data class DirtyKeyInfo(val type: String, val keyHex: String, val restOfWord: String)

fun parseDirtyWordForKey(mightBeAKey: String): DirtyKeyInfo? {
    var key = mightBeAKey
    if (key.startsWith("nostr:", true)) {
        key = key.substring("nostr:".length)
    }

    key = key.removePrefix("@")

    if (key.length < 63) {
        return null
    }

    try {
        val keyB32 = key.substring(0, 63)
        val restOfWord = key.substring(63)

        if (key.startsWith("nsec1", true)) {
            return DirtyKeyInfo("npub", Persona(privKey = keyB32.bechToBytes()).pubKey.toHexKey(), restOfWord)
        } else if (key.startsWith("npub1", true)) {
            return DirtyKeyInfo("npub", keyB32.bechToBytes().toHexKey(), restOfWord)
        } else if (key.startsWith("note1", true)) {
            return DirtyKeyInfo("note", keyB32.bechToBytes().toHexKey(), restOfWord)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return null
}
