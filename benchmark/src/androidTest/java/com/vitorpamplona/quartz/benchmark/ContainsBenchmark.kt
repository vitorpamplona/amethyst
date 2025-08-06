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
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.containsAny
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContainsBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    private val test =
        """Contrary to popular belief, Lorem Ipsum is not simply random text. It has roots in a piece of classical Latin literature from 45 BC, making it over 2000 years old. Richard McClintock, a Latin professor at Hampden-Sydney College in Virginia, looked up one of the more obscure Latin words, consectetur, from a Lorem Ipsum passage, and going through the cites of the word in classical literature, discovered the undoubtable source. Lorem Ipsum comes from sections 1.10.32 and 1.10.33 of "de Finibus Bonorum et Malorum" (The Extremes of Good and Evil) by Cicero, written in 45 BC. This book is a treatise on the theory of ethics, very popular during the Renaissance. The first line of Lorem Ipsum, "Lorem ipsum dolor sit amet..", comes from a line in section 1.10.32.

The standard chunk of Lorem Ipsum used since the 1500s is reproduced below for those interested. Sections 1.10.32 and 1.10.33 from "de Finibus Bonorum et Malorum" by Cicero are also reproduced in their exact original form, accompanied by English versions from the 1914 translation by H. Rackham.
""".intern()

    val atTheMiddle = DualCase("Lorem Ipsum".lowercase(), "Lorem Ipsum".uppercase())
    val atTheBeginning = DualCase("contrAry".lowercase(), "contrAry".uppercase())

    val atTheEndCase = DualCase("h. rackham".lowercase(), "h. rackham".uppercase())

    val lastCase =
        listOf(
            DualCase("my mom".lowercase(), "my mom".uppercase()),
            DualCase("my dad".lowercase(), "my dad".uppercase()),
            DualCase("h. rackham".lowercase(), "h. rackham".uppercase()),
        )

    @Test
    fun middleCaseKotlin() {
        benchmarkRule.measureRepeated { assertTrue(test.contains(atTheMiddle.lowercase, true)) }
    }

    @Test
    fun middleCaseOurs() {
        val list = listOf(atTheMiddle)
        benchmarkRule.measureRepeated { assertTrue(test.containsAny(list)) }
    }

    @Test
    fun atTheBeginningKotlin() {
        benchmarkRule.measureRepeated { assertTrue(test.contains(atTheBeginning.lowercase, true)) }
    }

    @Test
    fun atTheBeginningOurs() {
        val list = listOf(atTheBeginning)
        benchmarkRule.measureRepeated { assertTrue(test.containsAny(list)) }
    }

    @Test
    fun atTheEndKotlin() {
        benchmarkRule.measureRepeated { assertTrue(test.contains(atTheEndCase.lowercase, true)) }
    }

    @Test
    fun atTheEndOurs() {
        val list = listOf(atTheEndCase)
        benchmarkRule.measureRepeated { assertTrue(test.containsAny(list)) }
    }

    @Test
    fun theLastAtTheEndKotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(
                lastCase.any { test.contains(it.lowercase, true) },
            )
        }
    }

    @Test
    fun theLastAtTheEndOurs() {
        benchmarkRule.measureRepeated { assertTrue(test.containsAny(lastCase)) }
    }
}
