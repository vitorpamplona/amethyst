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
import com.vitorpamplona.quartz.nip01Core.EventHasher
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.sha256.sha256
import junit.framework.TestCase.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class Sha256Benchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun sha256Pool() {
        val event = Event.fromJson(largeKind1Event)
        val byteArray = EventHasher.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content).toByteArray()

        benchmarkRule.measureRepeated {
            // Should pass
            assertNotNull(sha256(byteArray))
        }
    }

    @Test
    fun sha256NewEachTime() {
        val event = Event.fromJson(largeKind1Event)
        val byteArray = EventHasher.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content).toByteArray()

        benchmarkRule.measureRepeated {
            val digest = MessageDigest.getInstance("SHA-256")
            assertNotNull(digest.digest(byteArray))
        }
    }

    @Test
    fun sha256Reuse() {
        val event = Event.fromJson(largeKind1Event)
        val byteArray = EventHasher.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content).toByteArray()

        val digest = MessageDigest.getInstance("SHA-256")

        benchmarkRule.measureRepeated {
            assertNotNull(digest.digest(byteArray))
            digest.reset()
        }
    }
}
