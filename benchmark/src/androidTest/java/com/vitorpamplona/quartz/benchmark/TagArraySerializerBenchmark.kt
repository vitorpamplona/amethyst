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
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import junit.framework.TestCase.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagArraySerializerBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    val tags =
        arrayOf(
            arrayOf("title", "Retro Computer Fans"),
            arrayOf("d", "xmbspe8rddsq"),
            arrayOf("image", "https://blog.johnnovak.net/2022/04/15/achieving-period-correct-graphics-in-personal-computer-emulators-part-1-the-amiga/img/dream-setup.jpg"),
            arrayOf("p", "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da"),
            arrayOf("p", "9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740"),
            arrayOf("p", "4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382"),
            arrayOf("p", "ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd"),
            arrayOf("p", "47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f"),
            arrayOf("p", "2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf"),
            arrayOf("p", "6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94"),
            arrayOf("description", "Retro computer fans and enthusiasts "),
        )

    @Test
    fun serializeCommonTagArray() {
        benchmarkRule.measureRepeated { assertNotNull(OptimizedJsonMapper.toJson(tags)) }
    }

    @Test
    fun deserializeCommonTagArray() {
        val serialized = JacksonMapper.toJson(tags)
        benchmarkRule.measureRepeated { assertNotNull(OptimizedJsonMapper.fromJsonToTagArray(serialized)) }
    }
}
