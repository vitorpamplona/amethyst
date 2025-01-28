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
package com.vitorpamplona.quartz.nip10Notes

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedAddresses
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip10Notes.content.findIndexTagsWithEventsOrAddresses
import com.vitorpamplona.quartz.nip10Notes.content.findIndexTagsWithPeople
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.Note
import com.vitorpamplona.quartz.nip19Bech32.parse
import com.vitorpamplona.quartz.nip19Bech32.parseAtag
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityDefinitionEvent

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

    open fun markedReplyTos(): List<HexKey> {
        val newStyleReply = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "reply" }?.get(1)
        val newStyleRoot = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "root" }?.get(1)
        return listOfNotNull(newStyleReply, newStyleRoot)
    }

    open fun unMarkedReplyTos(): List<HexKey> = tags.filter { it.size > 1 && it.size <= 3 && it[0] == "e" }.map { it[1] }

    open fun replyingTo(): HexKey? {
        val oldStylePositional = tags.lastOrNull { it.size > 1 && it.size <= 3 && it[0] == "e" }?.get(1)
        val newStyleReply = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "reply" }?.get(1)
        val newStyleRoot = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "root" }?.get(1)

        return newStyleReply ?: newStyleRoot ?: oldStylePositional
    }

    open fun replyingToAddress(): ATag? {
        val oldStylePositional = tags.lastOrNull { it.size > 1 && it.size <= 3 && it[0] == "a" }?.let { ATag.parseAtag(it[1], it[2]) }
        val newStyleReply = tags.lastOrNull { it.size > 3 && it[0] == "a" && it[3] == "reply" }?.let { ATag.parseAtag(it[1], it[2]) }
        val newStyleRoot = tags.lastOrNull { it.size > 3 && it[0] == "a" && it[3] == "root" }?.let { ATag.parseAtag(it[1], it[2]) }

        return newStyleReply ?: newStyleRoot ?: oldStylePositional
    }

    open fun replyingToAddressOrEvent(): String? {
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

        val citedUsers = mutableSetOf<String>()

        findIndexTagsWithPeople(content, tags, citedUsers)

        Nip19Parser.parseAll(content).forEach { parsed ->
            when (parsed) {
                is NProfile -> citedUsers.add(parsed.hex)
                is NPub -> citedUsers.add(parsed.hex)
            }
        }

        citedUsersCache = citedUsers
        return citedUsers
    }

    fun findCitations(): Set<HexKey> {
        citedNotesCache?.let {
            return it
        }

        val citations = mutableSetOf<String>()

        findIndexTagsWithEventsOrAddresses(content, tags, citations).toMutableSet()

        Nip19Parser.parseAll(content).forEach { entity ->
            when (entity) {
                is NEvent -> citations.add(entity.hex)
                is NAddress -> citations.add(entity.aTag())
                is Note -> citations.add(entity.hex)
                is NEmbed -> citations.add(entity.event.id)
            }
        }

        citedNotesCache = citations
        return citations
    }

    fun tagsWithoutCitations(): List<String> {
        val certainRepliesTo = markedReplyTos()
        val uncertainRepliesTo = unMarkedReplyTos()

        val tagAddresses =
            taggedAddresses()
                .filter {
                    it.kind != CommunityDefinitionEvent.KIND && (kind != WikiNoteEvent.KIND || it.kind != WikiNoteEvent.KIND)
                    // removes forks from itself.
                }.map { it.toTag() }

        if (certainRepliesTo.isEmpty() && uncertainRepliesTo.isEmpty() && tagAddresses.isEmpty()) return emptyList()

        val citations = findCitations()

        return if (citations.isEmpty()) {
            if (certainRepliesTo.isNotEmpty()) {
                certainRepliesTo + tagAddresses
            } else {
                uncertainRepliesTo + tagAddresses
            }
        } else {
            if (certainRepliesTo.isNotEmpty()) {
                certainRepliesTo + tagAddresses.filter { it !in citations }
            } else {
                // mix bag between `e` for replies and `e` for citations
                uncertainRepliesTo.filter { it !in citations }
            }
        }
    }
}
