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
package com.vitorpamplona.quartz.utils.urldetector.detection

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharUtilsTest {
    @Test
    fun testCharUtilsIsHex() {
        val arr = charArrayOf('a', 'A', '0', '9')
        for (a in arr) {
            assertTrue(CharUtils.isHex(a))
        }

        val arr2 = charArrayOf('~', ';', 'Z', 'g')
        for (a in arr2) {
            assertFalse(CharUtils.isHex(a))
        }
    }

    @Test
    fun testCharUtilsIsNumeric() {
        val arr = charArrayOf('0', '4', '6', '9')
        for (a in arr) {
            assertTrue(CharUtils.isNumeric(a))
        }

        val arr2 = charArrayOf('a', '~', 'A', 0.toChar())
        for (a in arr2) {
            assertFalse(CharUtils.isNumeric(a))
        }
    }

    @Test
    fun testCharUtilsIsAlpha() {
        val arr = charArrayOf('a', 'Z', 'f', 'X')
        for (a in arr) {
            assertTrue(CharUtils.isAlpha(a))
        }

        val arr2 = charArrayOf('0', '9', '[', '~')
        for (a in arr2) {
            assertFalse(CharUtils.isAlpha(a))
        }
    }

    @Test
    fun testCharUtilsIsAlphaNumeric() {
        val arr = charArrayOf('a', 'G', '3', '9')
        for (a in arr) {
            assertTrue(CharUtils.isAlphaNumeric(a))
        }

        val arr2 = charArrayOf('~', '-', '_', '\n')
        for (a in arr2) {
            assertFalse(CharUtils.isAlphaNumeric(a))
        }
    }

    @Test
    fun testCharUtilsIsUnreserved() {
        val arr = charArrayOf('-', '.', 'a', '9', 'Z', '_', 'f')
        for (a in arr) {
            assertTrue(CharUtils.isUnreserved(a))
        }

        val arr2 = charArrayOf(' ', '!', '(', '\n')
        for (a in arr2) {
            assertFalse(CharUtils.isUnreserved(a))
        }
    }

    @Test
    fun testSplitByDot() {
        val stringsToSplit =
            listOf(
                "192.168.1.1",
                "..",
                "192%2e168%2e1%2e1",
                "asdf",
                "192.39%2e1%2E1",
                "as\uFF61awe.a3r23.lkajsf0ijr....",
                "%2e%2easdf",
                "sdoijf%2e",
                "ksjdfh.asdfkj.we%2",
                "0xc0%2e0x00%2e0x02%2e0xeb",
                "",
            )

        val regex = "[\\.\u3002\uFF0E\uFF61]|%2e|%2E".toRegex()

        stringsToSplit.forEach { stringToSplit ->
            assertContentEquals(
                stringToSplit.split(regex),
                CharUtils.splitByDot(stringToSplit),
                "Splitting $stringToSplit",
            )
        }
    }
}
