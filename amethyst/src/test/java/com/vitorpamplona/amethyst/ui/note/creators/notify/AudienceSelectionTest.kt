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
package com.vitorpamplona.amethyst.ui.note.creators.notify

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserContext
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide who a bulk add would actually put in a note's audience.
 *
 * These matter more than they look: a private note is gift-wrapped once per
 * recipient, and the recipient list is readable by every recipient — so
 * "selected by default" is a privacy and a cost decision, not a convenience.
 */
class AudienceSelectionTest {
    // User pins a few addressable note shells on construction; empty shells are
    // enough here since none of these rules read them.
    private val noContext = UserContext { addr -> AddressableNote(addr) }

    private fun user(hex: String) = User(hex, noContext)

    private val alice = user("aa".repeat(32))
    private val bruno = user("bb".repeat(32))
    private val carla = user("cc".repeat(32))
    private val dev = user("dd".repeat(32))

    private fun listOfPeople(
        public: List<User> = emptyList(),
        private: List<User> = emptyList(),
    ) = AudienceList(
        id = "close-friends",
        kind = AudienceListKind.PEOPLE_LIST,
        title = "Close friends",
        publicMembers = persistentListOf(*public.toTypedArray()),
        privateMembers = persistentListOf(*private.toTypedArray()),
    )

    private fun members(
        list: AudienceList,
        alreadyIn: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        flagInbox: Boolean = false,
    ) = AudienceSelection.buildMembers(list, alreadyIn, hidden, flagInbox)

    @Test
    fun ordinaryMembersStartSelected() {
        val rows = members(listOfPeople(public = listOf(alice, bruno)))

        assertEquals(setOf(alice.pubkeyHex, bruno.pubkeyHex), AudienceSelection.defaultSelection(rows))
    }

    @Test
    fun privateMembersStartDeselected() {
        val rows = members(listOfPeople(public = listOf(alice), private = listOf(carla)))

        // Adding a private member publishes their pubkey to every other
        // recipient, so it has to be a deliberate tap.
        assertEquals(setOf(alice.pubkeyHex), AudienceSelection.defaultSelection(rows))
        assertTrue(rows.first { it.pubkeyHex == carla.pubkeyHex }.isPrivateMember)
    }

    @Test
    fun mutedPeopleStartDeselected() {
        val rows = members(listOfPeople(public = listOf(alice, bruno)), hidden = setOf(bruno.pubkeyHex))

        assertEquals(setOf(alice.pubkeyHex), AudienceSelection.defaultSelection(rows))
    }

    @Test
    fun alreadyAddedPeopleAreSelectedButNeverReAdded() {
        val rows = members(listOfPeople(public = listOf(alice, bruno)), alreadyIn = setOf(alice.pubkeyHex))
        val selection = AudienceSelection.defaultSelection(rows)

        // Alice counts in the header so the number tells the truth...
        assertTrue(alice.pubkeyHex in selection)
        // ...but confirming only adds Bruno.
        assertEquals(listOf(bruno.pubkeyHex), AudienceSelection.pendingAdditions(rows, selection).map { it.pubkeyHex })
        // and select-all must not be able to turn her off.
        assertFalse(alice.pubkeyHex in AudienceSelection.toggleableIds(rows))
    }

    @Test
    fun peopleInBothMemberSetsAppearOnce() {
        val rows = members(listOfPeople(public = listOf(alice, bruno), private = listOf(alice)))

        assertEquals(2, rows.size)
    }

    @Test
    fun missingInboxRelayIsOnlyFlaggedForPrivateNotes() {
        val public = members(listOfPeople(public = listOf(alice)), flagInbox = false)
        assertFalse(public.single().isMissingInboxRelay)

        // A user with no loaded relay list cannot be shown to have an inbox.
        val private = members(listOfPeople(public = listOf(alice)), flagInbox = true)
        assertTrue(private.single().isMissingInboxRelay)
    }

    @Test
    fun capsDiscloseThenRefuse() {
        assertEquals(AudienceCap.Fine, AudienceSelection.capFor(0, AudienceSelection.SOFT_CAP))
        assertEquals(
            AudienceCap.OverSoft(AudienceSelection.SOFT_CAP + 1),
            AudienceSelection.capFor(1, AudienceSelection.SOFT_CAP),
        )
        assertEquals(
            AudienceCap.OverHard(AudienceSelection.HARD_CAP + 1),
            AudienceSelection.capFor(1, AudienceSelection.HARD_CAP),
        )
    }

    @Test
    fun capCountsTheAudienceAlreadyInTheComposer() {
        // 20 already p-tagged plus 10 more is over the soft cap even though
        // neither number is on its own.
        assertEquals(AudienceCap.OverSoft(30), AudienceSelection.capFor(20, 10))
    }

    @Test
    fun removingAListOnlyOrphansPeopleThatListAloneBroughtIn() {
        val provenance =
            mapOf(
                alice.pubkeyHex to setOf("close-friends"),
                bruno.pubkeyHex to setOf("close-friends", "work"),
                carla.pubkeyHex to setOf("work"),
            )

        val removal = AudienceSelection.removeListFromProvenance(provenance, "close-friends")

        // Alice came only from the removed list, so she goes.
        assertEquals(setOf(alice.pubkeyHex), removal.orphaned)
        // Bruno is also in Work, so he stays — with Work as his remaining source.
        assertEquals(setOf("work"), removal.provenance[bruno.pubkeyHex])
        assertEquals(setOf("work"), removal.provenance[carla.pubkeyHex])
        assertFalse(alice.pubkeyHex in removal.provenance)
    }

    @Test
    fun removingAListThatWasNeverAddedChangesNothing() {
        val provenance = mapOf(alice.pubkeyHex to setOf("work"))

        val removal = AudienceSelection.removeListFromProvenance(provenance, "close-friends")

        assertTrue(removal.orphaned.isEmpty())
        assertEquals(provenance, removal.provenance)
    }

    @Test
    fun groupChipsCountOnlyPeopleStillInTheAudience() {
        val provenance =
            mapOf(
                alice.pubkeyHex to setOf("close-friends"),
                bruno.pubkeyHex to setOf("close-friends"),
                dev.pubkeyHex to setOf("close-friends"),
            )
        val lists = listOf(listOfPeople(public = listOf(alice, bruno, dev)))

        // Dev was removed from the audience by hand; the chip must say 2, not 3.
        val chips =
            AudienceSelection.activeGroupChips(
                provenance = provenance,
                audience = setOf(alice.pubkeyHex, bruno.pubkeyHex),
                lists = lists,
            )

        assertEquals(1, chips.size)
        assertEquals("Close friends", chips.single().title)
        assertEquals(2, chips.single().count)
    }

    @Test
    fun groupChipsSkipListsThatNoLongerExist() {
        val chips =
            AudienceSelection.activeGroupChips(
                provenance = mapOf(alice.pubkeyHex to setOf("deleted-list")),
                audience = setOf(alice.pubkeyHex),
                lists = emptyList(),
            )

        assertTrue(chips.isEmpty())
    }

    @Test
    fun listTitleSearchIsCaseInsensitiveAndBlankMatchesEverything() {
        val list = listOfPeople(public = listOf(alice))

        assertTrue(list.matches(""))
        assertTrue(list.matches("  "))
        assertTrue(list.matches("CLOSE"))
        assertTrue(list.matches(" friends "))
        assertFalse(list.matches("work"))
    }
}
