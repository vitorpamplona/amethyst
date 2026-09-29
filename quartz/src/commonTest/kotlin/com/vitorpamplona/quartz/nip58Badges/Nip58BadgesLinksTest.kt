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

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip58BadgesLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val issuer = "b1".repeat(32)
    private val awardee = "b2".repeat(32)
    private val bravery = "30009:$issuer:bravery"
    private val honor = "30009:$issuer:honor"
    private val braveryAward = "e1".repeat(32)
    private val honorAward = "e2".repeat(32)

    @Test
    fun anAwardNamesItsDefinitionAndItsAwardees() {
        val event =
            BadgeAwardEvent(
                id,
                issuer,
                1L,
                arrayOf(
                    arrayOf("a", bravery),
                    arrayOf("p", awardee, "wss://relay.example/"),
                    arrayOf("p", me),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.BADGE_DEFINITION, LinkTarget.Address(bravery), "a"),
                Link(Relation.AWARDED, LinkTarget.User(awardee), "p"),
                Link(Relation.AWARDED, LinkTarget.User(me), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun aBadgeSetListsDefinitionAndAwardPairs() {
        val event =
            AcceptedBadgeSetEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("d", "my-favorites"),
                    arrayOf("a", bravery),
                    arrayOf("e", braveryAward, "wss://relay.example/"),
                    arrayOf("a", honor),
                    arrayOf("e", honorAward),
                    // an unpaired e is not a badge
                    arrayOf("e", "e3".repeat(32)),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.BADGE_DEFINITION, LinkTarget.Address(bravery), "a"),
                Link(Relation.BADGE_AWARD, LinkTarget.Event(braveryAward), "e"),
                Link(Relation.BADGE_DEFINITION, LinkTarget.Address(honor), "a"),
                Link(Relation.BADGE_AWARD, LinkTarget.Event(honorAward), "e"),
            ),
            event.links(),
        )
    }

    @Test
    fun aProfileShowsBadgesAndWholeBadgeSets() {
        val set = "30008:$me:my-favorites"
        val event =
            ProfileBadgesEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("a", bravery),
                    arrayOf("e", braveryAward),
                    arrayOf("a", set),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.BADGE_DEFINITION, LinkTarget.Address(bravery), "a"),
                Link(Relation.BADGE_AWARD, LinkTarget.Event(braveryAward), "e"),
                Link(Relation.BADGE_SET, LinkTarget.Address(set), "a"),
            ),
            event.links(),
        )
    }
}
