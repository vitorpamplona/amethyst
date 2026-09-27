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
package com.vitorpamplona.quartz.nip02FollowList.petnames

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetnamePathTest {
    private val me = "a".repeat(64)
    private val erin = "e".repeat(64)
    private val charlie = "c".repeat(64)
    private val carol = "b".repeat(64)
    private val dave = "d".repeat(64)

    private fun p(
        pubKey: HexKey,
        petname: String? = null,
    ) = if (petname == null) arrayOf("p", pubKey) else arrayOf("p", pubKey, "", petname)

    private val followLists: Map<HexKey, TagArray> =
        mapOf(
            me to arrayOf(p(erin, "erin"), p(dave), p(carol, "Carol Smith")),
            erin to arrayOf(p(charlie, "charlie"), p(me, "boss")),
            carol to arrayOf(p(erin, "erin_2")),
        )

    private suspend fun follows(pubKey: HexKey): TagArray? = followLists[pubKey]

    @Test
    fun parsesRelativePaths() {
        assertEquals(PetnamePath(PetnameRoot.CurrentUser, listOf("erin", "charlie")), PetnamePath.parse("~/erin/charlie"))
        assertEquals(PetnamePath(PetnameRoot.CurrentUser, listOf("erin")), PetnamePath.parse(" ~/erin "))
    }

    @Test
    fun parsesAbsoluteRoots() {
        val npub = NPub.create(carol)
        assertEquals(PetnamePath(PetnameRoot.PubKey(carol), listOf("erin_2", "charlie")), PetnamePath.parse("~$npub/erin_2/charlie"))
        assertEquals(PetnamePath(PetnameRoot.PubKey(carol), emptyList()), PetnamePath.parse("~$npub"))
        assertEquals(PetnamePath(PetnameRoot.Nip05("carol@names.com"), listOf("erin", "charlie")), PetnamePath.parse("~carol@names.com/erin/charlie"))
    }

    @Test
    fun rejectsMalformedPaths() {
        assertNull(PetnamePath.parse("erin/charlie"))
        assertNull(PetnamePath.parse("~"))
        assertNull(PetnamePath.parse("~/"))
        assertNull(PetnamePath.parse("~/erin//charlie"))
        assertNull(PetnamePath.parse("~/erin/"))
        assertNull(PetnamePath.parse("~/Carol Smith"))
        assertNull(PetnamePath.parse("~/josé"))
        assertNull(PetnamePath.parse("~npub1notakey/erin"))
        assertNull(PetnamePath.parse("~carol/erin"))
    }

    @Test
    fun onlyAsciiLettersDigitsAndUnderscoreAreEligible() {
        assertTrue(PetnamePath.isEligible("erin_2"))
        assertTrue(PetnamePath.isEligible("ERIN9"))
        assertFalse(PetnamePath.isEligible(""))
        assertFalse(PetnamePath.isEligible("erin-2"))
        assertFalse(PetnamePath.isEligible("erin.2"))
        assertFalse(PetnamePath.isEligible("Carol Smith"))
        assertFalse(PetnamePath.isEligible("émile"))
    }

    @Test
    fun encodesBackToText() {
        assertEquals("~/erin/charlie", PetnamePath(PetnameRoot.CurrentUser, listOf("erin", "charlie")).encode())
        assertEquals("~carol@names.com/erin", PetnamePath(PetnameRoot.Nip05("carol@names.com"), listOf("erin")).encode())
        assertEquals("~" + NPub.create(carol) + "/erin_2", PetnamePath(PetnameRoot.PubKey(carol), listOf("erin_2")).encode())
        assertEquals("~/erin/charlie", PetnamePath.display(listOf("erin", "charlie")))
    }

    @Test
    fun resolvesOneComponentAtATime() =
        runTest {
            assertEquals(erin, PetnameResolver.resolve("~/erin", me, ::follows))
            assertEquals(charlie, PetnameResolver.resolve("~/erin/charlie", me, ::follows))
            assertEquals(me, PetnameResolver.resolve("~/erin/boss", me, ::follows))
        }

    @Test
    fun resolvesAbsoluteRootsWithoutALoggedInUser() =
        runTest {
            val npub = NPub.create(carol)
            assertEquals(charlie, PetnameResolver.resolve("~$npub/erin_2/charlie", null, ::follows))
            assertEquals(carol, PetnameResolver.resolve("~$npub", null, ::follows))
            assertEquals(
                charlie,
                PetnameResolver.resolve("~carol@names.com/erin_2/charlie", null, ::follows) { if (it == "carol@names.com") carol else null },
            )
        }

    @Test
    fun failsWhenAnyStepIsMissing() =
        runTest {
            assertNull(PetnameResolver.resolve("~/erin", null, ::follows))
            assertNull(PetnameResolver.resolve("~/nobody", me, ::follows))
            assertNull(PetnameResolver.resolve("~/erin/charlie/x", me, ::follows)) // charlie's list is unknown
            assertNull(PetnameResolver.resolve("~/Erin", me, ::follows)) // exact match
            assertNull(PetnameResolver.resolve("~carol@names.com/erin_2", null, ::follows)) // no NIP-05 resolver
            assertNull(PetnameResolver.resolve("not a path", me, ::follows))
        }

    @Test
    fun petnameOfReturnsAnyPetnameForDisplay() {
        val mine = followLists.getValue(me)
        assertEquals("erin", PetnameResolver.petnameOf(mine, erin))
        assertEquals("Carol Smith", PetnameResolver.petnameOf(mine, carol))
        assertNull(PetnameResolver.petnameOf(mine, dave))
        assertNull(PetnameResolver.petnameOf(mine, charlie))
        // Ineligible names still display but never resolve a path.
        assertNull(PetnameResolver.findByPetname(mine, "Carol Smith"))

        assertEquals(mapOf(erin to "erin", carol to "Carol Smith"), PetnameResolver.petnames(mine))
    }
}
