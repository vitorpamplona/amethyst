/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.Bech32
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.CancellationException

class NewMessageTagger(
    var message: String,
    var pTags: List<User>? = null,
    var eTags: List<Note>? = null,
    var channelHex: String? = null,
    var dao: Dao,
) {
    val directMentions = mutableSetOf<HexKey>()

    fun addUserToMentions(user: User) {
        directMentions.add(user.pubkeyHex)
        pTags = if (pTags?.contains(user) == true) pTags else pTags?.plus(user) ?: listOf(user)
    }

    fun addNoteToReplyTos(note: Note) {
        directMentions.add(note.idHex)

        note.author?.let { addUserToMentions(it) }
        eTags = if (eTags?.contains(note) == true) eTags else eTags?.plus(note) ?: listOf(note)
    }

    fun tagIndex(user: User): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (channelHex != null) 1 else 0) + (eTags?.size ?: 0) + (pTags?.indexOf(user) ?: 0)
    }

    fun tagIndex(note: Note): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (channelHex != null) 1 else 0) + (eTags?.indexOf(note) ?: 0)
    }

    suspend fun run() {
        // adds all references to mentions and reply tos
        message.split('\n').forEach { paragraph: String ->
            paragraph.split(' ').forEach { word: String ->
                val results = parseDirtyWordForKey(word)

                when (val entity = results?.key?.entity) {
                    is Nip19Bech32.NPub -> addUserToMentions(dao.getOrCreateUser(entity.hex))
                    is Nip19Bech32.NProfile -> addUserToMentions(dao.getOrCreateUser(entity.hex))

                    is Nip19Bech32.Note -> addNoteToReplyTos(dao.getOrCreateNote(entity.hex))
                    is Nip19Bech32.NEvent -> addNoteToReplyTos(dao.getOrCreateNote(entity.hex))
                    is Nip19Bech32.NEmbed -> addNoteToReplyTos(dao.getOrCreateNote(entity.event.id))

                    is Nip19Bech32.NAddress -> {
                        val note = dao.checkGetOrCreateAddressableNote(entity.atag)
                        if (note != null) {
                            addNoteToReplyTos(note)
                        }
                    }

                    is Nip19Bech32.NSec -> {}
                    is Nip19Bech32.NRelay -> {}
                }
            }
        }

        // Tags the text in the correct order.
        message =
            message
                .split('\n')
                .map { paragraph: String ->
                    paragraph
                        .split(' ')
                        .map { word: String ->
                            val results = parseDirtyWordForKey(word)
                            when (val entity = results?.key?.entity) {
                                is Nip19Bech32.NPub -> {
                                    getNostrAddress(dao.getOrCreateUser(entity.hex).pubkeyNpub(), results.restOfWord)
                                }
                                is Nip19Bech32.NProfile -> {
                                    getNostrAddress(dao.getOrCreateUser(entity.hex).pubkeyNpub(), results.restOfWord)
                                }

                                is Nip19Bech32.Note -> {
                                    getNostrAddress(dao.getOrCreateNote(entity.hex).toNEvent(), results.restOfWord)
                                }
                                is Nip19Bech32.NEvent -> {
                                    getNostrAddress(dao.getOrCreateNote(entity.hex).toNEvent(), results.restOfWord)
                                }

                                is Nip19Bech32.NAddress -> {
                                    val note = dao.checkGetOrCreateAddressableNote(entity.atag)
                                    if (note != null) {
                                        getNostrAddress(note.idNote(), results.restOfWord)
                                    } else {
                                        word
                                    }
                                }

                                is Nip19Bech32.NEmbed -> {
                                    word
                                }

                                is Nip19Bech32.NSec -> {
                                    word
                                }

                                is Nip19Bech32.NRelay -> {
                                    word
                                }

                                else -> {
                                    word
                                }
                            }
                        }
                        .joinToString(" ")
                }
                .joinToString("\n")
    }

    fun getNostrAddress(
        bechAddress: String,
        restOfTheWord: String?,
    ): String {
        return if (restOfTheWord.isNullOrEmpty()) {
            "nostr:$bechAddress"
        } else {
            if (Bech32.ALPHABET.contains(restOfTheWord.get(0), true)) {
                "nostr:$bechAddress $restOfTheWord"
            } else {
                "nostr:${bechAddress}$restOfTheWord"
            }
        }
    }

    @Immutable data class DirtyKeyInfo(val key: Nip19Bech32.ParseReturn, val restOfWord: String?)

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
                val pubkey =
                    Nip19Bech32.uriToRoute(KeyPair(privKey = keyB32.bechToBytes()).pubKey.toNpub()) ?: return null

                return DirtyKeyInfo(pubkey, restOfWord.ifEmpty { null })
            } else if (key.startsWith("npub1", true)) {
                if (key.length < 63) {
                    return null
                }

                val keyB32 = key.substring(0, 63)
                val restOfWord = key.substring(63)

                val pubkey = Nip19Bech32.uriToRoute(keyB32) ?: return null

                return DirtyKeyInfo(pubkey, restOfWord.ifEmpty { null })
            } else if (key.startsWith("note1", true)) {
                if (key.length < 63) {
                    return null
                }

                val keyB32 = key.substring(0, 63)
                val restOfWord = key.substring(63)

                val noteId = Nip19Bech32.uriToRoute(keyB32) ?: return null

                return DirtyKeyInfo(noteId, restOfWord.ifEmpty { null })
            } else if (key.startsWith("nprofile", true)) {
                val pubkeyRelay = Nip19Bech32.uriToRoute(key) ?: return null

                return DirtyKeyInfo(pubkeyRelay, pubkeyRelay.additionalChars)
            } else if (key.startsWith("nevent1", true)) {
                val noteRelayId = Nip19Bech32.uriToRoute(key) ?: return null

                return DirtyKeyInfo(noteRelayId, noteRelayId.additionalChars)
            } else if (key.startsWith("naddr1", true)) {
                val address = Nip19Bech32.uriToRoute(key) ?: return null

                return DirtyKeyInfo(
                    address,
                    address.additionalChars,
                ) // no way to know when they address ends and dirt begins
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }

        return null
    }
}

interface Dao {
    suspend fun getOrCreateUser(hex: String): User

    suspend fun getOrCreateNote(hex: String): Note

    suspend fun checkGetOrCreateAddressableNote(hex: String): Note?
}
