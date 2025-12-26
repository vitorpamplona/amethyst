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
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RightToVanishTest {
    private lateinit var db: EventStore

    val signer = NostrSignerSync()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = EventStore(context, null, relayUrl = "testUrl")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertDeleteEvent() {
        val time = TimeUtils.now()
        val note1 = signer.sign(TextNoteEvent.build("test1", createdAt = time))
        val note2 = signer.sign(TextNoteEvent.build("test2", createdAt = time + 1))
        val note3 = signer.sign(TextNoteEvent.build("test3", createdAt = time + 2))

        db.insert(note1)
        db.insert(note2)
        db.insert(note3)

        db.assertQuery(note1, Filter(ids = listOf(note1.id)))
        db.assertQuery(note2, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))

        val vanish = signer.sign(RequestToVanishEvent.build("testUrl", createdAt = time + 2))

        db.insert(vanish)

        db.assertQuery(vanish, Filter(ids = listOf(vanish.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))

        // trying to insert again should fail.
        try {
            db.insert(note1)
            fail("Should not be able to insert a deleted event")
        } catch (e: SQLiteConstraintException) {
            assertEquals("blocked: a request to vanish event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
        }

        db.assertQuery(vanish, Filter(ids = listOf(vanish.id)))
        db.assertQuery(null, Filter(ids = listOf(note1.id)))
        db.assertQuery(null, Filter(ids = listOf(note2.id)))
        db.assertQuery(note3, Filter(ids = listOf(note3.id)))
    }

    @Test
    fun testInsertDeleteGiftWrap() {
        val time = TimeUtils.now()

        val me = NostrSignerSync()
        val myFriend = NostrSignerSync()

        val note1 = me.sign(TextNoteEvent.build("test1", createdAt = time))
        val wrap1 = GiftWrapEvent.create(note1, me.pubKey)
        val wrap2 = GiftWrapEvent.create(note1, myFriend.pubKey)

        db.insert(wrap1)
        db.insert(wrap2)

        db.assertQuery(wrap1, Filter(ids = listOf(wrap1.id)))
        db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))

        val randomVanishToWrap = signer.sign(RequestToVanishEvent.build("testUrl", createdAt = time + 2))

        db.insert(randomVanishToWrap)

        db.assertQuery(wrap1, Filter(ids = listOf(wrap1.id)))
        db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))

        val vanish = me.sign(RequestToVanishEvent.build("testUrl", createdAt = time + 2))

        db.insert(vanish)

        db.assertQuery(vanish, Filter(ids = listOf(vanish.id)))
        db.assertQuery(null, Filter(ids = listOf(wrap1.id)))
        db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))

        // trying to insert again should fail.
        try {
            db.insert(wrap1)
            fail("Should not be able to insert a deleted event")
        } catch (e: SQLiteConstraintException) {
            assertEquals("blocked: a request to vanish event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
        }

        db.assertQuery(vanish, Filter(ids = listOf(vanish.id)))
        db.assertQuery(null, Filter(ids = listOf(wrap1.id)))
        db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))
    }
}
