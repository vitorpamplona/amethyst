package com.vitorpamplona.amethyst.ui.actions

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.toNpub

class NewMessageTagger(
    var message: String,
    var mentions: List<User>? = null,
    var replyTos: List<Note>? = null,
    var channelHex: String? = null
) {

    val directMentions = mutableSetOf<HexKey>()

    fun addUserToMentions(user: User) {
        directMentions.add(user.pubkeyHex)
        mentions = if (mentions?.contains(user) == true) mentions else mentions?.plus(user) ?: listOf(user)
    }

    fun addNoteToReplyTos(note: Note) {
        directMentions.add(note.idHex)

        note.author?.let { addUserToMentions(it) }
        replyTos = if (replyTos?.contains(note) == true) replyTos else replyTos?.plus(note) ?: listOf(note)
    }

    fun tagIndex(user: User): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (channelHex != null) 1 else 0) + (replyTos?.size ?: 0) + (mentions?.indexOf(user) ?: 0)
    }

    fun tagIndex(note: Note): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (channelHex != null) 1 else 0) + (replyTos?.indexOf(note) ?: 0)
    }

    fun run() {
        // adds all references to mentions and reply tos
        message.split('\n').forEach { paragraph: String ->
            paragraph.split(' ').forEach { word: String ->
                val results = parseDirtyWordForKey(word)

                if (results?.key?.type == Nip19.Type.USER) {
                    addUserToMentions(LocalCache.getOrCreateUser(results.key.hex))
                } else if (results?.key?.type == Nip19.Type.NOTE) {
                    addNoteToReplyTos(LocalCache.getOrCreateNote(results.key.hex))
                } else if (results?.key?.type == Nip19.Type.EVENT) {
                    addNoteToReplyTos(LocalCache.getOrCreateNote(results.key.hex))
                } else if (results?.key?.type == Nip19.Type.ADDRESS) {
                    val note = LocalCache.checkGetOrCreateAddressableNote(results.key.hex)
                    if (note != null) {
                        addNoteToReplyTos(note)
                    }
                }
            }
        }

        // Tags the text in the correct order.
        message = message.split('\n').map { paragraph: String ->
            paragraph.split(' ').map { word: String ->
                val results = parseDirtyWordForKey(word)
                if (results?.key?.type == Nip19.Type.USER) {
                    val user = LocalCache.getOrCreateUser(results.key.hex)

                    "nostr:${user.pubkeyNpub()}${results.restOfWord}"
                } else if (results?.key?.type == Nip19.Type.NOTE) {
                    val note = LocalCache.getOrCreateNote(results.key.hex)

                    "nostr:${note.toNEvent()}${results.restOfWord}"
                } else if (results?.key?.type == Nip19.Type.EVENT) {
                    val note = LocalCache.getOrCreateNote(results.key.hex)

                    "nostr:${note.toNEvent()}${results.restOfWord}"
                } else if (results?.key?.type == Nip19.Type.ADDRESS) {
                    val note = LocalCache.checkGetOrCreateAddressableNote(results.key.hex)
                    if (note != null) {
                        "nostr:${note.idNote()}${results.restOfWord}"
                    } else {
                        word
                    }
                } else {
                    word
                }
            }.joinToString(" ")
        }.joinToString("\n")
    }

    data class DirtyKeyInfo(val key: Nip19.Return, val restOfWord: String)

    fun parseDirtyWordForKey(mightBeAKey: String): DirtyKeyInfo? {
        var key = mightBeAKey
        if (key.startsWith("nostr:", true)) {
            key = key.substring("nostr:".length)
        }

        key = key.removePrefix("@")

        try {
            if (key.startsWith("nsec1", true)) {
                if (key.length < 63) {
                    return null
                }

                val keyB32 = key.substring(0, 63)
                val restOfWord = key.substring(63)
                // Converts to npub
                val pubkey = Nip19.uriToRoute(KeyPair(privKey = keyB32.bechToBytes()).pubKey.toNpub()) ?: return null

                return DirtyKeyInfo(pubkey, restOfWord)
            } else if (key.startsWith("npub1", true)) {
                if (key.length < 63) {
                    return null
                }

                val keyB32 = key.substring(0, 63)
                val restOfWord = key.substring(63)

                val pubkey = Nip19.uriToRoute(keyB32) ?: return null

                return DirtyKeyInfo(pubkey, restOfWord)
            } else if (key.startsWith("note1", true)) {
                if (key.length < 63) {
                    return null
                }

                val keyB32 = key.substring(0, 63)
                val restOfWord = key.substring(63)

                val noteId = Nip19.uriToRoute(keyB32) ?: return null

                return DirtyKeyInfo(noteId, restOfWord)
            } else if (key.startsWith("nprofile", true)) {
                val pubkeyRelay = Nip19.uriToRoute(key) ?: return null

                return DirtyKeyInfo(pubkeyRelay, pubkeyRelay.additionalChars)
            } else if (key.startsWith("nevent1", true)) {
                val noteRelayId = Nip19.uriToRoute(key) ?: return null

                return DirtyKeyInfo(noteRelayId, noteRelayId.additionalChars)
            } else if (key.startsWith("naddr1", true)) {
                val address = Nip19.uriToRoute(key) ?: return null

                return DirtyKeyInfo(address, address.additionalChars) // no way to know when they address ends and dirt begins
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}
