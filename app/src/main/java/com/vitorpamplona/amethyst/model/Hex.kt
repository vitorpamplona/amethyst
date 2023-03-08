package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import nostr.postr.Bech32
import nostr.postr.Persona
import nostr.postr.bechToBytes
import nostr.postr.toHex
import nostr.postr.toNpub

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

data class DirtyKeyInfo(val key: Nip19.Return, val restOfWord: String)

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
            // Converts to npub
            val pubkey = Nip19.uriToRoute(Persona(privKey = keyB32.bechToBytes()).pubKey.toNpub()) ?: return null

            return DirtyKeyInfo(pubkey, restOfWord)
        } else if (key.startsWith("npub1", true)) {
            val pubkey = Nip19.uriToRoute(keyB32) ?: return null

            return DirtyKeyInfo(pubkey, restOfWord)
        } else if (key.startsWith("note1", true)) {
            val noteId = Nip19.uriToRoute(keyB32) ?: return null

            return DirtyKeyInfo(noteId, restOfWord)
        } else if (key.startsWith("nprofile", true)) {
            val pubkeyRelay = Nip19.uriToRoute(keyB32 + restOfWord) ?: return null

            return DirtyKeyInfo(pubkeyRelay, restOfWord)
        } else if (key.startsWith("nevent", true)) {
            val noteRelayId = Nip19.uriToRoute(keyB32 + restOfWord) ?: return null

            return DirtyKeyInfo(noteRelayId, restOfWord)
        } else if (key.startsWith("naddr1", true)) {
            val address = Nip19.uriToRoute(keyB32 + restOfWord) ?: return null

            return DirtyKeyInfo(address, "") // no way to know when they address ends and dirt begins
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return null
}
