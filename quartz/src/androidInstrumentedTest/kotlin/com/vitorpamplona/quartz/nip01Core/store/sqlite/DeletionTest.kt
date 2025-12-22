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
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DeletionTest {
    private lateinit var db: EventStore

    val signer = NostrSignerSync()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = EventStore(context, null)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertDeleteEvent() {
        val note1 = signer.sign(TextNoteEvent.build("test1"))
        val note2 = signer.sign(TextNoteEvent.build("test2"))
        val note3 = signer.sign(TextNoteEvent.build("test3"))

        db.insert(note1)
        db.insert(note2)
        db.insert(note3)

        db.assertQuery(note1, Filter(ids = listOf(note1.id)))
        db.assertQuery(note2, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))

        val deletion = signer.sign(DeletionEvent.build(listOf(note1)))

        db.insert(deletion)

        db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(note2, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))

        // trying to insert again should fail.
        try {
            db.insert(note1)
            fail("Should not be able to insert a deleted event")
        } catch (e: SQLiteConstraintException) {
            assertEquals("blocked: a deletion event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
        }

        db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(note2, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))
    }

    @Test
    fun testInsertDeleteEventOfAddressable() {
        val time = TimeUtils.now()
        val note1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
        val note2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
        val note3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

        db.insert(note1)

        db.assertQuery(note1, Filter(ids = listOf(note1.id)))

        db.insert(note2)

        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(note2, Filter(ids = listOf(note2.id)))

        db.insert(note3)

        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))

        val deletion = signer.sign(DeletionEvent.build(listOf(note1)))

        db.insert(deletion)

        db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(null, Filter(ids = listOf(note3.id)))

        // trying to insert again should fail.
        try {
            db.insert(note1)
            fail("Should not be able to insert a deleted event")
        } catch (e: SQLiteConstraintException) {
            assertEquals("blocked: a deletion event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
        }

        db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(null, Filter(ids = listOf(note3.id)))
    }

    @Test
    fun testInsertDeleteEventOfAddressable2() {
        val time = TimeUtils.now()
        val note1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
        val note2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
        val note3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

        db.insert(note1)
        db.insert(note2)
        db.insert(note3)

        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))

        val deletion = signer.sign(DeletionEvent.buildAddressOnly(listOf(note1)))

        db.insert(deletion)

        db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(null, Filter(ids = listOf(note3.id)))

        // trying to insert again should fail.
        try {
            db.insert(note1)
            fail("Should not be able to insert a deleted event")
        } catch (e: SQLiteConstraintException) {
            assertEquals("blocked: a deletion event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
        }

        db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(null, Filter(ids = listOf(note3.id)))
    }
}
