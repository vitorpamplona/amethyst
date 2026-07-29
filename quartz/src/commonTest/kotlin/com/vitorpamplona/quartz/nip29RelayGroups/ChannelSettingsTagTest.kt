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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Buzz-only channel-settings tags on kind-9002 edit-metadata (`visibility`, `archived`) and the
 * `archived` reflection on the relay-signed kind-39000. These ride the 9002 as tags — Buzz reads its
 * own `visibility` vocabulary rather than the NIP-29 `private` status flag, and stamps `archived` onto
 * the 39000 for an archived channel.
 */
class ChannelSettingsTagTest {
    private val relaySelf = "aa".repeat(32)
    private val sig = "bb".repeat(64)
    private val id = "00".repeat(32)
    private val gid = "0123456789abcdef"

    private fun tagValue(
        tags: Array<Array<String>>,
        name: String,
    ): String? = tags.firstOrNull { it.isNotEmpty() && it[0] == name }?.getOrNull(1)

    @Test
    fun editEmitsVisibilityTagOnlyWhenSet() {
        val priv = EditMetadataEvent.build(gid, visibility = "private")
        assertEquals("private", tagValue(priv.tags, "visibility"))

        val open = EditMetadataEvent.build(gid, visibility = "open")
        assertEquals("open", tagValue(open.tags, "visibility"))

        // Absent by default so an ordinary metadata edit doesn't reclassify visibility.
        assertNull(tagValue(EditMetadataEvent.build(gid, name = "x").tags, "visibility"))
    }

    @Test
    fun editEmitsArchivedTagAsTrueFalse() {
        assertEquals("true", tagValue(EditMetadataEvent.build(gid, archived = true).tags, "archived"))
        assertEquals("false", tagValue(EditMetadataEvent.build(gid, archived = false).tags, "archived"))
        // Null archived means "don't touch it" — no tag emitted.
        assertNull(tagValue(EditMetadataEvent.build(gid, name = "x").tags, "archived"))
    }

    @Test
    fun metadataReflectsArchivedFlag() {
        val archivedTemplate = GroupMetadataEvent.build(gid, name = gid) { add(arrayOf("archived", "true")) }
        val archived = EventFactory.create(id, relaySelf, archivedTemplate.createdAt, GroupMetadataEvent.KIND, archivedTemplate.tags, "", sig) as GroupMetadataEvent
        assertTrue(archived.isArchived())

        val plainTemplate = GroupMetadataEvent.build(gid, name = gid)
        val plain = EventFactory.create(id, relaySelf, plainTemplate.createdAt, GroupMetadataEvent.KIND, plainTemplate.tags, "", sig) as GroupMetadataEvent
        assertFalse(plain.isArchived())
    }
}
