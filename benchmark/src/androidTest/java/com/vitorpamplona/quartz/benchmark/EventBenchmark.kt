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

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.EventFactory
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.nip01Core.verifyId
import com.vitorpamplona.quartz.nip01Core.verifySignature
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
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
    fun parseComplete() {
        benchmarkRule.measureRepeated {
            val tree = JsonMapper.mapper.readTree(reqResponseEvent)
            val event = JsonMapper.fromJson(tree[2])
            assertTrue(event.verify())
        }
    }

    @Test
    fun parseREQString() {
        benchmarkRule.measureRepeated { JsonMapper.mapper.readTree(reqResponseEvent) }
    }

    @Test
    fun parseEvent() {
        val msg = JsonMapper.mapper.readTree(reqResponseEvent)

        benchmarkRule.measureRepeated { JsonMapper.fromJson(msg[2]) }
    }

    @Test
    fun checkId() {
        val msg = JsonMapper.mapper.readTree(reqResponseEvent)
        val event = JsonMapper.fromJson(msg[2])
        benchmarkRule.measureRepeated {
            // Should pass
            assertTrue(event.verifyId())
        }
    }

    @Test
    fun checkSignature() {
        val msg = JsonMapper.mapper.readTree(reqResponseEvent)
        val event = JsonMapper.fromJson(msg[2])
        benchmarkRule.measureRepeated {
            // Should pass
            assertTrue(event.verifySignature())
        }
    }

    @Test
    fun eventFactoryKind1PerformanceTest() {
        val now = TimeUtils.now()
        val tags = arrayOf(arrayOf(""))
        benchmarkRule.measureRepeated {
            EventFactory.create<TextNoteEvent>("id", "pubkey", now, 1, tags, "content", "sig")
        }
    }

    @Test
    fun eventFactoryKind30818PerformanceTest() {
        val now = TimeUtils.now()
        val tags = arrayOf(arrayOf(""))
        benchmarkRule.measureRepeated {
            EventFactory.create<TextNoteEvent>("id", "pubkey", now, 30818, tags, "content", "sig")
        }
    }
}
