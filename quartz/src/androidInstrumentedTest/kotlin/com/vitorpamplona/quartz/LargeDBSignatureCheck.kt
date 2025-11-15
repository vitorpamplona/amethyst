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
package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
class LargeDBSignatureCheck {
    @Test
    fun insertDatabaseSample() =
        runBlocking {
            val fullDBInputStream = javaClass.classLoader?.getResourceAsStream("nostr_vitor_short.json")

            val eventArray =
                JacksonMapper.mapper.readValue<ArrayList<Event>>(
                    InputStreamReader(fullDBInputStream),
                ) as List<Event>

            var counter = 0
            eventArray.forEach {
                assertTrue(it.verify())
                counter++
            }

            assertEquals(eventArray.size, counter)
        }

    @Test
    fun insertStartupDatabase() =
        runBlocking {
            // This file includes duplicates
            val fullDBInputStream = javaClass.classLoader?.getResourceAsStream("nostr_vitor_startup_data.json")

            val eventArray =
                JacksonMapper.mapper.readValue<ArrayList<Event>>(
                    GZIPInputStream(fullDBInputStream),
                ) as List<Event>

            var counter = 0
            eventArray.forEach {
                if (it.sig.isNotBlank() && !it.verify()) {
                    println("Skipping unverifiable event ${it.id}")
                }
                counter++
            }

            assertEquals(eventArray.size, counter)
        }
}
