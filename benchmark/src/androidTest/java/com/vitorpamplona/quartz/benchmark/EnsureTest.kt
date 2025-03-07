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
import com.vitorpamplona.quartz.utils.ensure
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnsureTest {
    @get:Rule
    val scope = BenchmarkRule()

    private val testArgs = arrayOf("0", "1f")

    companion object {
        const val TAG = "0"
        const val KEY_SIZE = 2

        private val isHexChar =
            BooleanArray(256).apply {
                "0123456789abcdefABCDEF".forEach { this[it.code] = true }
            }

        @JvmStatic
        fun String.isHex() = all { isHexChar[it.code] }
    }

    fun directCheckElse(args: Array<String>) =
        if (args[0] != TAG || args[1].length != KEY_SIZE || !args[1].isHex()) {
            null
        } else {
            args[0] + args[1]
        }

    fun directCheck(args: Array<String>): String? {
        if (args[0] != TAG || args[1].length != KEY_SIZE || !args[1].isHex()) return null
        return args[0] + args[1]
    }

    fun directCheck2(args: Array<String>): String? {
        if (args[0] == TAG && args[1].length == KEY_SIZE && args[1].isHex()) return args[0] + args[1]
        return null
    }

    fun linedCheck(args: Array<String>): String? {
        if (args[0] != TAG) return null
        if (args[1].length != KEY_SIZE) return null
        if (!args[1].isHex()) return null
        return args[0] + args[1]
    }

    fun ensureCheck(args: Array<String>): String? {
        ensure(args[0] == TAG) { return null }
        ensure(args[1].length == KEY_SIZE) { return null }
        ensure(args[1].isHex()) { return null }
        return args[0] + args[1]
    }

    fun checkCatch(args: Array<String>): String? =
        runCatching {
            check(args[0] == TAG)
            check(args[1].length == KEY_SIZE)
            check(args[1].isHex())
            args[0] + args[1]
        }.getOrNull()

    fun whenCheck(args: Array<String>) =
        when (false) {
            (args[0] == TAG),
            (args[1].length == KEY_SIZE),
            (args[1].isHex()),
            -> null
            else -> args[0] + args[1]
        }

    fun ensureAll(args: Array<String>): String? {
        ensureAll {
            ensure { args[0] == TAG }
            ensure { args[1].length == KEY_SIZE }
            ensure { args[1].isHex() }
        }.ifFail { return null }
        return args[0] + args[1]
    }

    @Test
    fun directCheckElse() = scope.measureRepeated { assertTrue(directCheckElse(testArgs) != null) }

    @Test
    fun directCheck() = scope.measureRepeated { assertTrue(directCheck(testArgs) != null) }

    @Test
    fun linedCheck() = scope.measureRepeated { assertTrue(linedCheck(testArgs) != null) }

    @Test
    fun ensureCheck() = scope.measureRepeated { assertTrue(ensureCheck(testArgs) != null) }

    @Test
    fun checkCatch() = scope.measureRepeated { assertTrue(checkCatch(testArgs) != null) }

    @Test
    fun whenCheck() = scope.measureRepeated { assertTrue(whenCheck(testArgs) != null) }

    @Test
    fun ensureAll() = scope.measureRepeated { assertTrue(ensureAll(testArgs) != null) }
}

class EnsureScope(
    var passing: Boolean = true,
) {
    inline fun ensure(predicate: () -> Boolean) {
        if (passing) passing = predicate()
    }

    inline fun ifFail(ifFail: () -> Unit) {
        if (!passing) ifFail()
    }
}

inline fun ensureAll(block: EnsureScope.() -> Unit) = EnsureScope().apply(block)
