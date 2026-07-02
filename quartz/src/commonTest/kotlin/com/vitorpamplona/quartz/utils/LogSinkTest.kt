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
package com.vitorpamplona.quartz.utils

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogSinkTest {
    private data class Line(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    private val originalSink = Log.sink
    private val originalMinLevel = Log.minLevel

    @AfterTest
    fun restore() {
        Log.sink = originalSink
        Log.minLevel = originalMinLevel
    }

    @Test
    fun defaultSinkIsThePlatformLogger() {
        assertSame(PlatformLogSink, Log.sink)
    }

    @Test
    fun consumerSinkReceivesEveryLevelWithTagMessageAndThrowable() {
        val captured = mutableListOf<Line>()
        Log.minLevel = LogLevel.DEBUG
        Log.sink = LogSink { level, tag, message, throwable -> captured.add(Line(level, tag, message, throwable)) }

        val boom = RuntimeException("boom")
        Log.d("A", "d")
        Log.i("B", "i")
        Log.w("C", "w")
        Log.e("D", "e", boom)

        assertEquals(
            listOf(
                Line(LogLevel.DEBUG, "A", "d", null),
                Line(LogLevel.INFO, "B", "i", null),
                Line(LogLevel.WARN, "C", "w", null),
                Line(LogLevel.ERROR, "D", "e", boom),
            ),
            captured,
        )
    }

    @Test
    fun minLevelGatesBeforeReachingTheSink() {
        val captured = mutableListOf<Line>()
        Log.minLevel = LogLevel.WARN
        Log.sink = LogSink { level, tag, message, throwable -> captured.add(Line(level, tag, message, throwable)) }

        Log.d("A", "dropped")
        Log.i("B", "dropped")
        Log.w("C", "kept")
        Log.e("D", "kept")

        assertEquals(listOf(LogLevel.WARN, LogLevel.ERROR), captured.map { it.level })
    }

    @Test
    fun lazyMessageIsNotBuiltWhenGatedOut() {
        var built = false
        Log.minLevel = LogLevel.ERROR
        Log.sink = LogSink { _, _, _, _ -> }

        Log.d("A") {
            built = true
            "expensive"
        }

        assertTrue(!built, "lambda must not run below minLevel")
    }

    @Test
    fun lazyMessagePassesNullThrowable() {
        var captured: Line? = null
        Log.minLevel = LogLevel.DEBUG
        Log.sink = LogSink { level, tag, message, throwable -> captured = Line(level, tag, message, throwable) }

        Log.w("T") { "built" }

        assertEquals(LogLevel.WARN, captured?.level)
        assertEquals("built", captured?.message)
        assertNull(captured?.throwable)
    }
}
