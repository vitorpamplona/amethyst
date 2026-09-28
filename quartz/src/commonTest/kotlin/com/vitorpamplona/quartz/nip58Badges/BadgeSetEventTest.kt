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
package com.vitorpamplona.quartz.nip58Badges

import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BadgeSetEventTest {
    private val issuer = "a".repeat(64)

    @Test
    fun readsAGeneralNip51BadgeSet() {
        val set =
            AcceptedBadgeSetEvent(
                "0".repeat(64),
                "1".repeat(64),
                1L,
                arrayOf(
                    arrayOf("d", "conferences"),
                    arrayOf("title", "Conferences"),
                    arrayOf("description", "Badges from events I attended"),
                    arrayOf("a", "30009:$issuer:nostrasia"),
                    arrayOf("e", "b".repeat(64)),
                ),
                "",
                "",
            )

        assertFalse(set.isLegacyProfileBadges())
        assertEquals("Conferences", set.title())
        assertEquals("Badges from events I attended", set.description())
        assertEquals(1, set.acceptedBadges().size)
    }

    @Test
    fun recognizesTheLegacyProfileBadges() {
        val legacy = AcceptedBadgeSetEvent("0".repeat(64), "1".repeat(64), 1L, arrayOf(arrayOf("d", "profile_badges")), "", "")
        assertTrue(legacy.isLegacyProfileBadges())
    }
}
