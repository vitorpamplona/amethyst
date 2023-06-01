package com.vitorpamplona.amethyst.ui.actions

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.parseDirtyWordForKey
import com.vitorpamplona.amethyst.service.nip19.Nip19

class NewMessageTagger(var channelHex: String?, var mentions: List<User>?, var replyTos: List<Note>?, var message: String) {

    fun addUserToMentions(user: User) {
        mentions = if (mentions?.contains(user) == true) mentions else mentions?.plus(user) ?: listOf(user)
    }

    fun addNoteToReplyTos(note: Note) {
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
}
