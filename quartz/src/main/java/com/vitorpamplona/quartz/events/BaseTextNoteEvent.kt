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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip19Bech32.nip19regex
import java.util.regex.Pattern

val tagSearch = Pattern.compile("(?:\\s|\\A)\\#\\[([0-9]+)\\]")
val hashtagSearch = Pattern.compile("(?:\\s|\\A)#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)")

@Immutable
open class BaseTextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun mentions() = taggedUsers()

    fun isAFork() = tags.any { it.size > 3 && (it[0] == "a" || it[0] == "e") && it[3] == "fork" }

    fun forkFromAddress() =
        tags.firstOrNull { it.size > 3 && it[0] == "a" && it[3] == "fork" }?.let {
            val aTagValue = it[1]
            val relay = it.getOrNull(2)

            ATag.parse(aTagValue, relay)
        }

    fun forkFromVersion() = tags.firstOrNull { it.size > 3 && it[0] == "e" && it[3] == "fork" }?.get(1)

    fun isForkFromAddressWithPubkey(authorHex: HexKey) = tags.any { it.size > 3 && it[0] == "a" && it[3] == "fork" && it[1].contains(authorHex) }

    open fun replyTos(): List<HexKey> {
        val oldStylePositional = tags.filter { it.size > 1 && it.size <= 3 && it[0] == "e" }.map { it[1] }
        val newStyleReply = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "reply" }?.get(1)
        val newStyleRoot = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "root" }?.get(1)

        val newStyleReplyTos = listOfNotNull(newStyleReply, newStyleRoot)

        return if (newStyleReplyTos.isNotEmpty()) {
            newStyleReplyTos
        } else {
            oldStylePositional
        }
    }

    fun replyingTo(): HexKey? {
        val oldStylePositional = tags.lastOrNull { it.size > 1 && it.size <= 3 && it[0] == "e" }?.get(1)
        val newStyleReply = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "reply" }?.get(1)
        val newStyleRoot = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "root" }?.get(1)

        return newStyleReply ?: newStyleRoot ?: oldStylePositional
    }

    fun replyingToAddress(): ATag? {
        val oldStylePositional = tags.lastOrNull { it.size > 1 && it.size <= 3 && it[0] == "a" }?.let { ATag.parseAtag(it[1], it[2]) }
        val newStyleReply = tags.lastOrNull { it.size > 3 && it[0] == "a" && it[3] == "reply" }?.let { ATag.parseAtag(it[1], it[2]) }
        val newStyleRoot = tags.lastOrNull { it.size > 3 && it[0] == "a" && it[3] == "root" }?.let { ATag.parseAtag(it[1], it[2]) }

        return newStyleReply ?: newStyleRoot ?: oldStylePositional
    }

    fun replyingToAddressOrEvent(): String? {
        val oldStylePositional = tags.lastOrNull { it.size > 1 && it.size <= 3 && (it[0] == "e" || it[0] == "a") }?.get(1)
        val newStyleReply = tags.lastOrNull { it.size > 3 && (it[0] == "e" || it[0] == "a") && it[3] == "reply" }?.get(1)
        val newStyleRoot = tags.lastOrNull { it.size > 3 && (it[0] == "e" || it[0] == "a") && it[3] == "root" }?.get(1)

        return newStyleReply ?: newStyleRoot ?: oldStylePositional
    }

    @Transient private var citedUsersCache: Set<String>? = null

    @Transient private var citedNotesCache: Set<String>? = null

    fun citedUsers(): Set<HexKey> {
        citedUsersCache?.let {
            return it
        }

        val matcher = tagSearch.matcher(content)
        val returningList = mutableSetOf<String>()
        while (matcher.find()) {
            try {
                val tag = matcher.group(1)?.let { tags[it.toInt()] }
                if (tag != null && tag.size > 1 && tag[0] == "p") {
                    returningList.add(tag[1])
                }
            } catch (e: Exception) {
            }
        }

        val matcher2 = nip19regex.matcher(content)
        while (matcher2.find()) {
            val type = matcher2.group(2) // npub1
            val key = matcher2.group(3) // bech32
            val additionalChars = matcher2.group(4) // additional chars

            try {
                if (type != null) {
                    val parsed = Nip19Bech32.parseComponents(type, key, additionalChars)?.entity

                    if (parsed != null) {
                        if (parsed is Nip19Bech32.NProfile) {
                            returningList.add(parsed.hex)
                        }
                        if (parsed is Nip19Bech32.NPub) {
                            returningList.add(parsed.hex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("Unable to parse cited users that matched a NIP19 regex", e)
            }
        }

        citedUsersCache = returningList
        return returningList
    }

    fun findCitations(): Set<HexKey> {
        citedNotesCache?.let {
            return it
        }

        val citations = mutableSetOf<HexKey>()
        // Removes citations from replies:
        val matcher = tagSearch.matcher(content)
        while (matcher.find()) {
            try {
                val tag = matcher.group(1)?.let { tags[it.toInt()] }
                if (tag != null && tag.size > 1 && tag[0] == "e") {
                    citations.add(tag[1])
                }
                if (tag != null && tag.size > 1 && tag[0] == "a") {
                    citations.add(tag[1])
                }
            } catch (e: Exception) {
            }
        }

        val matcher2 = nip19regex.matcher(content)
        while (matcher2.find()) {
            val type = matcher2.group(2) // npub1
            val key = matcher2.group(3) // bech32
            val additionalChars = matcher2.group(4) // additional chars

            if (type != null) {
                val parsed = Nip19Bech32.parseComponents(type, key, additionalChars)?.entity

                if (parsed != null) {
                    when (parsed) {
                        is Nip19Bech32.NEvent -> citations.add(parsed.hex)
                        is Nip19Bech32.NAddress -> citations.add(parsed.atag)
                        is Nip19Bech32.Note -> citations.add(parsed.hex)
                        is Nip19Bech32.NEmbed -> citations.add(parsed.event.id)
                    }
                }
            }
        }

        citedNotesCache = citations
        return citations
    }

    fun tagsWithoutCitations(): List<String> {
        val repliesTo = replyTos()
        val tagAddresses =
            taggedAddresses().filter {
                it.kind != CommunityDefinitionEvent.KIND &&
                    it.kind != WikiNoteEvent.KIND
            }.map { it.toTag() }
        if (repliesTo.isEmpty() && tagAddresses.isEmpty()) return emptyList()

        val citations = findCitations()

        return if (citations.isEmpty()) {
            repliesTo + tagAddresses
        } else {
            repliesTo.filter { it !in citations }
        }
    }
}

fun findHashtags(content: String): List<String> {
    val matcher = hashtagSearch.matcher(content)
    val returningList = mutableSetOf<String>()
    while (matcher.find()) {
        try {
            val tag = matcher.group(1)
            if (tag != null && tag.isNotBlank()) {
                returningList.add(tag)
            }
        } catch (e: Exception) {
        }
    }
    return returningList.toList()
}
