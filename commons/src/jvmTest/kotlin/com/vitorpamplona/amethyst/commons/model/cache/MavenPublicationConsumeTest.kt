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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nipFCMavenRepositories.release.MavenReleaseEvent
import com.vitorpamplona.quartz.nipFCMavenRepositories.snapshot.MavenSnapshotEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * NIP-FC library releases (3071) and snapshots (33071) reach the cache, where their card reads them:
 * a release by its id, a snapshot at its coordinate, where a newer build replaces the last.
 */
class MavenPublicationConsumeTest {
    private val owner = "aa".repeat(32)
    private val sig = "22".repeat(64)
    private val manifest = "bb".repeat(32)

    private fun event(
        idByte: String,
        kind: Int,
        createdAt: Long,
        version: String,
        vararg extra: Array<String>,
    ): Event =
        EventFactory.create(
            idByte.repeat(32),
            owner,
            createdAt,
            kind,
            arrayOf(
                *extra,
                arrayOf("j", "com.example:lib"),
                arrayOf("j", "com.example:lib-jvm"),
                arrayOf("v", version),
                arrayOf("manifest", manifest, "5784"),
                arrayOf("x", manifest),
                arrayOf("server", "https://blossom.example.com"),
            ),
            "",
            sig,
        )

    @Test
    fun storesAReleaseByItsId() {
        val cache = EventCache()
        val release = event("01", MavenReleaseEvent.KIND, 1_700_000_000L, "1.0.0")

        assertIs<MavenReleaseEvent>(release)
        assertTrue(cache.justConsume(release, null, true), release.toJson())
        assertSame(release, cache.getNoteIfExists(release.id)?.event)
    }

    @Test
    fun storesASnapshotAtItsCoordinateAndKeepsTheNewestBuild() {
        val cache = EventCache()
        val coordinate = arrayOf("d", "com.example:lib:1.1.0-SNAPSHOT")
        val older = event("02", MavenSnapshotEvent.KIND, 1_700_000_000L, "1.1.0-SNAPSHOT", coordinate)
        val newer = event("03", MavenSnapshotEvent.KIND, 1_700_000_100L, "1.1.0-SNAPSHOT", coordinate)

        assertIs<MavenSnapshotEvent>(older)
        assertIs<MavenSnapshotEvent>(newer)
        assertTrue(older.isWellFormed())
        assertTrue(cache.justConsume(older, null, true), older.toJson())
        assertTrue(cache.justConsume(newer, null, true), newer.toJson())
        assertSame(newer, cache.getAddressableNoteIfExists(newer.address())?.event)
    }
}
