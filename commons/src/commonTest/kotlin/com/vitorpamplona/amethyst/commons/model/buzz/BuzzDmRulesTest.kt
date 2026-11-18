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
package com.vitorpamplona.amethyst.commons.model.buzz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Buzz DM rules as of buzz `97d5a46cc` (`handle_dm_open` / `handle_dm_add_member`). */
class BuzzDmRulesTest {
    private fun key(i: Int) = i.toString(16).padStart(64, '0')

    private val agents = (100..120).map(::key).toSet()
    private val isAgent: (String) -> Boolean = { it in agents }

    @Test
    fun aDmHoldsNinePeopleCountingMeAndAgentsDoNotCount() {
        val eightOthers = (1..8).map(::key)
        assertTrue(BuzzDmRules.canAdd(eightOthers.take(7), key(8), isAgent))
        // A ninth person besides me would make ten.
        assertFalse(BuzzDmRules.canAdd(eightOthers, key(9), isAgent))
        // An agent still fits, however many people there are.
        assertTrue(BuzzDmRules.canAdd(eightOthers, key(100), isAgent))
        // Agents already in don't use up people slots.
        assertTrue(BuzzDmRules.canAdd(eightOthers.take(7) + (100..105).map(::key), key(8), isAgent))
    }

    @Test
    fun openTemplateTakesAgentsPastEight() {
        val participants = (1..8).map(::key) + (100..103).map(::key)
        val template = BuzzDmRules.openTemplate(participants)
        assertEquals(participants, template.tags.filter { it[0] == "p" }.map { it[1] })
    }

    @Test
    fun addMemberCarriesHistoryOnlyWhenAsked() {
        val withHistory = BuzzDmRules.addMemberTemplate("c1", key(1), includeHistory = true)
        assertEquals(
            listOf(listOf("h", "c1"), listOf("p", key(1)), listOf("history", "include")),
            withHistory.tags.map { it.toList() },
        )
        val without = BuzzDmRules.addMemberTemplate("c1", key(1), includeHistory = false)
        assertTrue(without.tags.none { it[0] == "history" })
    }

    @Test
    fun readsTheChannelIdFromTheOk() {
        // command_executor.rs: `response:{"channel_id":…,"created":…}` from serde_json.
        val channel = "11111111-1111-4111-8111-111111111111"
        assertEquals(channel, BuzzDmRules.channelIdFromOk("response:{\"channel_id\":\"$channel\",\"created\":false}"))
        assertNull(BuzzDmRules.channelIdFromOk("duplicate: already processed"))
        assertNull(BuzzDmRules.channelIdFromOk(null))
    }
}
