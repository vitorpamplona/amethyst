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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.napplet.NappletRecentEncryptions
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.consent_delete_about
import com.vitorpamplona.amethyst.commons.resources.consent_delete_count
import com.vitorpamplona.amethyst.commons.resources.consent_delete_events
import com.vitorpamplona.amethyst.commons.resources.consent_delete_nothing
import com.vitorpamplona.amethyst.commons.resources.consent_list_adds
import com.vitorpamplona.amethyst.commons.resources.consent_list_keeps
import com.vitorpamplona.amethyst.commons.resources.consent_list_names_more
import com.vitorpamplona.amethyst.commons.resources.consent_list_no_change
import com.vitorpamplona.amethyst.commons.resources.consent_list_removes
import com.vitorpamplona.amethyst.commons.resources.consent_list_unknown
import com.vitorpamplona.amethyst.commons.resources.consent_list_wipe
import com.vitorpamplona.amethyst.commons.resources.consent_report_content
import com.vitorpamplona.amethyst.commons.resources.consent_report_person
import com.vitorpamplona.amethyst.commons.resources.consent_report_person_reason
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindNameFor
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent

/**
 * A kind-22242 that names no relay is a web app proving to its own server who you are (Brainstorm
 * signs `t=brainstorm_login`), not a NIP-42 relay login, and the prompt should say so.
 */
fun isAppLogin(
    kind: Int,
    tags: Array<Array<String>>,
): Boolean = kind == RelayAuthEvent.KIND && tags.none { it.size > 1 && it[0] == "relay" && it[1].isNotBlank() }

/** What a kind-13 seal carries, recovered from the encryption that produced its content. */
class SealContents(
    val recipient: HexKey,
    /** The rumor as an unsigned template, so the prompt can render the message itself. */
    val rumor: EventTemplate<Event>?,
    val rumorJson: String,
)

/**
 * The message inside a seal, when this broker encrypted it moments ago (see
 * [NappletRecentEncryptions]); null when it can't know, in which case the prompt says so instead of
 * showing ciphertext.
 */
fun sealContents(
    kind: Int,
    content: String,
    recent: NappletRecentEncryptions?,
): SealContents? {
    if (kind != SealEvent.KIND) return null
    val entry = recent?.lookup(content) ?: return null
    val rumor =
        runCatching {
            val node = JacksonMapper.mapper.readTree(entry.plaintext)
            val tags =
                node
                    .get("tags")
                    ?.map { tag -> tag.map { it.asText() }.toTypedArray() }
                    ?.toTypedArray() ?: emptyArray()
            EventTemplate<Event>(
                createdAt = node.get("created_at")?.asLong() ?: 0L,
                kind = node.get("kind")?.asInt() ?: return@runCatching null,
                tags = tags,
                content = node.get("content")?.asText() ?: "",
            )
        }.getOrNull()
    val pretty =
        runCatching {
            JacksonMapper.mapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(JacksonMapper.mapper.readTree(entry.plaintext))
        }.getOrDefault(entry.plaintext)
    return SealContents(entry.recipient, rumor, pretty)
}

/** One line telling the user what a people-list update changes, and whether it is alarming. */
class ListChange(
    val text: String,
    val warning: Boolean,
)

/**
 * For a follow list (kind 3) or mute list (kind 10000) the app is about to replace, what changes
 * compared with the list Amethyst already has: who is added, who is removed, and how many stay.
 *
 * A replaceable list is published whole, so a buggy app can wipe it in one signature. Saying
 * "Adds Nind · keeps the other 15" makes the normal case reassuring and the wipe impossible to miss.
 * Only the public `p` tags are compared; a mute list's private entries are encrypted and the app
 * rebuilds them itself.
 */
