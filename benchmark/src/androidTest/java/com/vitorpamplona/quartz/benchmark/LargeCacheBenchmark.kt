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
import java.util.Arrays
import java.util.function.Consumer
import java.util.zip.GZIPInputStream

open class BaseLargeCacheBenchmark {
    fun getEventDB(): List<Event> {
        // This file includes duplicates
        val fullDBInputStream = getInstrumentation().context.assets.open("nostr_vitor_startup_data.json")

        return Event.mapper.readValue<ArrayList<Event>>(
            GZIPInputStream(fullDBInputStream),
        )
    }

    fun getLargeCache(db: List<Event>): LargeCache<HexKey, Event> {
        val cache = LargeCache<HexKey, Event>()

        db.forEach {
            cache.getOrCreate(it.id) { key ->
                it
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
}

@RunWith(AndroidJUnit4::class)
class LargeCacheForEachBenchmark : BaseLargeCacheBenchmark() {
    @get:Rule val benchmarkRule = BenchmarkRule()

    // 191,353   ns           0 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.benchForEachConsumerList
    @Test
    fun benchForEachConsumerList() {
        val db = getEventDB().distinctBy { it.id }.toList()
        benchmarkRule.measureRepeated { db.forEach(consumer) }
    }

    // 245,319   ns           1 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.benchForEachRegularList
    @Test
    fun benchForEachRegularList() {
        val db = getEventDB().distinctBy { it.id }.toList()
        benchmarkRule.measureRepeated { db.forEach { hasId(it) } }
    }

    // 435,097   ns           1 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.forEach
    @Test
    fun forEach() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.forEach { key, it -> hasId(it) } }
    }

    // 525,329   ns          18 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.filterKind1List
    @Test
    fun filterKind1List() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.filter { key, it -> it.kind == 1 } }
    }

    // 690,323   ns        3581 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.filterKind1Set
    @Test
    fun filterKind1Set() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.filterIntoSet { key, it -> it.kind == 1 } }
    }

    // 641,179   ns          23 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapToSigs
    @Test
    fun mapToSigs() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.map { key, it -> it.sig } }
    }

    // 590,930   ns          23 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapNotNullTagList
    @Test
    fun mapNotNullTagList() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.mapNotNull { key, it -> it.tags.firstOrNull() } }
    }

    // HashSet: 1,817,833   ns       30632 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapNotNullTagSet
    // LinkedHashSet: 2,057,674   ns       30633 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapNotNullTagSet
    @Test
    fun mapNotNullTagSet() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.mapNotNullIntoSet { key, it -> it.tags.firstOrNull() } }
    }

    // 2,619,604   ns       93505 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapFlattenTagList
    @Test
    fun mapFlattenTagList() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.mapFlatten { key, it -> it.tags.asList() } }
    }

    // ----

    // 4,802,623   ns      114928 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapFlattenTagSetAsList
    @Test
    fun mapFlattenTagSetAsList() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.mapFlattenIntoSet { key, it -> it.tags.asList() } }
    }

    // 5,695,432   ns      146089 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapFlattenTagSetArraysAsList
    @Test
    fun mapFlattenTagSetArraysAsList() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.mapFlattenIntoSet { key, it -> Arrays.asList(*it.tags) } }
    }

    // 7,008,496   ns      176161 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapFlattenTagSetListOf
    @Test
    fun mapFlattenTagSetListOf() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.mapFlattenIntoSet { key, it -> listOf(*it.tags) } }
    }

    // 7,032,714   ns      193834 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.mapFlattenTagSetToList
    @Test
    fun mapFlattenTagSetToList() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.mapFlattenIntoSet { key, it -> it.tags.toList() } }
    }

    // ----

    // 467,227   ns           1 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.sumOfKinds
    @Test
    fun sumOfKinds() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.sumOf { key, it -> it.kind } }
    }

    // 458,998   ns           1 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.sumOfKindLong
    @Test
    fun sumOfKindLong() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.sumOfLong { key, it -> it.createdAt } }
    }

    // 1,021,368   ns       11683 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.groupByKind
    @Test
    fun groupByKind() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.groupBy { key, it -> it.kind } }
    }

    // 1,133,156   ns       39899 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.countByGroupKind
    @Test
    fun countByGroupKind() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.countByGroup { key, it -> it.kind } }
    }

    // 428,641   ns           1 allocs    Trace    EMULATOR_LargeCacheForEachBenchmark.countNotEmptyTags
    @Test
    fun countNotEmptyTags() {
        val cache = getLargeCache(getEventDB())
        benchmarkRule.measureRepeated { cache.count { key, it -> it.tags.isNotEmpty() } }
    }
}
