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
package com.vitorpamplona.amethyst.commons.mavenReleases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MavenVersionTest {
    private fun assertOrdered(vararg versions: String) {
        versions.toList().zipWithNext().forEach { (lower, higher) ->
            assertTrue(MavenVersion.isNewer(higher, lower), "$higher should be newer than $lower")
            assertFalse(MavenVersion.isNewer(lower, higher), "$lower should not be newer than $higher")
        }
    }

    @Test
    fun numbersCompareAsNumbers() = assertOrdered("1.2.9", "1.2.10", "1.10.0", "2.0.0", "10.0")

    @Test
    fun qualifiersComeBeforeTheRelease() = assertOrdered("2.1.0-alpha01", "2.1.0-alpha02", "2.1.0-beta1", "2.1.0-rc1", "2.1.0-SNAPSHOT", "2.1.0", "2.1.0-sp1", "2.1.1")

    @Test
    fun trailingZerosAndReleaseMarkersAreEqual() {
        assertEquals(0, MavenVersion.compare("1.0", "1.0.0"))
        assertEquals(0, MavenVersion.compare("1.0.0", "1.0.0-final"))
        assertEquals(0, MavenVersion.compare("1.0-RC1", "1.0-rc-1"))
    }

    @Test
    fun aNumberBeatsAQualifier() = assertOrdered("1.0-rc1", "1.0.1")
}
