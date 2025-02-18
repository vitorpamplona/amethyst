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
import com.vitorpamplona.quartz.EventFactory
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class EventBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test
    fun parseREQString() {
        benchmarkRule.measureRepeated { EventMapper.mapper.readTree(reqResponseEvent) }
    }

    @Test
    fun parseEvent() {
        val msg = EventMapper.mapper.readTree(reqResponseEvent)

        benchmarkRule.measureRepeated { EventMapper.fromJson(msg[2]) }
    }

    @Test
    fun checkSignature() {
        val msg = EventMapper.mapper.readTree(reqResponseEvent)
        val event = EventMapper.fromJson(msg[2])
        benchmarkRule.measureRepeated {
            // Should pass
            assertTrue(event.verify())
        }
    }

    @Test
    fun eventFactoryKind1PerformanceTest() {
        val now = TimeUtils.now()
        val tags = arrayOf(arrayOf(""))
        benchmarkRule.measureRepeated {
            EventFactory.create("id", "pubkey", now, 1, tags, "content", "sig")
        }
    }

    @Test
    fun eventFactoryKind30818PerformanceTest() {
        val now = TimeUtils.now()
        val tags = arrayOf(arrayOf(""))
        benchmarkRule.measureRepeated {
            EventFactory.create("id", "pubkey", now, 30818, tags, "content", "sig")
        }
    }
}
