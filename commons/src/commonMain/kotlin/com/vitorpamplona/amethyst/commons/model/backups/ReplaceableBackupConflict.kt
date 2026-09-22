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
package com.vitorpamplona.amethyst.commons.model.backups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * What a newer, externally signed version of a backed-up replaceable event dropped
 * relative to the version this device had saved.
 *
 * @property removedTags tags of the saved version that the new one no longer carries.
 * @property removedFields kind:0 profile fields that were filled and are now gone or blank.
 * @property contentCleared the saved version had content (e.g. NIP-44 private list items)
 * and the new one has none.
 */
@Immutable
class DataLossReport(
    val removedTags: List<Array<String>>,
    val removedFields: List<String>,
    val contentCleared: Boolean,
) {
    fun isEmpty() = removedTags.isEmpty() && removedFields.isEmpty() && !contentCleared
}

/**
 * A newer version of one of the account's backed-up replaceable events arrived from outside
 * this app and dropped data the saved version had. The backup keeps [saved] until the user
 * either accepts [incoming] or re-signs [saved] on top of it.
 */
@Immutable
class ReplaceableBackupConflict(
    val saved: Event,
    val incoming: Event,
    val loss: DataLossReport,
    private val acceptIncoming: () -> Unit,
) {
    /** One conflict per replaceable slot: kind for replaceables, kind + d-tag for addressables. */
    val slot: String = backupSlot(incoming)

    fun keepIncoming() = acceptIncoming()
}

fun backupSlot(event: Event): String = event.kind.toString() + ":" + (event.tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: "")

object ReplaceableBackupDiff {
    /**
     * Tag names that clients rewrite for their own bookkeeping. Dropping them loses nothing
     * the user entered, so they don't count as data loss.
     */
    private val IGNORED_TAG_NAMES = setOf("alt", "client", "d", "expiration")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compares the [saved] backup with an [incoming] replacement of the same slot and reports
     * what the replacement dropped, or null when it dropped nothing (it only added or edited
     * entries, which means the other app did look at the previous version).
     *
     * Tags are matched by name + value, so a changed relay hint or petname on the same entry
     * is an edit, not a loss.
     */
    fun detectLoss(
        saved: Event,
        incoming: Event,
    ): DataLossReport? {
        if (saved.id == incoming.id) return null
        if (saved.kind != incoming.kind || saved.pubKey != incoming.pubKey) return null
        // Older or same-age versions never replace the backup in LocalCache anyway.
        if (incoming.createdAt <= saved.createdAt) return null

        val incomingKeys = HashSet<String>(incoming.tags.size)
        incoming.tags.forEach { tag -> tagKey(tag)?.let { incomingKeys.add(it) } }

        val removedTags =
            saved.tags.filter { tag ->
                val key = tagKey(tag)
                key != null && key !in incomingKeys
            }

        val removedFields: List<String>
        val contentCleared: Boolean

        when (saved.kind) {
            MetadataEvent.KIND -> {
                removedFields = removedProfileFields(saved.content, incoming.content)
                contentCleared = false
            }
            // kind:3 content is a deprecated relay map many clients drop on purpose.
            ContactListEvent.KIND -> {
                removedFields = emptyList()
                contentCleared = false
            }
            else -> {
                removedFields = emptyList()
                contentCleared = saved.content.isNotBlank() && incoming.content.isBlank()
            }
        }

        val report = DataLossReport(removedTags, removedFields, contentCleared)
        return if (report.isEmpty()) null else report
    }

    private fun tagKey(tag: Array<String>): String? {
        if (tag.isEmpty() || tag[0] in IGNORED_TAG_NAMES) return null
        return if (tag.size > 1) tag[0] + "\u0000" + tag[1] else tag[0]
    }

    private fun parseObject(content: String): JsonObject? = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()

    private fun JsonObject.filledKeys(): Set<String> =
        filter { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.content.isNotBlank() && value.content != "null"
                else -> true
            }
        }.keys

    private fun removedProfileFields(
        savedContent: String,
        incomingContent: String,
    ): List<String> {
        val saved = parseObject(savedContent)?.filledKeys() ?: return emptyList()
        val incoming = parseObject(incomingContent)?.filledKeys() ?: emptySet()
        return saved.filter { it !in incoming }.sorted()
    }
}
