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
package com.vitorpamplona.quartz.nip58Badges.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileBadgesLinkedAddressesTest {
    private val issuer = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val definition = "30009:$issuer:bravery"
    private val badgeSet = "30008:$issuer:profile_badges"

    @Test
    fun linksBothTheDisplayedDefinitionsAndTheBadgeSets() {
        val event =
            ProfileBadgesEvent(
                "0".repeat(64),
                issuer,
                1700000000L,
                arrayOf(
                    arrayOf("a", definition),
                    arrayOf("a", badgeSet),
                    // Neither a definition nor a set: not a relationship of this kind.
                    arrayOf("a", "1:$issuer:x"),
                ),
                "",
                "",
            )
        assertEquals(listOf(definition), event.badgeAwardDefinitions().map { it.toValue() })
        assertEquals(listOf(badgeSet), event.badgeSets().map { it.toValue() })
        assertEquals(listOf(definition, badgeSet), event.linkedAddressIds())
    }
}
