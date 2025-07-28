/**
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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class BasicTest {
    private lateinit var db: SQLiteEventStore

    val signer = NostrSignerSync()

    companion object Companion {
        val profile =
            MetadataEvent(
                id = "490d7439e530423f2540d4f2bdb73a0a2935f3df9e1f2a6f699a140c7db311fe",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669816,
                tags =
                    arrayOf(
                        arrayOf("alt", "User profile for Vitor"),
                        arrayOf("name", "Vitor"),
                    ),
                content = "{\"name\":\"Vitor\"}",
                sig = "977a6152199f17d103d8d56736ed1b7767054464cf9423d017c01c8cdd2344698f0a5e13da8dff98d01bb1f798837e3b6271e1fd1cac861bb90686f622ae6ef4",
            )

        val comment =
            CommentEvent(
                id = "fecb2ecf61a1433d417a784d10bd1e8ec19a916170a53ca8fb3a15fc666a6592",
                pubKey = "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
                createdAt = 1747753115,
                tags =
                    arrayOf(
                        arrayOf("alt", "Reply to geo:drt3n"),
                        arrayOf("I", "geo:drt3n"),
                        arrayOf("I", "geo:drt3"),
                        arrayOf("I", "geo:drt"),
                        arrayOf("I", "geo:dr"),
                        arrayOf("I", "geo:d"),
                        arrayOf("K", "geo"),
                        arrayOf("i", "geo:drt3n"),
                        arrayOf("i", "geo:drt3"),
                        arrayOf("i", "geo:drt"),
                        arrayOf("i", "geo:dr"),
                        arrayOf("i", "geo:d"),
                        arrayOf("k", "geo"),
                    ),
                content = "testing",
                sig = "12070e663272f1227c639fb834eb2122fc7bb995f4c49e55ebb1dfe2135ef7347d44810bacd2e64fd26b8826fd47d2800ce6c3d3b579bb3afe39088ffd4faa60",
            )
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SQLiteEventStore(context, null)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertDeleteEvent() {
        val note = signer.sign(TextNoteEvent.build("test1"))

        db.insertEvent(note)

        db.assertQuery(note, Filter(ids = listOf(note.id)))

        db.delete(note.id)

        db.assertQuery(null, Filter(ids = listOf(note.id)))

        db.insertEvent(note)

        db.assertQuery(note, Filter(ids = listOf(note.id)))
    }

    @Test
    fun testEmptyFilter() {
        val note1 = signer.sign(TextNoteEvent.build("test1", createdAt = 1))
        val note2 = signer.sign(TextNoteEvent.build("test2", createdAt = 2))

        db.insertEvent(note1)

        db.assertQuery(note1, Filter())

        db.insertEvent(note2)

        db.assertQuery(listOf(note2, note1), Filter())
    }

    @Test
    fun testLimitFilter() {
        val note1 = signer.sign(TextNoteEvent.build("test1", createdAt = 1))
        val note2 = signer.sign(TextNoteEvent.build("test2", createdAt = 2))
        val note3 = signer.sign(TextNoteEvent.build("test3", createdAt = 3))
        val note4 = signer.sign(TextNoteEvent.build("test4", createdAt = 4))

        db.insertEvent(note1)

        db.assertQuery(note1, Filter(limit = 1))

        db.insertEvent(note2)
        db.insertEvent(note3)
        db.insertEvent(note4)

        db.assertQuery(listOf(note4), Filter(limit = 1))
    }

    @Test
    fun testPubkeyTag() {
        db.insertEvent(comment)
        db.insertEvent(profile)

        db.assertQuery(
            comment,
            Filter(authors = listOf(comment.pubKey), tags = mapOf("I" to listOf("geo:drt3n"))),
        )
    }

    @Test
    fun testTagOnly() {
        db.insertEvent(comment)
        db.insertEvent(profile)

        db.assertQuery(comment, Filter(tags = mapOf("I" to listOf("geo:drt3n"))))
    }

    @Test
    fun testTagWithSinceOnly() {
        db.insertEvent(comment)
        db.insertEvent(profile)

        db.assertQuery(
            comment,
            Filter(tags = mapOf("I" to listOf("geo:drt3n")), since = comment.createdAt - 1),
        )
        db.assertQuery(
            comment,
            Filter(tags = mapOf("I" to listOf("geo:drt3n")), since = comment.createdAt),
        )
        db.assertQuery(
            null,
            Filter(tags = mapOf("I" to listOf("geo:drt3n")), since = comment.createdAt + 1),
        )
    }

    @Test
    fun testTagWithUntilOnly() {
        db.insertEvent(comment)
        db.insertEvent(profile)

        db.assertQuery(
            null,
            Filter(tags = mapOf("I" to listOf("geo:drt3n")), until = comment.createdAt - 1),
        )
        db.assertQuery(
            comment,
            Filter(tags = mapOf("I" to listOf("geo:drt3n")), until = comment.createdAt),
        )
        db.assertQuery(
            comment,
            Filter(tags = mapOf("I" to listOf("geo:drt3n")), until = comment.createdAt + 1),
        )
    }

    @Test
    fun testTagWithUntilOnlyEmitting() {
        db.insertEvent(comment)
        db.insertEvent(profile)

        db.query(Filter(tags = mapOf("I" to listOf("geo:drt3n")))) { event ->
            assertEquals(comment.toJson(), event.toJson())
        }
    }
}
