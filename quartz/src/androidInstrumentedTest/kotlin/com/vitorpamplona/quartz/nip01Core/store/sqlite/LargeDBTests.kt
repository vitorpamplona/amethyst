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
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.utils.Log
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class LargeDBTests {
    companion object {
        fun getEventDB(): List<Event> {
            // This file includes duplicates
            val fullDBInputStream = javaClass.classLoader?.getResourceAsStream("nostr_vitor_startup_data.json")

            return JacksonMapper.mapper.readValue<ArrayList<Event>>(
                GZIPInputStream(fullDBInputStream),
            )
        }

        val events = getEventDB().distinctBy { it.id }.filter { !it.isExpired() }.sortedBy { it.createdAt }
    }

    private lateinit var db: EventStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = EventStore(context, "largeDBTest.db")
        db.store.clearDB()
    }

    @After
    fun tearDown() {
        db.store.clearDB()
        db.close()
    }

    @Test
    fun insertHeavyEvent() {
        events.first { it.id == "3f34b8cb682307ec11753de4669ce8948e95fd6fb360d79136446c5547fd235e" }.let { event ->
            println(event.toJson())
            try {
                val measure =
                    measureTimeMillis {
                        db.insert(event)
                    }
                if (measure > 1) {
                    println("Inserted event ${event.id} of kind ${event.kind} in $measure ms")
                }
            } catch (e: SQLiteException) {
                Log.w("LargeDBTests", "Error inserting event: ${e.message} for event: ${event.toJson()}")
            }
        }
    }

    @Test
    fun insertDatabase() {
        events.forEach { event ->
            try {
                val measure =
                    measureTimeMillis {
                        db.insert(event)
                    }
                if (measure > 1) {
                    println("Inserted event ${event.id} of kind ${event.kind} in $measure ms")
                }
            } catch (e: SQLiteException) {
                Log.w("LargeDBTests", "Error inserting event: ${e.message} for event: ${event.toJson()}")
            }
        }
    }
}
