/*
 * Copyright (c) 2025 Vitor Pamplona
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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.content.findIndexTagsWithEventsOrAddresses
import com.vitorpamplona.quartz.nip10Notes.content.findIndexTagsWithPeople
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

@Immutable
open class BaseNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedUsersCache: Set<String>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedNotesCache: Set<String>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedNIP19Cache: List<Entity>? = null

    fun citedNIP19(): List<Entity> {
        citedNIP19Cache?.let {
            return it
        }

        return findNostrUris(content).also { citedNIP19Cache = it }
    }

    fun citedUsers(): Set<HexKey> {
        citedUsersCache?.let {
            return it
        }

        val citedUsers = mutableSetOf<String>()

        findIndexTagsWithPeople(content, tags, citedUsers)
        citedNIP19().forEach { parsed ->
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
        citedNIP19().forEach { entity ->
            when (entity) {
                is NEvent -> citations.add(entity.hex)
                is NAddress -> citations.add(entity.aTag())
                is NNote -> citations.add(entity.hex)
                is NEmbed -> citations.add(entity.event.id)
            }
        }

        citedNotesCache = citations
        return citations
    }
}
