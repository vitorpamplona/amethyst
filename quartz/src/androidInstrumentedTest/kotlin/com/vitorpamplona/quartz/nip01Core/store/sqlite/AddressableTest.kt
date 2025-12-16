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
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Before
import org.junit.Test

class AddressableTest {
    private lateinit var db: EventStore

    val signer = NostrSignerSync()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("test.db")
        db = EventStore(context, "test.db", relayUrl = "testUrl")
    }

    @After
    fun tearDown() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("test.db")
    }

    @Test
    fun testReplacingAddressables() {
        val time = TimeUtils.now()
        val version1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
        val version2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
        val version3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

        db.insert(version1)

        db.assertQuery(version1, Filter(ids = listOf(version1.id)))

        db.insert(version2)

        db.assertQuery(null, Filter(ids = listOf(version1.id)))
        db.assertQuery(version2, Filter(ids = listOf(version2.id)))

        db.insert(version3)

        db.assertQuery(null, Filter(ids = listOf(version1.id)))
        db.assertQuery(null, Filter(ids = listOf(version2.id)))
        db.assertQuery(version3, Filter(ids = listOf(version3.id)))
    }

    @Test
    fun testBlockingOldAddressables() {
        val time = TimeUtils.now()
        val version1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
        val version2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
        val version3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

        db.insert(version3)

        db.assertQuery(version3, Filter(ids = listOf(version3.id)))

        try {
            db.insert(version2)
            fail("It should not allow inserting an older version")
        } catch (e: Exception) {
            TestCase.assertTrue(e is SQLiteConstraintException)
        }

        try {
            db.insert(version1)
            fail("It should not allow inserting an older version")
        } catch (e: Exception) {
            TestCase.assertTrue(e is SQLiteConstraintException)
        }

        db.assertQuery(version3, Filter(ids = listOf(version3.id)))
        db.assertQuery(null, Filter(ids = listOf(version2.id)))
        db.assertQuery(null, Filter(ids = listOf(version1.id)))
    }
}
