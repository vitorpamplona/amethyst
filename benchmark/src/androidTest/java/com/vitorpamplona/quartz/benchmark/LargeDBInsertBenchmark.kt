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
package com.vitorpamplona.quartz.benchmark

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.flattenToSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeDBInsertBenchmark : BaseLargeCacheBenchmark() {
    @get:Rule val benchmarkRule = BenchmarkRule()

    companion object {
        val allEvents = getEventDB().distinctBy { it.id }.filter { !it.isExpired() }.sortedBy { it.createdAt }
        val firstThousandEvents = allEvents.take(1000)
    }

    @Test
    fun benchInserting1000Events() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        benchmarkRule.measureRepeated {
            val db =
                this.runWithMeasurementDisabled {
                    context.deleteDatabase("test1.db")
                    val db = EventStore(context, "test1.db")
                    db
                }
            firstThousandEvents.forEach { event ->
                try {
                    db.insert(event)
                } catch (e: SQLiteException) {
                    Log.w("LargeDBInsertBenchmark", "Error inserting event: ${e.message} for event: ${event.toJson()}")
                }
            }
            this.runWithMeasurementDisabled {
                db.close()
                context.deleteDatabase("test1.db")
            }
        }
    }

    @Test
    fun bench40DeletionRequestsEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deletions = allEvents.filterIsInstance<DeletionEvent>()
        val deletionIds = deletions.map { it.deleteEventIds() }.flattenToSet()
        val deletionAddresses = deletions.map { it.deleteAddressIds() }.flattenToSet()

        val toBeDeletedEvents =
            allEvents.filter {
                (it.id in deletionIds || (it is AddressableEvent && it.addressTag() in deletionAddresses))
            }

        benchmarkRule.measureRepeated {
            val db =
                this.runWithMeasurementDisabled {
                    context.deleteDatabase("test1.db")
                    val db = EventStore(context, "test1.db")
                    toBeDeletedEvents.forEach { event ->
                        try {
                            db.insert(event)
                        } catch (e: SQLiteException) {
                            Log.w("LargeDBInsertBenchmark", "Error inserting event: ${e.message} for event: ${event.toJson()}")
                        }
                    }

                    db
                }

            deletions.forEach { event ->
                try {
                    db.insert(event)
                } catch (e: SQLiteException) {
                    Log.w("LargeDBInsertBenchmark", "Error inserting event: ${e.message} for event: $event")
                }
            }

            runWithMeasurementDisabled {
                db.close()
                context.deleteDatabase("test1.db")
            }
        }
    }
}
