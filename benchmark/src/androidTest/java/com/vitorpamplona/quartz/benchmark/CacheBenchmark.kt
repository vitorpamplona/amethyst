/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.commons.data.LargeCache
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.zip.GZIPInputStream

open class BaseCacheBenchmark {
    fun getEventDB(): List<Event> {
        // This file includes duplicates
        val fullDBInputStream = getInstrumentation().context.assets.open("nostr_vitor_startup_data.json")

        return Event.mapper.readValue<ArrayList<Event>>(
            GZIPInputStream(fullDBInputStream),
        )
    }

    fun getConcurrentSkipList(db: List<Event>): ConcurrentSkipListMap<HexKey, Event> {
        val cache = ConcurrentSkipListMap<HexKey, Event>()

        db.forEach {
            cache.put(it.id, it)
        }

        return cache
    }

    fun getConcurrentHashMap(db: List<Event>): ConcurrentHashMap<HexKey, Event> {
        val cache = ConcurrentHashMap<HexKey, Event>()

        db.forEach {
            cache.put(it.id, it)
        }

        return cache
    }

    fun getRegularHashMap(db: List<Event>): HashMap<HexKey, Event> {
        val cache = HashMap<HexKey, Event>()

        db.forEach {
            cache.put(it.id, it)
        }

        return cache
    }

    fun getLargeCache(db: List<Event>): LargeCache<HexKey, Event> {
        val cache = LargeCache<HexKey, Event>()

        db.forEach { event ->
            cache.getOrCreate(event.id) {
                event
            }
        }

        return cache
    }

    fun hasId(event: Event) {
        assertTrue(event.id.isNotEmpty())
    }

    val consumer =
        Consumer<Event> {
            hasId(it)
        }

    val biconsumer =
        BiConsumer<HexKey, Event> { hex, event ->
            hasId(event)
        }
}

@RunWith(AndroidJUnit4::class)
class CacheLoadingBenchmark : BaseCacheBenchmark() {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test
    fun loadConcurrentSkipList() {
        val db = getEventDB()
        benchmarkRule.measureRepeated { getConcurrentSkipList(db) }
    }

    @Test
    fun loadCountDuplicates() {
        val db = getEventDB().distinctBy { it.id }.toList()
    }

    @Test
    fun loadConcurrentHashMap() {
        val db = getEventDB()
        benchmarkRule.measureRepeated { getConcurrentHashMap(db) }
    }

    @Test
    fun loadRegularHashMap() {
        val db = getEventDB()
        benchmarkRule.measureRepeated { getRegularHashMap(db) }
    }

    @Test
    fun loadLargeCache() {
        val db = getEventDB()
        benchmarkRule.measureRepeated { getLargeCache(db) }
    }
}

@RunWith(AndroidJUnit4::class)
class CommonForEachBenchmark : BaseCacheBenchmark() {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchForEachRegularList() {
        val db = getEventDB().distinctBy { it.id }.toList()
        benchmarkRule.measureRepeated { db.forEach { hasId(it) } }
    }

    @Test
    fun benchForEachConsumerList() {
        val db = getEventDB().distinctBy { it.id }.toList()
        benchmarkRule.measureRepeated { db.forEach(consumer) }
    }

    @Test
    fun forEachConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach { hasId(it.value) } }
    }

    @Test
    fun forEachConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach { hasId(it.value) } }
    }

    @Test
    fun forEachRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach { hasId(it.value) } }
    }

    @Test
    fun consumerForEachConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }

    @Test
    fun consumerForEachConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }

    @Test
    fun consumerForEachRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }

    @Test
    fun consumerForEachLargeCache() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }
}

@RunWith(AndroidJUnit4::class)
class CommonMapBenchmark : BaseCacheBenchmark() {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchMapRegularList() {
        val db = getEventDB().distinctBy { it.id }.toList()
        benchmarkRule.measureRepeated { db.map { it.id } }
    }

    @Test
    fun consumerMapConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated { cache.map { it.value.id } }
    }

    @Test
    fun consumerMapConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.map { it.value.id } }
    }

    @Test
    fun consumerMapLargeCache() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.map { key, item -> item.id } }
    }
}

@RunWith(AndroidJUnit4::class)
class BiggerForEachBenchmark : BaseCacheBenchmark() {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test
    fun benchForEachRegularList() {
        val db = getEventDB().distinctBy { it.id }.toList()
        benchmarkRule.measureRepeated { db.forEach { hasId(it) } }
    }

    @Test
    fun benchForEachConsumerList() {
        val db = getEventDB().distinctBy { it.id }.toList()
        benchmarkRule.measureRepeated { db.forEach(consumer) }
    }

    @Test
    fun forEachConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach { hasId(it.value) } }
    }

    @Test
    fun forEachConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach { hasId(it.value) } }
    }

    @Test
    fun forEachRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach { hasId(it.value) } }
    }

    @Test
    fun consumerForEachConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }

    @Test
    fun consumerForEachConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }

    @Test
    fun consumerForEachRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }

    @Test
    fun consumerForEachLargeCache() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach(biconsumer) }
    }

    @Test
    fun valuesConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated { cache.values.forEach { hasId(it) } }
    }

    @Test
    fun valuesConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.values.forEach { hasId(it) } }
    }

    @Test
    fun valuesRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.values.forEach { hasId(it) } }
    }

    @Test
    fun consumerValuesConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated { cache.values.forEach(consumer) }
    }

    @Test
    fun consumerValuesConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.values.forEach(consumer) }
    }

    @Test
    fun consumerValuesRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated { cache.values.forEach(consumer) }
    }

    @Test
    fun iterableConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated {
            with(cache.iterator()) {
                while (hasNext()) {
                    hasId(next().value)
                }
            }
        }
    }

    @Test
    fun iterableConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated {
            with(cache.iterator()) {
                while (hasNext()) {
                    hasId(next().value)
                }
            }
        }
    }

    @Test
    fun iterableRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated {
            with(cache.iterator()) {
                while (hasNext()) {
                    hasId(next().value)
                }
            }
        }
    }

    @Test
    fun iterableValuesConcurrentSkipList() {
        val cache = getConcurrentSkipList(getEventDB())
        benchmarkRule.measureRepeated {
            with(cache.values.iterator()) {
                while (hasNext()) {
                    hasId(next())
                }
            }
        }
    }

    @Test
    fun iterableValuesConcurrentHashMap() {
        val cache = getConcurrentHashMap(getEventDB())
        benchmarkRule.measureRepeated {
            with(cache.values.iterator()) {
                while (hasNext()) {
                    hasId(next())
                }
            }
        }
    }

    @Test
    fun iterableValuesRegularHashMap() {
        val cache = getRegularHashMap(getEventDB())
        benchmarkRule.measureRepeated {
            with(cache.values.iterator()) {
                while (hasNext()) {
                    hasId(next())
                }
            }
        }
    }
}
