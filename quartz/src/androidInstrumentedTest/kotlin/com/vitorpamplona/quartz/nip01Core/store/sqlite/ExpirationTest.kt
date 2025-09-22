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
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExpirationTest {
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
    fun testDeletingExpiredEvents() {
        val time = TimeUtils.now()

        val noteSafe =
            signer.sign(
                TextNoteEvent.build("test1", createdAt = time + 1) {
                    expiration(time + 100)
                },
            )

        db.insert(noteSafe)

        val noteToExpire =
            signer.sign(
                TextNoteEvent.build("test1", createdAt = time + 1) {
                    expiration(time + 1)
                },
            )

        db.insert(noteToExpire)

        db.assertQuery(noteToExpire, Filter(ids = listOf(noteToExpire.id)))

        Thread.sleep(2000)

        db.deleteExpiredEvents()

        db.assertQuery(null, Filter(ids = listOf(noteToExpire.id)))
        db.assertQuery(noteSafe, Filter(ids = listOf(noteSafe.id)))
    }

    @Test
    fun testInsertingExpiredEvents() {
        val time = TimeUtils.now()

        val note1 =
            signer.sign(
                TextNoteEvent.build("test1", createdAt = time - 12) {
                    expiration(time - 10)
                },
            )

        try {
            db.insert(note1)
            fail("Should not be able to insert expired events")
        } catch (e: Exception) {
            assertTrue(e is SQLiteConstraintException)
        }
    }
}
