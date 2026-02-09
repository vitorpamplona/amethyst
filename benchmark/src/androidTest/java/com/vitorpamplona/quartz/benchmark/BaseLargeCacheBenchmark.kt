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

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.utils.cache.LargeCache
import org.junit.Assert.assertTrue
import java.util.function.Consumer
import java.util.zip.GZIPInputStream

open class BaseLargeCacheBenchmark {
    companion object {
        fun getEventDB(): List<Event> {
            // This file includes duplicates
            val fullDBInputStream = javaClass.classLoader!!.getResourceAsStream("nostr_vitor_startup_data.json")

            return JacksonMapper.mapper.readValue<ArrayList<Event>>(
                GZIPInputStream(fullDBInputStream),
            )
        }
    }

    fun getLargeCache(db: List<Event>): LargeCache<HexKey, Event> {
        val cache = LargeCache<HexKey, Event>()

        db.forEach { event ->
            cache.getOrCreate(event.id) { key -> event }
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