suspend fun listChange(
    account: Account?,
    kind: Int,
    tags: Array<Array<String>>,
): ListChange? {
    if (account == null) return null
    val current: Set<HexKey> =
        when (kind) {
            ContactListEvent.KIND ->
                account.kind3FollowList
                    .getFollowListEvent()
                    ?.tags
                    ?.taggedUserIds()
                    ?.toSet()
            MuteListEvent.KIND ->
                account.muteList
                    .getMuteList()
                    ?.tags
                    ?.taggedUserIds()
                    ?.toSet()
            else -> return null
        } ?: return ListChange(loadStringRes(Res.string.consent_list_unknown), warning = true)

    val next = tags.taggedUserIds().toSet()
    val added = next - current
    val removed = current - next
    val kept = (current intersect next).size

    if (next.isEmpty() && current.isNotEmpty()) {
        return ListChange(loadPluralStringRes(Res.plurals.consent_list_wipe, current.size, current.size), warning = true)
    }
    if (added.isEmpty() && removed.isEmpty()) return ListChange(loadStringRes(Res.string.consent_list_no_change), warning = false)

    val parts = mutableListOf<String>()
    if (added.isNotEmpty()) parts += loadStringRes(Res.string.consent_list_adds, names(added))
    if (removed.isNotEmpty()) parts += loadStringRes(Res.string.consent_list_removes, names(removed))
    if (kept > 0) parts += loadPluralStringRes(Res.plurals.consent_list_keeps, kept, kept)
    // Removing more than one person in a single update is unusual for a tap in an app: flag it.
    return ListChange(parts.joinToString(" · "), warning = removed.size > 1)
}

private suspend fun names(pubkeys: Set<HexKey>): String {
    val shown = pubkeys.take(2).map { counterpartyLabel(it) }
    val more = pubkeys.size - shown.size
    val listed = shown.joinToString(", ")
    return if (more > 0) loadPluralStringRes(Res.plurals.consent_list_names_more, more, listed, more) else listed
}

/**
 * For a NIP-09 deletion (kind 5), what it removes. The event itself is just ids, so the prompt
 * otherwise shows nothing: name the deleted kind ("Deletes 1 of your Reports"), and when the target
 * is cached and names a person, who it was about ("Deletes your Report about Vitor").
 */
suspend fun deletionChange(
    kind: Int,
    tags: Array<Array<String>>,
): ListChange? {
    if (kind != DeletionRequestEvent.KIND) return null
    val targets = tags.filter { it.size > 1 && (it[0] == "e" || it[0] == "a") }.map { it[1] }
    if (targets.isEmpty()) return ListChange(loadStringRes(Res.string.consent_delete_nothing), warning = false)
    val deletedKind = tags.firstOrNull { it.size > 1 && it[0] == "k" }?.get(1)?.toIntOrNull()
    val kindName = deletedKind?.let { kindNameFor(it) } ?: loadStringRes(Res.string.consent_delete_events)

    // One cached target that names a person reads best as "about Vitor".
    val subject =
        targets
            .singleOrNull()
            ?.let { LocalCache.getNoteIfExists(it)?.event }
            ?.tags
            ?.taggedUserIds()
            ?.firstOrNull()
    val text =
        if (subject != null) {
            loadStringRes(Res.string.consent_delete_about, kindName, counterpartyLabel(subject))
        } else {
            loadPluralStringRes(Res.plurals.consent_delete_count, targets.size, targets.size, kindName)
        }
    return ListChange(text, warning = targets.size > 1)
}

/**
 * For a NIP-56 report (kind 1984), who is being reported and why. The report renders as just its
 * reason ("Spam"); the person it accuses is the decision, so it leads the prompt in red.
 */
suspend fun reportChange(
    kind: Int,
    tags: Array<Array<String>>,
): ListChange? {
    if (kind != ReportEvent.KIND) return null
    val person = tags.firstOrNull { it.size > 1 && it[0] == "p" }
    val reason = person?.getOrNull(2)?.ifBlank { null }
    val text =
        when {
            person == null -> loadStringRes(Res.string.consent_report_content)
            reason != null -> loadStringRes(Res.string.consent_report_person_reason, counterpartyLabel(person[1]), reason)
            else -> loadStringRes(Res.string.consent_report_person, counterpartyLabel(person[1]))
        }
    return ListChange(text, warning = true)
}
