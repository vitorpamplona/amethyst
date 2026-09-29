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

import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileBadgeDefinitionsTest {
    @Test
    fun badgeSetPointersAreNotBadgeDefinitions() {
        val pk = "1".repeat(64)
        val definition = "30009:$pk:early-adopter"
        val profile =
            ProfileBadgesEvent(
                "0".repeat(64),
                pk,
                1,
                arrayOf(arrayOf("d", "profile_badges"), arrayOf("a", definition), arrayOf("e", "e".repeat(64)), arrayOf("a", "30008:$pk:favorites")),
                "",
                "0".repeat(128),
            )
        assertEquals(listOf(definition), profile.badgeAwardDefinitions().map { it.toValue() })
    }
}
