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
package com.vitorpamplona.quartz.benchmark

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeDBQueryingBenchmark : BaseLargeCacheBenchmark() {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    companion object {
        val allEvents = getEventDB().distinctBy { it.id }.sortedBy { it.createdAt }
    }

    lateinit var db: EventStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        context.deleteDatabase("allEvents.db")
        db = EventStore(context, "allEvents.db")
        allEvents.forEach { event ->
            try {
                db.insert(event)
            } catch (e: SQLiteException) {
                Log.w("LargeDBQueryingBenchmark", "Error inserting event: ${e.message} for event: ${event.toJson()}")
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("allEvents.db")
    }

    @Test
    fun benchQuerying1000Events() {
        benchmarkRule.measureRepeated {
            db.query<Event>(Filter(limit = 1000))
        }
    }

    @Test
    fun benchQuerying1614GiftWrapsEvents() {
        benchmarkRule.measureRepeated {
            db.query<GiftWrapEvent>(
                Filter(
                    kinds = listOf(GiftWrapEvent.KIND),
                    tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                ),
            )
        }
    }

    @Test
    fun benchQueryingEventsByAuthor() {
        benchmarkRule.measureRepeated {
            db.query<Event>(
                Filter(
                    authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                ),
            )
        }
    }

    @Test
    fun benchQueryingMetadataByAuthor() {
        benchmarkRule.measureRepeated {
            val result =
                db.query<MetadataEvent>(
                    Filter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    ),
                )
            assertEquals(1, result.size)
        }
    }

    @Test
    fun benchQueryingRelayListByAuthor() {
        benchmarkRule.measureRepeated {
            val result =
                db.query<AdvertisedRelayListEvent>(
                    Filter(
                        kinds = listOf(AdvertisedRelayListEvent.KIND),
                        authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    ),
                )
            assertEquals(0, result.size)
        }
    }

    @Test
    fun benchQueryingEmptyReturnByTags() {
        benchmarkRule.measureRepeated {
            db.query<Event>(
                Filter(
                    tags = mapOf("p" to listOf("176d972f60dcdc3212ed8c92ef85065c176d972f60dcdc3212ed8c92ef85065c")),
                ),
            )
        }
    }

    @Test
    fun benchQueryingEmptyReturnByIds() {
        benchmarkRule.measureRepeated {
            db.query<Event>(
                Filter(ids = listOf("176d972f60dcdc3212ed8c92ef85065c176d972f60dcdc3212ed8c92ef85065c")),
            )
        }
    }
}
