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
package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.events.Event
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
class LargeDBSignatureCheck {
    @Test
    fun insertDatabaseSample() =
        runBlocking {
            val fullDBInputStream = getInstrumentation().context.assets.open("nostr_vitor_short.json")

            val eventArray =
                Event.mapper.readValue<ArrayList<Event>>(
                    InputStreamReader(fullDBInputStream),
                ) as List<Event>

            var counter = 0
            eventArray.forEach {
                assertTrue(it.hasValidSignature())
                counter++
            }

            assertEquals(eventArray.size, counter)
        }

    @Test
    fun insertStartupDatabase() =
        runBlocking {
            // This file includes duplicates
            val fullDBInputStream = getInstrumentation().context.assets.open("nostr_vitor_startup_data.json")

            val eventArray =
                Event.mapper.readValue<ArrayList<Event>>(
                    GZIPInputStream(fullDBInputStream),
                ) as List<Event>

            var counter = 0
            eventArray.forEach {
                if (it.sig != "") {
                    assertTrue(it.hasValidSignature())
                }
                counter++
            }

            assertEquals(eventArray.size, counter)
        }
}
